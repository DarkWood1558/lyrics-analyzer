package com.lyricsanalyzer.dto;

import com.lyricsanalyzer.model.LyricsStatus;
import com.lyricsanalyzer.model.SentimentLabel;
import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.model.Track;

/**
 * Antwort-DTO für Track-Abfragen. Bewusst getrennt von der Entity,
 * damit interne Persistenz-Details nicht 1:1 nach außen durchgereicht werden.
 */
public record TrackResponse(
        Long id,
        String artistName,
        String title,
        String albumName,
        String genre,
        Integer releaseYear,
        LyricsStatus lyricsStatus,
        boolean hasLyrics,
        SentimentLabel sentimentLabel,
        Double sentimentScore,
        Theme theme
) {
    public static TrackResponse from(Track track) {
        return new TrackResponse(
                track.getId(),
                track.getArtistName(),
                track.getTitle(),
                track.getAlbumName(),
                track.getGenre(),
                track.getReleaseYear(),
                track.getLyricsStatus(),
                track.getLyrics() != null,
                track.getSentimentLabel(),
                track.getSentimentScore(),
                track.getTheme()
        );
    }
}