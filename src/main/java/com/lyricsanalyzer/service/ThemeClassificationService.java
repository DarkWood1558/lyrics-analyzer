package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.ArtistStyleFeatures;
import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.*;

import java.util.*;

/**
 * Service für die Themenklassifikation von Songtexten mit Weka.
 */
@Service
public class ThemeClassificationService {

    private final FeatureExtractionService featureExtractionService;
    private final TrackRepository trackRepository;

    private Classifier themeClassifier;
    private Instances trainingData;

    public ThemeClassificationService(FeatureExtractionService featureExtractionService,
                                     TrackRepository trackRepository) {
        this.featureExtractionService = featureExtractionService;
        this.trackRepository = trackRepository;
    }

    /**
     * Trainiert den Themen-Klassifikator mit gelabelten Tracks.
     */
    public void trainThemeClassifier(List<Track> labeledTracks) {
        try {
            if (labeledTracks == null || labeledTracks.isEmpty()) {
                throw new IllegalArgumentException("No labeled tracks provided for training");
            }

            // Attribute definieren
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("avgWordLength"));
            attributes.add(new Attribute("rhymeDensity"));
            attributes.add(new Attribute("uniqueWordRatio"));
            attributes.add(new Attribute("sentimentScore"));

            // Top 20 Wörter als Attribute
            for (int i = 0; i < 20; i++) {
                attributes.add(new Attribute("wordFreq" + i));
            }

            // Zielvariable (Theme)
            ArrayList<String> themeValues = new ArrayList<>();
            Arrays.stream(Theme.values()).forEach(t -> themeValues.add(t.name()));
            attributes.add(new Attribute("theme", themeValues));

            trainingData = new Instances("ThemeTraining", attributes, labeledTracks.size());
            trainingData.setClassIndex(trainingData.numAttributes() - 1);

            // Daten für Training vorbereiten
            for (Track track : labeledTracks) {
                if (track.getTheme() == null || track.getLyrics() == null || track.getLyrics().isBlank()) {
                    continue;
                }

                Instance instance = new DenseInstance(attributes.size());
                ArtistStyleFeatures features = featureExtractionService.extractStyleFeatures(track.getLyrics());

                instance.setValue(0, features.avgWordLength());
                instance.setValue(1, features.rhymeDensity());
                instance.setValue(2, features.uniqueWordRatio());
                instance.setValue(3, track.getSentimentScore() != null ? track.getSentimentScore() : 0.0);

                // Top 20 Wörter
                List<Map.Entry<String, Double>> sortedWords = features.topWords().entrySet().stream()
                        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                        .limit(20)
                        .toList();

                for (int i = 0; i < 20; i++) {
                    double freq = i < sortedWords.size() ? sortedWords.get(i).getValue() : 0.0;
                    instance.setValue(4 + i, freq);
                }

                instance.setValue(attributes.size() - 1, track.getTheme().name());
                trainingData.add(instance);
            }

            if (trainingData.numInstances() == 0) {
                throw new IllegalStateException("No valid training instances created");
            }

            // Klassifikator trainieren
            themeClassifier = new NaiveBayes();
            themeClassifier.buildClassifier(trainingData);

        } catch (Exception e) {
            throw new RuntimeException("Failed to train theme classifier: " + e.getMessage(), e);
        }
    }

    /**
     * Klassifiziert das Thema eines Songtextes.
     */
    public Theme classifyTheme(String lyrics) {
        if (themeClassifier == null) {
            throw new IllegalStateException("Classifier not trained. Call trainThemeClassifier() first.");
        }

        if (lyrics == null || lyrics.isBlank()) {
            return null;
        }

        try {
            ArtistStyleFeatures features = featureExtractionService.extractStyleFeatures(lyrics);

            Instance instance = createInstanceFromLyrics(lyrics);
            instance.setDataset(trainingData);

            double prediction = themeClassifier.classifyInstance(instance);
            String themeName = trainingData.classAttribute().value((int) prediction);

            return Theme.valueOf(themeName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to classify theme: " + e.getMessage(), e);
        }
    }

    /**
     * Gibt die Themenverteilung (Wahrscheinlichkeiten) zurück.
     */
    public Map<Theme, Integer> getThemeDistribution(String lyrics) {
        if (themeClassifier == null || trainingData == null) {
            return Map.of();
        }

        if (lyrics == null || lyrics.isBlank()) {
            return Map.of();
        }

        try {
            Instance instance = createInstanceFromLyrics(lyrics);
            instance.setDataset(trainingData);

            double[] probs = themeClassifier.distributionForInstance(instance);
            Map<Theme, Integer> distribution = new EnumMap<>(Theme.class);

            for (int i = 0; i < probs.length; i++) {
                Theme theme = Theme.valueOf(trainingData.classAttribute().value(i));
                distribution.put(theme, (int) (probs[i] * 100));
            }

            return distribution;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Erstellt eine Instanz aus Songtexten für die Klassifikation.
     */
    private Instance createInstanceFromLyrics(String lyrics) {
        ArtistStyleFeatures features = featureExtractionService.extractStyleFeatures(lyrics);
        Instance instance = new DenseInstance(25); // 4 + 20 words + 1 class

        instance.setValue(0, features.avgWordLength());
        instance.setValue(1, features.rhymeDensity());
        instance.setValue(2, features.uniqueWordRatio());
        instance.setValue(3, 0.0); // Placeholder für Sentiment

        List<Map.Entry<String, Double>> sortedWords = features.topWords().entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(20)
                .toList();

        for (int i = 0; i < 20; i++) {
            double freq = i < sortedWords.size() ? sortedWords.get(i).getValue() : 0.0;
            instance.setValue(4 + i, freq);
        }

        return instance;
    }

    /**
     * Prüft, ob der Klassifikator trainiert ist.
     */
    public boolean isTrained() {
        return themeClassifier != null && trainingData != null;
    }

    /**
     * Gibt die Anzahl der Trainingsdaten zurück.
     */
    public int getTrainingDataSize() {
        return trainingData != null ? trainingData.numInstances() : 0;
    }
}
