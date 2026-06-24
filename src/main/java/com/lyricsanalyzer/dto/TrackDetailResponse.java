package com.lyricsanalyzer.dto;

import com.lyricsanalyzer.model.LyricsStatus;
import com.lyricsanalyzer.model.SentimentLabel;
import com.lyricsanalyzer.model.Track;

/**
 * Detailansicht eines einzelnen Tracks inklusive vollständigem Lyrics-Text.
 * Wird bewusst nicht in Listen-Endpunkten verwendet (Performance + Datenschutz/Urheberrecht:
 * Lyrics sollten nicht in Massen-Listings exponiert werden).
 */
public record TrackDetailResponse(
        Long id,
        String artistName,
        String title,
        String albumName,
        String genre,
        Integer releaseYear,
        LyricsStatus lyricsStatus,
        String lyrics,
        SentimentLabel sentimentLabel,
        Double sentimentScore
) {
    public static TrackDetailResponse from(Track track) {
        return new TrackDetailResponse(
                track.getId(),
                track.getArtistName(),
                track.getTitle(),
                track.getAlbumName(),
                track.getGenre(),
                track.getReleaseYear(),
                track.getLyricsStatus(),
                track.getLyrics(),
                track.getSentimentLabel(),
                track.getSentimentScore()
        );
    }
}
