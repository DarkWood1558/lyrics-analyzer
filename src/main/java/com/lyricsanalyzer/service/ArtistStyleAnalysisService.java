package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.ArtistStyleFeatures;
import com.lyricsanalyzer.model.LyricsDNA;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für die Analyse von Künstler-Stilen.
 */
@Service
public class ArtistStyleAnalysisService {

    private final FeatureExtractionService featureExtractionService;
    private final TrackRepository trackRepository;

    public ArtistStyleAnalysisService(FeatureExtractionService featureExtractionService,
                                     TrackRepository trackRepository) {
        this.featureExtractionService = featureExtractionService;
        this.trackRepository = trackRepository;
    }

    /**
     * Analysiert den Stil eines einzelnen Songtextes.
     */
    public ArtistStyleFeatures analyzeStyle(String lyrics) {
        if (lyrics == null || lyrics.isBlank()) {
            return new ArtistStyleFeatures(0, 0, 0, null, Map.of());
        }
        return featureExtractionService.extractStyleFeatures(lyrics);
    }

    /**
     * Analysiert den Stil für einen bestimmten Künstler (Aggregation aller seiner Tracks).
     */
    public ArtistStyleFeatures analyzeStyleForArtist(String artistName) {
        List<Track> tracks = trackRepository.findByArtistNameIgnoreCase(artistName);
        
        List<String> lyricsList = tracks.stream()
                .map(Track::getLyrics)
                .filter(Objects::nonNull)
                .filter(l -> !l.isBlank())
                .toList();

        return featureExtractionService.extractStyleFeaturesForArtist(lyricsList);
    }

    /**
     * Generiert die Lyrics-DNA für einen Künstler.
     */
    public LyricsDNA generateLyricsDNA(String artistName) {
        List<Track> tracks = trackRepository.findByArtistNameIgnoreCase(artistName);

        if (tracks.isEmpty()) {
            return null;
        }

        // Aggregierte Features berechnen
        double totalAvgWordLength = 0;
        double totalRhymeDensity = 0;
        double totalUniqueWordRatio = 0;
        double totalSentiment = 0;
        Map<String, Double> aggregatedWordFrequencies = new HashMap<>();

        int count = 0;

        for (Track track : tracks) {
            if (track.getLyrics() == null || track.getLyrics().isBlank()) {
                continue;
            }

            ArtistStyleFeatures features = analyzeStyle(track.getLyrics());

            totalAvgWordLength += features.avgWordLength();
            totalRhymeDensity += features.rhymeDensity();
            totalUniqueWordRatio += features.uniqueWordRatio();
            totalSentiment += track.getSentimentScore() != null ? track.getSentimentScore() : 0.0;
            count++;

            // Wortfrequenzen aggregieren
            features.topWords().forEach((word, freq) ->
                aggregatedWordFrequencies.merge(word, freq, Double::sum)
            );
        }

        if (count == 0) {
            return null;
        }

        // Durchschnitt berechnen
        double[] featureVector = {
                totalAvgWordLength / count,
                totalRhymeDensity / count,
                totalUniqueWordRatio / count,
                totalSentiment / count
        };

        // Normalisierte Wortfrequenzen
        final int countFinal = count;
        Map<String, Double> avgWordFrequencies = aggregatedWordFrequencies.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() / countFinal,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        return new LyricsDNA(artistName, featureVector, Map.of(), totalSentiment / count);
    }

    /**
     * Generiert DNA für alle Künstler.
     */
    public List<LyricsDNA> generateDNAForAllArtists() {
        List<Track> allTracks = trackRepository.findAll();

        Map<String, List<Track>> tracksByArtist = allTracks.stream()
                .filter(t -> t.getArtistName() != null && !t.getArtistName().isBlank())
                .collect(Collectors.groupingBy(t -> t.getArtistName().toLowerCase()));

        return tracksByArtist.entrySet().stream()
                .map(entry -> {
                    String artistName = entry.getKey();
                    // Capitalize first letter of each word
                    artistName = Arrays.stream(artistName.split(" "))
                            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                            .collect(Collectors.joining(" "));
                    return generateLyricsDNA(artistName);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Berechnet die Ähnlichkeit zwischen zwei Lyrics-DNAs (Cosine Similarity).
     */
    public double calculateSimilarity(LyricsDNA dna1, LyricsDNA dna2) {
        if (dna1 == null || dna2 == null) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;

        double[] vec1 = dna1.featureVector();
        double[] vec2 = dna2.featureVector();

        int minLength = Math.min(vec1.length, vec2.length);

        for (int i = 0; i < minLength; i++) {
            dotProduct += vec1[i] * vec2[i];
            magnitude1 += Math.pow(vec1[i], 2);
            magnitude2 += Math.pow(vec2[i], 2);
        }

        if (magnitude1 == 0 || magnitude2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(magnitude1) * Math.sqrt(magnitude2));
    }

    /**
     * Findet ähnliche Künstler zu einem gegebenen Künstler.
     */
    public Map<String, Double> findSimilarArtists(String artistName, int limit) {
        List<LyricsDNA> allDNA = generateDNAForAllArtists();
        LyricsDNA targetDNA = allDNA.stream()
                .filter(d -> d.artistName().equalsIgnoreCase(artistName))
                .findFirst()
                .orElse(null);

        if (targetDNA == null) {
            return Map.of();
        }

        return allDNA.stream()
                .filter(d -> !d.artistName().equalsIgnoreCase(artistName))
                .collect(Collectors.toMap(
                        LyricsDNA::artistName,
                        dna -> calculateSimilarity(targetDNA, dna)
                ))
                .entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
