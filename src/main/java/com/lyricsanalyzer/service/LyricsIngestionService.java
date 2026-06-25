package com.lyricsanalyzer.service;

import com.lyricsanalyzer.client.DeezerAlbumMetadata;
import com.lyricsanalyzer.client.DeezerClient;
import com.lyricsanalyzer.client.LyricsFetchResult;
import com.lyricsanalyzer.client.LyricsOvhClient;
import com.lyricsanalyzer.config.LyricsAnalyzerProperties;
import com.lyricsanalyzer.dto.DeezerSearchResponse;
import com.lyricsanalyzer.model.LyricsStatus;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestriert die komplette Ingestion-Pipeline:
 * 1. Songs über Deezer suchen
 * 2. Pro Song prüfen, ob er (inkl. Lyrics) schon in der DB liegt
 * 3. Falls nicht: Lyrics von lyrics.ovh abrufen und DAUERHAFT speichern
 * 4. Songs, die bereits einen Lyrics-Status haben, werden NICHT erneut abgefragt
 * 5. Für neu angelegte Tracks: Genre + Erscheinungsjahr über die Deezer Album-API
 *    nachladen, da die einfache Such-API diese Felder nicht mitliefert (siehe README)
 */
@Service
public class LyricsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LyricsIngestionService.class);

    private final DeezerClient deezerClient;
    private final LyricsOvhClient lyricsOvhClient;
    private final TrackRepository trackRepository;
    private final LyricsAnalyzerProperties properties;

    public LyricsIngestionService(DeezerClient deezerClient,
                                  LyricsOvhClient lyricsOvhClient,
                                  TrackRepository trackRepository,
                                  LyricsAnalyzerProperties properties) {
        this.deezerClient = deezerClient;
        this.lyricsOvhClient = lyricsOvhClient;
        this.trackRepository = trackRepository;
        this.properties = properties;
    }

    public record IngestionSummary(int searched, int alreadyCached, int newlyFetched,
                                   int notFound, int errors) {
    }

    /**
     * Sucht Songs über Deezer und lädt für jeden neuen Song die Lyrics nach.
     *
     * @param searchQuery Deezer-Suchbegriff, z.B. "artist:\"Coldplay\"" oder ein Genre/Künstlername
     * @param limit       maximale Anzahl an Songs aus der Deezer-Suche
     */
    public IngestionSummary ingestBySearchQuery(String searchQuery, int limit) {
        List<DeezerSearchResponse.DeezerTrack> deezerTracks =
                deezerClient.searchTracks(searchQuery, limit);

        int alreadyCached = 0;
        int newlyFetched = 0;
        int notFound = 0;
        int errors = 0;

        for (DeezerSearchResponse.DeezerTrack deezerTrack : deezerTracks) {
            String artist = deezerTrack.getArtist() != null ? deezerTrack.getArtist().getName() : null;
            String title = deezerTrack.getTitle();

            if (artist == null || title == null) {
                continue;
            }

            FindOrCreateResult findOrCreateResult = findOrCreateTrack(deezerTrack, artist, title);
            Track track = findOrCreateResult.track();

            // Neu angelegte Tracks direkt um Genre/Jahr anreichern (zusätzlicher Call zur
            // Deezer Album-API, da die einfache Suche diese Felder nicht liefert).
            if (findOrCreateResult.isNewlyCreated()) {
                enrichWithAlbumMetadata(track);
            }

            // Zentrale Caching-Logik: nur abrufen, wenn noch nicht versucht (PENDING)
            if (track.getLyricsStatus() != LyricsStatus.PENDING) {
                alreadyCached++;
                continue;
            }

            LyricsFetchResult result = lyricsOvhClient.fetchLyrics(artist, title);
            applyFetchResult(track, result);
            trackRepository.save(track);

            switch (result.status()) {
                case FOUND -> newlyFetched++;
                case NOT_FOUND -> notFound++;
                case ERROR -> errors++;
                default -> { /* PENDING kommt hier nicht vor */ }
            }

            sleepBetweenRequests();
        }

        IngestionSummary summary = new IngestionSummary(
                deezerTracks.size(), alreadyCached, newlyFetched, notFound, errors);
        log.info("Ingestion abgeschlossen für Query '{}': {}", searchQuery, summary);
        return summary;
    }

    private record FindOrCreateResult(Track track, boolean isNewlyCreated) {
    }

    private FindOrCreateResult findOrCreateTrack(DeezerSearchResponse.DeezerTrack deezerTrack,
                                    String artist, String title) {
        var existing = trackRepository.findByArtistNameIgnoreCaseAndTitleIgnoreCase(artist, title);
        if (existing.isPresent()) {
            return new FindOrCreateResult(existing.get(), false);
        }

        Track newTrack = new Track(artist, title);
        newTrack.setDeezerId(deezerTrack.getId());
        if (deezerTrack.getAlbum() != null) {
            newTrack.setAlbumName(deezerTrack.getAlbum().getTitle());
            newTrack.setDeezerAlbumId(deezerTrack.getAlbum().getId());
        }
        Track saved = trackRepository.save(newTrack);
        return new FindOrCreateResult(saved, true);
    }

    /**
     * Lädt Genre + Erscheinungsjahr für einen Track über die Deezer Album-API nach und
     * speichert sie direkt am Track. Schlägt der Abruf fehl, bleibt der Track einfach ohne
     * diese Metadaten (siehe DeezerClient.fetchAlbumMetadata) - die Lyrics-Ingestion läuft
     * unabhängig davon weiter.
     */
    private void enrichWithAlbumMetadata(Track track) {
        if (track.getDeezerAlbumId() == null) {
            return;
        }
        DeezerAlbumMetadata metadata = deezerClient.fetchAlbumMetadata(track.getDeezerAlbumId());
        if (metadata.genre() != null) {
            track.setGenre(metadata.genre());
        }
        if (metadata.releaseYear() != null) {
            track.setReleaseYear(metadata.releaseYear());
        }
        trackRepository.save(track);
    }

    private void applyFetchResult(Track track, LyricsFetchResult result) {
        track.setLyricsStatus(result.status());
        track.setLyricsFetchedAt(LocalDateTime.now());
        if (result.status() == LyricsStatus.FOUND) {
            track.setLyrics(result.lyrics());
        }
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(properties.getIngestion().getRequestDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Liefert alle Tracks, die Lyrics haben, aber noch nicht sentiment-analysiert wurden.
     */
    public List<Track> findTracksPendingSentimentAnalysis(int limit) {
        return new ArrayList<>(trackRepository.findByLyricsStatusAndSentimentLabelIsNull(
                LyricsStatus.FOUND.name(),
                PageRequest.of(0, limit)));
    }

    public record RetrySummary(int attempted, int newlyFetched, int notFound, int stillError) {
    }

    /**
     * Versucht für Tracks mit Status ERROR den Lyrics-Abruf erneut.
     * ERROR bedeutet meist ein vorübergehendes technisches Problem (Netzwerk, Timeout,
     * unerwarteter HTTP-Status) - ein erneuter Versuch kann hier tatsächlich helfen,
     * anders als bei NOT_FOUND (dort ist das Ergebnis von lyrics.ovh inhaltlich klar:
     * der Song wurde in keiner der Quellen gefunden, ein Retry würde nur unnötig Last
     * erzeugen ohne neue Information zu bringen).
     */
    public RetrySummary retryErrorTracks(int limit) {
        List<Track> errorTracks = trackRepository.findByLyricsStatus(
                LyricsStatus.ERROR.name(), PageRequest.of(0, limit));

        int newlyFetched = 0;
        int notFound = 0;
        int stillError = 0;

        for (Track track : errorTracks) {
            LyricsFetchResult result = lyricsOvhClient.fetchLyrics(
                    track.getArtistName(), track.getTitle());
            applyFetchResult(track, result);
            trackRepository.save(track);

            switch (result.status()) {
                case FOUND -> newlyFetched++;
                case NOT_FOUND -> notFound++;
                case ERROR -> stillError++;
                default -> { /* PENDING kommt hier nicht vor */ }
            }

            sleepBetweenRequests();
        }

        RetrySummary summary = new RetrySummary(errorTracks.size(), newlyFetched, notFound, stillError);
        log.info("Retry für ERROR-Tracks abgeschlossen: {}", summary);
        return summary;
    }

    public record MetadataBackfillSummary(int attempted, int updated, int skipped) {
    }

    /**
     * Lädt für bereits vorhandene Tracks (Altdaten, die vor Einführung der Metadaten-
     * Anreicherung angelegt wurden) Genre + Erscheinungsjahr nachträglich über die Deezer
     * Album-API nach. Betrifft nur Tracks mit bekannter deezer_album_id, bei denen Genre
     * oder Jahr noch fehlen (siehe TrackRepository.findPendingMetadataBackfill).
     *
     * @param limit maximale Anzahl an Tracks pro Lauf (um die Deezer-API nicht zu überlasten)
     */
    public MetadataBackfillSummary backfillMetadata(int limit) {
        List<Track> pending = trackRepository.findPendingMetadataBackfill(PageRequest.of(0, limit));

        int updated = 0;
        int skipped = 0;

        for (Track track : pending) {
            DeezerAlbumMetadata metadata = deezerClient.fetchAlbumMetadata(track.getDeezerAlbumId());

            boolean changed = false;
            if (metadata.genre() != null && track.getGenre() == null) {
                track.setGenre(metadata.genre());
                changed = true;
            }
            if (metadata.releaseYear() != null && track.getReleaseYear() == null) {
                track.setReleaseYear(metadata.releaseYear());
                changed = true;
            }

            if (changed) {
                trackRepository.save(track);
                updated++;
            } else {
                skipped++;
            }

            sleepBetweenRequests();
        }

        MetadataBackfillSummary summary = new MetadataBackfillSummary(pending.size(), updated, skipped);
        log.info("Metadaten-Backfill abgeschlossen: {}", summary);
        return summary;
    }
}
