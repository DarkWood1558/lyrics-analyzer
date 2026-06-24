package com.lyricsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Repräsentiert die Antwort von https://api.deezer.com/album/{id}
 * Liefert (anders als die einfache Such-API) zusätzlich Genre- und Erscheinungsjahr-Daten:
 * - genre_id: numerische Deezer-Genre-ID (Klartext-Name kommt erst über /genre/{id}, siehe DeezerGenreResponse)
 * - release_date: Format "YYYY-MM-DD" (manchmal nur das digitale Veröffentlichungsdatum,
 *   nicht das ursprüngliche Erscheinungsdatum - siehe README)
 * Nur die für uns relevanten Felder werden gemappt, der Rest wird ignoriert.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeezerAlbumResponse {

    private Long id;

    @JsonProperty("genre_id")
    private Long genreId;

    @JsonProperty("release_date")
    private String releaseDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGenreId() {
        return genreId;
    }

    public void setGenreId(Long genreId) {
        this.genreId = genreId;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }
}
