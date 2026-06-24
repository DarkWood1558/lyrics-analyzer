package com.lyricsanalyzer.model;

import java.util.Map;

/**
 * Repräsentiert die "DNA" eines Künstlers basierend auf seinen Lyrics.
 */
public record LyricsDNA(
    String artistName,
    double[] featureVector,
    Map<Theme, Integer> themeDistribution,
    double averageSentiment
) {
    public LyricsDNA {
        if (artistName == null || artistName.isBlank()) {
            throw new IllegalArgumentException("Artist name cannot be null or empty");
        }
        if (featureVector == null || featureVector.length == 0) {
            featureVector = new double[4];
        }
        if (themeDistribution == null) {
            themeDistribution = Map.of();
        }
    }
}
