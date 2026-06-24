package com.lyricsanalyzer.client;

import com.lyricsanalyzer.dto.DeezerAlbumResponse;
import com.lyricsanalyzer.dto.DeezerGenreResponse;
import com.lyricsanalyzer.dto.DeezerSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client für die Deezer Such-API (kein Account/Key nötig).
 * Wird genutzt, um Songlisten (Künstler + Titel + Album) zu finden,
 * die anschließend für die Lyrics-Analyse herangezogen werden.
 * <p>
 * Liefert außerdem Zusatzmetadaten (Genre, Erscheinungsjahr) über die Deezer Album-API,
 * da die einfache Such-API ({@code /search}) diese Felder nicht mitliefert (siehe README).
 */
@Component
public class DeezerClient {

    private static final Logger log = LoggerFactory.getLogger(DeezerClient.class);

    private final WebClient deezerWebClient;

    /**
     * Cache für genre_id -> Klartext-Genre-Name. Deezer hat nur eine kleine, feste Anzahl
     * an Top-Level-Genres - ein einfacher prozessweiter Cache spart hier sehr viele
     * redundante HTTP-Calls, ohne dass eine aufwändige Cache-Invalidierung nötig wäre.
     */
    private final Map<Long, String> genreNameCache = new ConcurrentHashMap<>();

    public DeezerClient(WebClient deezerWebClient) {
        this.deezerWebClient = deezerWebClient;
    }

    /**
     * Sucht Tracks über die Deezer Such-API.
     *
     * @param query z.B. "artist:\"Coldplay\"" oder ein freier Suchbegriff
     * @param limit maximale Anzahl an Ergebnissen
     */
    public List<DeezerSearchResponse.DeezerTrack> searchTracks(String query, int limit) {
        try {
            DeezerSearchResponse response = deezerWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(DeezerSearchResponse.class)
                    .block();

            if (response == null || response.getData() == null) {
                return Collections.emptyList();
            }
            return response.getData();

        } catch (Exception e) {
            log.error("Fehler bei der Deezer-Suche für Query '{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Lädt Genre und Erscheinungsjahr für ein Album nach (über {@code GET /album/{albumId}}).
     * Die einfache Such-API liefert diese Felder nicht, daher ist dieser Zusatz-Call nötig,
     * um die Statistik-Auswertungen ("Sentiment pro Genre/Jahr") aussagekräftig zu machen.
     * <p>
     * Schlägt der Aufruf fehl (Netzwerk, 404, o.ä.), wird {@link DeezerAlbumMetadata#EMPTY}
     * zurückgegeben - ein fehlender Metadaten-Lookup soll die Ingestion-Pipeline nicht abbrechen.
     *
     * @param albumId die Deezer-Album-ID, wie sie im Such-Ergebnis unter "album.id" steht
     */
    public DeezerAlbumMetadata fetchAlbumMetadata(Long albumId) {
        if (albumId == null) {
            return DeezerAlbumMetadata.EMPTY;
        }

        try {
            DeezerAlbumResponse album = deezerWebClient.get()
                    .uri("/album/{albumId}", albumId)
                    .retrieve()
                    .bodyToMono(DeezerAlbumResponse.class)
                    .block();

            if (album == null) {
                return DeezerAlbumMetadata.EMPTY;
            }

            String genreName = resolveGenreName(album.getGenreId());
            Integer releaseYear = parseReleaseYear(album.getReleaseDate());

            return new DeezerAlbumMetadata(genreName, releaseYear);

        } catch (WebClientResponseException e) {
            log.warn("Album-Metadaten für albumId={} nicht abrufbar (HTTP {}): {}",
                    albumId, e.getStatusCode(), e.getMessage());
            return DeezerAlbumMetadata.EMPTY;
        } catch (Exception e) {
            log.error("Fehler beim Abruf der Album-Metadaten für albumId={}: {}", albumId, e.getMessage());
            return DeezerAlbumMetadata.EMPTY;
        }
    }

    /**
     * Löst eine genre_id über {@code GET /genre/{genreId}} zu einem lesbaren Namen auf
     * (z.B. 116 -> "Rap/Hip Hop"). Ergebnisse werden gecacht, siehe {@link #genreNameCache}.
     * Liefert {@code null}, wenn keine genre_id vorhanden ist oder der Lookup fehlschlägt -
     * das Genre bleibt dann am Track schlicht unbefüllt, statt die gesamte Anreicherung
     * abzubrechen.
     */
    private String resolveGenreName(Long genreId) {
        if (genreId == null) {
            return null;
        }

        String cached = genreNameCache.get(genreId);
        if (cached != null) {
            return cached;
        }

        try {
            DeezerGenreResponse genre = deezerWebClient.get()
                    .uri("/genre/{genreId}", genreId)
                    .retrieve()
                    .bodyToMono(DeezerGenreResponse.class)
                    .block();

            if (genre == null || genre.getName() == null || genre.getName().isBlank()) {
                return null;
            }

            genreNameCache.put(genreId, genre.getName());
            return genre.getName();

        } catch (Exception e) {
            log.warn("Genre-Name für genreId={} nicht auflösbar: {}", genreId, e.getMessage());
            return null;
        }
    }

    /**
     * Deezer liefert release_date als "YYYY-MM-DD" (siehe DeezerAlbumResponse) - wir
     * interessieren uns für die Statistik nur für das Jahr. Ein leeres/unparsbares Datum
     * führt nicht zum Fehler, sondern liefert einfach kein Jahr.
     */
    private Integer parseReleaseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(releaseDate).getYear();
        } catch (DateTimeParseException e) {
            log.debug("Konnte release_date '{}' nicht parsen", releaseDate);
            return null;
        }
    }
}
