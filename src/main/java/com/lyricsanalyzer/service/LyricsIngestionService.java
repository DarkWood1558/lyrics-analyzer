package com.lyricsanalyzer.service;

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

            Track track = findOrCreateTrack(deezerTrack, artist, title);

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

    private Track findOrCreateTrack(DeezerSearchResponse.DeezerTrack deezerTrack,
                                    String artist, String title) {
        return trackRepository.findByArtistNameIgnoreCaseAndTitleIgnoreCase(artist, title)
                .orElseGet(() -> {
                    Track newTrack = new Track(artist, title);
                    newTrack.setDeezerId(deezerTrack.getId());
                    if (deezerTrack.getAlbum() != null) {
                        newTrack.setAlbumName(deezerTrack.getAlbum().getTitle());
                    }
                    return trackRepository.save(newTrack);
                });
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
                LyricsStatus.FOUND,
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
                LyricsStatus.ERROR, PageRequest.of(0, limit));

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
}