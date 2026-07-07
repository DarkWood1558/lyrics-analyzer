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
        "his", "her", "its", "our", "their",
        "der", "die", "das", "und", "den", "von", "zu", "mit", "sich", 
        "für", "ist", "des", "im", "dem", "nicht", "ein", "eine", "als",
        "auch", "es", "werden", "aus", "er", "hat", "dass", "sie", "nach",
        "wird", "bei", "einer", "um", "haben", "nur", "oder", "aber", "vor",
        "bis", "mehr", "durch", "man", "sein", "wurde", "so", "wenn", "einen", "wieder",
        "uns", "ihm", "dann", "unter", "waren", "ihnen", "ihrem", "ihres",
        "dieser", "ihr", "ihre", "unsere", "unser"
    );

    public Map<String, Double> extractWordFrequencies(String text) {
        if (text == null || text.isBlank()) return Map.of();
        
        // Unterstützung für deutsche Umlaute und ß - behält a-z, A-Z, ä, ö, ü, Ä, Ö, Ü, ß bei
        String[] words = text.toLowerCase().replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ").split("\\s+");
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

    public Map<String, Double> extractBigramFrequencies(String text) {
        if (text == null || text.isBlank()) return Map.of();
        
        // Unterstützung für deutsche Umlaute und ß - behält a-z, A-Z, ä, ö, ü, Ä, Ö, Ü, ß bei
        String[] words = text.toLowerCase().replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ").split("\\s+");
        Map<String, Integer> bigramCounts = new HashMap<>();
        int totalBigrams = 0;
        
        for (int i = 0; i < words.length - 1; i++) {
            String word1 = words[i].trim();
            String word2 = words[i + 1].trim();
            if (word1.length() > 2 && word2.length() > 2 && 
                !STOP_WORDS.contains(word1) && !STOP_WORDS.contains(word2)) {
                String bigram = word1 + "_" + word2;
                bigramCounts.merge(bigram, 1, Integer::sum);
                totalBigrams++;
            }
        }
        
        if (totalBigrams == 0) return Map.of();
        
        final int totalBigramsFinal = totalBigrams;
        return bigramCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, 
                    e -> (double) e.getValue() / totalBigramsFinal, (a, b) -> b, LinkedHashMap::new));
    }

    public double calculateAvgWordLength(String text) {
        if (text == null || text.isBlank()) return 0.0;
        // Behält deutsche Umlaute und ß bei
        String[] words = text.replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ").split("\\s+");
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
        // Unterstützung für deutsche Umlaute und ß
        String[] words = text.toLowerCase().replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ").split("\\s+");
        if (words.length == 0) return 0.0;
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        return (double) uniqueWords.size() / words.length;
    }

    public double calculateAvgLineLength(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String[] lines = text.split("\\n");
        if (lines.length == 0) return 0.0;
        return Arrays.stream(lines).mapToInt(String::length).average().orElse(0.0);
    }

    public double calculateExclamationDensity(String text) {
        if (text == null || text.isBlank()) return 0.0;
        long exclamationCount = text.chars().filter(ch -> ch == '!').count();
        // Unterstützung für deutsche Umlaute und ß bei der Wortzählung
        long wordCount = Arrays.stream(text.replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ").split("\\s+")).count();
        return wordCount > 0 ? (double) exclamationCount / wordCount * 100 : 0.0;
    }

    public double calculateQuestionMarkDensity(String text) {
        if (text == null || text.isBlank()) return 0.0;
        long questionCount = text.chars().filter(ch -> ch == '?').count();
        // Unterstützung für deutsche Umlaute und ß bei der Wortzählung
        long wordCount = Arrays.stream(text.replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ").split("\\s+")).count();
        return wordCount > 0 ? (double) questionCount / wordCount * 100 : 0.0;
    }

    public double calculateCapitalWordRatio(String text) {
        if (text == null || text.isBlank()) return 0.0;
        // Unterstützung für deutsche Umlaute und ß
        String[] words = text.replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ").split("\\s+");
        if (words.length == 0) return 0.0;
        long capitalWords = Arrays.stream(words)
                .filter(word -> !word.isEmpty() && Character.isUpperCase(word.charAt(0)))
                .count();
        return (double) capitalWords / words.length;
    }

    public int calculateLineCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\n").length;
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
                extractWordFrequencies(lyrics),
                calculateAvgLineLength(lyrics),
                calculateExclamationDensity(lyrics),
                calculateQuestionMarkDensity(lyrics),
                calculateCapitalWordRatio(lyrics),
                calculateLineCount(lyrics)
        );
    }

    public ArtistStyleFeatures extractStyleFeaturesForArtist(List<String> lyricsList) {
        if (lyricsList == null || lyricsList.isEmpty()) {
            return new ArtistStyleFeatures(0, 0, 0, null, Map.of(), 0, 0, 0, 0, 0);
        }
        
        double totalAvgWordLength = 0;
        double totalRhymeDensity = 0;
        double totalUniqueWordRatio = 0;
        double totalAvgLineLength = 0;
        double totalExclamationDensity = 0;
        double totalQuestionMarkDensity = 0;
        double totalCapitalWordRatio = 0;
        int totalLineCount = 0;
        Map<String, Double> aggregatedWordFrequencies = new HashMap<>();
        int count = 0;
        
        for (String lyrics : lyricsList) {
            if (lyrics == null || lyrics.isBlank()) continue;
            ArtistStyleFeatures features = extractStyleFeatures(lyrics);
            totalAvgWordLength += features.avgWordLength();
            totalRhymeDensity += features.rhymeDensity();
            totalUniqueWordRatio += features.uniqueWordRatio();
            totalAvgLineLength += features.avgLineLength();
            totalExclamationDensity += features.exclamationDensity();
            totalQuestionMarkDensity += features.questionMarkDensity();
            totalCapitalWordRatio += features.capitalWordRatio();
            totalLineCount += features.lineCount();
            count++;
            features.topWords().forEach((word, freq) -> aggregatedWordFrequencies.merge(word, freq, Double::sum));
        }
        
        if (count == 0) return new ArtistStyleFeatures(0, 0, 0, null, Map.of(), 0, 0, 0, 0, 0);
        
        final int countFinal = count;
        Map<String, Double> avgWordFrequencies = aggregatedWordFrequencies.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / countFinal, (a, b) -> b, LinkedHashMap::new));
        
        return new ArtistStyleFeatures(
                totalAvgWordLength / count,
                totalRhymeDensity / count,
                totalUniqueWordRatio / count,
                null,
                avgWordFrequencies,
                totalAvgLineLength / count,
                totalExclamationDensity / count,
                totalQuestionMarkDensity / count,
                totalCapitalWordRatio / count,
                totalLineCount / count
        );
    }
}
