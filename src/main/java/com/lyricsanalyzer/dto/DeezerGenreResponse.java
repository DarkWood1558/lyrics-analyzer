package com.lyricsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Repräsentiert die Antwort von https://api.deezer.com/genre/{id}
 * Liefert den lesbaren Namen zu einer genre_id (z.B. 116 -> "Rap/Hip Hop").
 * Deezer hat nur eine kleine, feste Anzahl an Top-Level-Genres, daher lohnt sich Caching
 * (siehe DeezerClient.fetchGenreName).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeezerGenreResponse {

    private Long id;
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
