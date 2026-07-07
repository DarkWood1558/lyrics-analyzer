package com.lyricsanalyzer.dto;

import com.lyricsanalyzer.model.LyricsStatus;
import com.lyricsanalyzer.model.SentimentLabel;
import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.service.SentimentAnalysisService;

/**
 * Detailansicht eines einzelnen Tracks inklusive vollständigem Lyrics-Text.
 * Wird bewusst nicht in Listen-Endpunkten verwendet (Performance + Datenschutz/Urheberrecht:
 * Lyrics sollten nicht in Massen-Listings exponiert werden).
 *
 * Enthält zusätzlich {@code theme}, da die GUI (Track-Detail-Modal) dieses Feld anzeigt
 * (siehe app.js: showTrackDetails -> track.theme). Vorher fehlte das Feld hier, obwohl
 * Track.getTheme() existierte - das Modal zeigte daher nie ein Thema an.
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
        Double sentimentScore,
        Double sentimentScoreNormalized,
        Theme theme
) {
    public static TrackDetailResponse from(Track track) {
        Double rawScore = track.getSentimentScore();
        Double normalizedScore = (rawScore != null) 
                ? SentimentAnalysisService.normalizeScore(rawScore) 
                : null;
        
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
                track.getSentimentScore(),
                normalizedScore,
                track.getTheme()
        );
    }
}