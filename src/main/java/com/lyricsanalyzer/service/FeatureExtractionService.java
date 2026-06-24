package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.ArtistStyleFeatures;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service zur Extraktion von stilistischen Features aus Songtexten.
 */
@Service
public class FeatureExtractionService {

    private static final Set<String> STOP_WORDS = Set.of(
        "the", "and", "a", "an", "in", "on", "at", "to", "of", "for", "with", 
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", 
        "do", "does", "did", "will", "would", "should", "could", "may", "might", 
        "must", "can", "i", "you", "he", "she", "it", "we", "they", "my", "your", 
        "his", "her", "its", "our", "their"
    );

    public Map<String, Double> extractWordFrequencies(String text) {
        if (text == null || text.isBlank()) return Map.of();
        
        String[] words = text.toLowerCase().replaceAll("[^a-zA-Z\\s]", " ").split("\\s+");
        Map<String, Integer> wordCounts = new HashMap<>();
        int totalWords = 0;
        
        for (String word : words) {
            word = word.trim();
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                wordCounts.merge(word, 1, Integer::sum);
                totalWords++;
            }
        }
        
        if (totalWords == 0) return Map.of();
        
        final int totalWordsFinal = totalWords;
        return wordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (double) e.getValue() / totalWordsFinal, (a, b) -> b, LinkedHashMap::new));
    }

    public double calculateAvgWordLength(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String[] words = text.split("\\s+");
        if (words.length == 0) return 0.0;
        return Arrays.stream(words).mapToInt(String::length).average().orElse(0.0);
    }

    public double calculateRhymeDensity(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String[] lines = text.split("\\n");
        if (lines.length < 2) return 0.0;
        
        int rhymingPairs = 0;
        for (int i = 0; i < lines.length - 1; i++) {
            String lastWord1 = extractLastWord(lines[i]);
            String lastWord2 = extractLastWord(lines[i + 1]);
            if (doWordsRhyme(lastWord1, lastWord2)) rhymingPairs++;
        }
        return (double) rhymingPairs / (lines.length - 1);
    }

    public double calculateUniqueWordRatio(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String[] words = text.toLowerCase().replaceAll("[^a-zA-Z\\s]", " ").split("\\s+");
        if (words.length == 0) return 0.0;
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        return (double) uniqueWords.size() / words.length;
    }

    private String extractLastWord(String line) {
        if (line == null || line.isBlank()) return "";
        String[] words = line.trim().split("\\s+");
        return words.length > 0 ? words[words.length - 1].toLowerCase() : "";
    }

    private boolean doWordsRhyme(String word1, String word2) {
        if (word1.isEmpty() || word2.isEmpty()) return false;
        return getPhoneticEnding(word1).equals(getPhoneticEnding(word2));
    }

    private String getPhoneticEnding(String word) {
        if (word.length() <= 2) return word;
        String normalized = word.replaceAll("[aeiouy]", "a");
        return normalized.substring(normalized.length() - 3);
    }

    public ArtistStyleFeatures extractStyleFeatures(String lyrics) {
        if (lyrics == null) lyrics = "";
        return new ArtistStyleFeatures(
                calculateAvgWordLength(lyrics),
                calculateRhymeDensity(lyrics),
                calculateUniqueWordRatio(lyrics),
                null,
                extractWordFrequencies(lyrics)
        );
    }

    public ArtistStyleFeatures extractStyleFeaturesForArtist(List<String> lyricsList) {
        if (lyricsList == null || lyricsList.isEmpty()) {
            return new ArtistStyleFeatures(0, 0, 0, null, Map.of());
        }
        
        double totalAvgWordLength = 0;
        double totalRhymeDensity = 0;
        double totalUniqueWordRatio = 0;
        Map<String, Double> aggregatedWordFrequencies = new HashMap<>();
        int count = 0;
        
        for (String lyrics : lyricsList) {
            if (lyrics == null || lyrics.isBlank()) continue;
            ArtistStyleFeatures features = extractStyleFeatures(lyrics);
            totalAvgWordLength += features.avgWordLength();
            totalRhymeDensity += features.rhymeDensity();
            totalUniqueWordRatio += features.uniqueWordRatio();
            count++;
            features.topWords().forEach((word, freq) -> aggregatedWordFrequencies.merge(word, freq, Double::sum));
        }
        
        if (count == 0) return new ArtistStyleFeatures(0, 0, 0, null, Map.of());
        
        final int countFinal = count;
        Map<String, Double> avgWordFrequencies = aggregatedWordFrequencies.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / countFinal, (a, b) -> b, LinkedHashMap::new));
        
        return new ArtistStyleFeatures(
                totalAvgWordLength / count,
                totalRhymeDensity / count,
                totalUniqueWordRatio / count,
                null,
                avgWordFrequencies
        );
    }
}
