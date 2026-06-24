package com.lyricsanalyzer.client;

import com.lyricsanalyzer.dto.DeezerSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

/**
 * Client für die Deezer Such-API (kein Account/Key nötig).
 * Wird genutzt, um Songlisten (Künstler + Titel + Album) zu finden,
 * die anschließend für die Lyrics-Analyse herangezogen werden.
 */
@Component
public class DeezerClient {

    private static final Logger log = LoggerFactory.getLogger(DeezerClient.class);

    private final WebClient deezerWebClient;

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
}
