package com.lyricsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Repräsentiert die Antwort von https://api.deezer.com/search?q=...
 * Nur die für uns relevanten Felder werden gemappt, der Rest wird ignoriert.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeezerSearchResponse {

    private List<DeezerTrack> data;

    public List<DeezerTrack> getData() {
        return data;
    }

    public void setData(List<DeezerTrack> data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeezerTrack {

        private Long id;
        private String title;

        @JsonProperty("artist")
        private DeezerArtist artist;

        @JsonProperty("album")
        private DeezerAlbum album;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public DeezerArtist getArtist() {
            return artist;
        }

        public void setArtist(DeezerArtist artist) {
            this.artist = artist;
        }

        public DeezerAlbum getAlbum() {
            return album;
        }

        public void setAlbum(DeezerAlbum album) {
            this.album = album;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeezerArtist {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeezerAlbum {
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
