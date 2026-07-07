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
    Map<String, Double> topWords,
    double avgLineLength,
    double exclamationDensity,
    double questionMarkDensity,
    double capitalWordRatio,
    int lineCount
) {
    public ArtistStyleFeatures {
        avgWordLength = Math.max(0, avgWordLength);
        rhymeDensity = Math.max(0, Math.min(1, rhymeDensity));
        uniqueWordRatio = Math.max(0, Math.min(1, uniqueWordRatio));
        avgLineLength = Math.max(0, avgLineLength);
        exclamationDensity = Math.max(0, exclamationDensity);
        questionMarkDensity = Math.max(0, questionMarkDensity);
        capitalWordRatio = Math.max(0, Math.min(1, capitalWordRatio));
        lineCount = Math.max(0, lineCount);
    }
}
