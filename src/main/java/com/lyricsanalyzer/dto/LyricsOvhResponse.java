package com.lyricsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Repräsentiert die Antwort von https://api.lyrics.ovh/v1/{artist}/{title}
 * Erfolgsfall: { "lyrics": "..." }
 * Fehlerfall: HTTP 404, kein Lyrics-Feld vorhanden -> wird vom Client als leer behandelt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LyricsOvhResponse {

    private String lyrics;

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }
}
