package com.lyricsanalyzer.model;

import java.util.Map;

/**
 * Repräsentiert die "DNA" eines Künstlers basierend auf seinen Lyrics.
 * 
 * Feature-Vektor Struktur (15 Elemente):
 * - Index 0-2: Basis-Text-Features (avgWordLength, rhymeDensity, uniqueWordRatio)
 * - Index 3-7: Erweiterte Stil-Features (avgLineLength, exclamationDensity, questionMarkDensity, capitalWordRatio, lineCount)
 * - Index 8: trackCount (Anzahl der Tracks des Künstlers)
 * - Index 9-13: Top-5 Wörter (Häufigkeiten pro Track)
 * - Index 14: avgSentiment
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
            featureVector = new double[15]; // Jetzt 15 Features statt 4
        }
        if (themeDistribution == null) {
            themeDistribution = Map.of();
        }
    }
}
