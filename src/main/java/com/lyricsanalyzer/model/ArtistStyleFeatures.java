package com.lyricsanalyzer.model;

import java.util.Map;

/**
 * Features zur Analyse des Schreibstils eines Künstlers/Songs.
 */
public record ArtistStyleFeatures(
    double avgWordLength,
    double rhymeDensity,
    double uniqueWordRatio,
    Double sentimentScore,
    Map<String, Double> topWords
) {
    public ArtistStyleFeatures {
        avgWordLength = Math.max(0, avgWordLength);
        rhymeDensity = Math.max(0, Math.min(1, rhymeDensity));
        uniqueWordRatio = Math.max(0, Math.min(1, uniqueWordRatio));
    }
}
