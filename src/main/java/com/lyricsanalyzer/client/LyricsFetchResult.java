package com.lyricsanalyzer.client;

import com.lyricsanalyzer.model.LyricsStatus;

/**
 * Ergebnis eines Lyrics-Abrufversuchs.
 * Unterscheidet bewusst zwischen "nicht gefunden" (404, legitimes Ergebnis)
 * und "Fehler" (z.B. Netzwerkproblem, sollte später erneut versucht werden).
 */
public record LyricsFetchResult(LyricsStatus status, String lyrics) {

    public static LyricsFetchResult found(String lyrics) {
        return new LyricsFetchResult(LyricsStatus.FOUND, lyrics);
    }

    public static LyricsFetchResult notFound() {
        return new LyricsFetchResult(LyricsStatus.NOT_FOUND, null);
    }

    public static LyricsFetchResult error() {
        return new LyricsFetchResult(LyricsStatus.ERROR, null);
    }
}
