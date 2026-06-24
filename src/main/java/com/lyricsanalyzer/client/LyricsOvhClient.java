package com.lyricsanalyzer.client;

import com.lyricsanalyzer.dto.LyricsOvhResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Client für die lyrics.ovh API (kein Account/Key nötig).
 * Lyrics werden bei lyrics.ovh nicht persistiert - die API ist nur ein Proxy
 * auf Quellen wie Genius/AZLyrics/etc. Deshalb speichern wir das Ergebnis
 * dauerhaft selbst in unserer Datenbank, um nicht jedes Mal neu abfragen zu müssen.
 */
@Component
public class LyricsOvhClient {

    private static final Logger log = LoggerFactory.getLogger(LyricsOvhClient.class);

    private final WebClient lyricsOvhWebClient;

    public LyricsOvhClient(WebClient lyricsOvhWebClient) {
        this.lyricsOvhWebClient = lyricsOvhWebClient;
    }

    public LyricsFetchResult fetchLyrics(String artist, String title) {
        try {
            // WebClient kodiert die {artist}/{title}-Platzhalter selbst beim Einsetzen -
            // hier KEIN manuelles URLEncoder.encode() vorschalten, sonst doppelte Kodierung
            // (z.B. Leerzeichen würde zu "%2520" statt "%20").
            LyricsOvhResponse response = lyricsOvhWebClient.get()
                    .uri("/v1/{artist}/{title}", artist, title)
                    .retrieve()
                    .bodyToMono(LyricsOvhResponse.class)
                    .block();

            if (response == null || response.getLyrics() == null || response.getLyrics().isBlank()) {
                return LyricsFetchResult.notFound();
            }
            return LyricsFetchResult.found(response.getLyrics());

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(404)) {
                return LyricsFetchResult.notFound();
            }
            log.warn("Unerwarteter HTTP-Status {} bei Lyrics-Abruf für '{} - {}'",
                    e.getStatusCode(), artist, title);
            return LyricsFetchResult.error();

        } catch (Exception e) {
            log.error("Fehler beim Lyrics-Abruf für '{} - {}': {}", artist, title, e.getMessage());
            return LyricsFetchResult.error();
        }
    }
}
