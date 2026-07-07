package com.lyricsanalyzer.model;

import java.util.Map;

/**
 * Repräsentiert die "DNA" eines Künstlers basierend auf seinen Lyrics.
 * 
 * Feature-Vektor Struktur (20 Elemente - ALLE normalisiert auf 0.0-1.0 Bereich):
 * - Index 0-2: Basis-Text-Features (avgWordLength/10, rhymeDensity, uniqueWordRatio)
 * - Index 3-7: Erweiterte Stil-Features (avgLineLength/40, exclamationDensity/5, questionMarkDensity/5, capitalWordRatio, lineCount/60)
 * - Index 8: trackCount/50 (Anzahl der Tracks des Künstlers MIT Lyrics, normalisiert)
 * - Index 9-13: Top-5 Wörter (Häufigkeiten pro Track, bereits im Bereich 0-0.1)
 * - Index 14-18: Genre One-Hot-Encoding (5 binäre Features: Rock, Pop, Schlager, Rap, Sonstige)
 * - Index 19: (avgSentiment+2)/4 (normalisiert von -2..2 auf 0.0..1.0)
 * 
 * Hinweis: Das averageSentiment Feld in diesem Record enthält den ORIGINALEN Wert (-2..2),
 * während featureVector[19] den normalisierten Wert enthält.
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
            featureVector = new double[20]; // Jetzt 20 Features
        }
        if (themeDistribution == null) {
            themeDistribution = Map.of();
        }
    }
}
