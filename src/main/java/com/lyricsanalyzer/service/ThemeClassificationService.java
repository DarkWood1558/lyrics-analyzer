package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.ArtistStyleFeatures;
import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.model.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Service für die Themenklassifikation von Songtexten mit Weka.
 *
 * <h2>Thread-Sicherheit</h2>
 * Dieser Service ist ein Spring-Singleton-Bean - ohne besondere Vorkehrung könnten
 * gleichzeitige Requests (ein Training läuft, während gleichzeitig klassifiziert wird)
 * zu Race Conditions führen, z.B. classifyInstance() mit einem trainingData-Schema,
 * das nicht mehr zum aktuell gesetzten themeClassifier passt.
 * <p>
 * Lösung: Klassifikator + Trainingsdaten-Schema werden als EIN unveränderliches
 * {@link TrainedModel}-Objekt behandelt und über eine einzige {@code volatile}-Referenz
 * atomar ausgetauscht. Lesende Zugriffe (classifyTheme, getThemeDistribution) lesen
 * die Referenz exakt einmal und arbeiten dann nur noch mit diesem konsistenten Snapshot -
 * unabhängig davon, ob parallel ein neues Training läuft. Da Lesezugriffe ungleich
 * häufiger sind als Trainings, ist ein Lock hierfür nicht nötig.
 *
 * <h2>Persistenz</h2>
 * Ohne Persistenz ginge ein trainiertes Modell bei jedem Neustart der Anwendung
 * verloren, obwohl die Theme-Labels in der DB dauerhaft sind und das Training u.U.
 * mehrere Minuten dauert. Das Modell wird daher nach jedem Training als Datei
 * gespeichert (Weka-Classifier sind {@link java.io.Serializable}) und beim Start der
 * Anwendung automatisch wieder geladen, falls vorhanden.
 */
@Service
public class ThemeClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ThemeClassificationService.class);

    /** Anzahl der Vokabular-Wörter, die als Features genutzt werden (siehe buildVocabulary). */
    private static final int VOCABULARY_SIZE = 100;

    private final FeatureExtractionService featureExtractionService;
    private final String modelFilePath;

    /**
     * EIN unveränderliches Objekt für Klassifikator + dazugehöriges Trainingsdaten-Schema
     * (inkl. Vokabular) + Klassen-Attribut. Wird bei jedem Training komplett neu erzeugt
     * und durch eine einzige atomare Schreiboperation ersetzt - alte Leser, die noch eine
     * Referenz auf das vorherige Modell halten, arbeiten unbeeinflusst damit weiter zu Ende.
     */
    private volatile TrainedModel currentModel;

    private record TrainedModel(Classifier classifier, Instances schema, List<String> vocabulary) {
    }

    public ThemeClassificationService(FeatureExtractionService featureExtractionService,
                                      @Value("${lyrics-analyzer.theme-model.path:./data/theme-classifier.model}")
                                      String modelFilePath) {
        this.featureExtractionService = featureExtractionService;
        this.modelFilePath = modelFilePath;
        loadPersistedModelIfPresent();
    }

    /**
     * Baut ein festes, gemeinsames Vokabular über ALLE Trainingsdaten (statt - wie zuvor -
     * pro Song individuell die Top-20-Wörter zu nehmen). Das ist der entscheidende
     * Unterschied: vorher hatte Attribut "wordFreq3" je nach Song eine andere
     * Wort-Bedeutung, wodurch der Klassifikator im Wesentlichen nur aus Zahlenmustern statt
     * aus tatsächlichen Wortinhalten lernen konnte. Mit einem festen Vokabular bedeutet
     * Attribut "word_love" für jeden Song dieselbe Sache: "wie häufig kommt 'love' vor".
     */
    private List<String> buildVocabulary(List<Track> labeledTracks) {
        Map<String, Double> globalFrequencies = new HashMap<>();

        for (Track track : labeledTracks) {
            if (track.getLyrics() == null || track.getLyrics().isBlank()) {
                continue;
            }
            Map<String, Double> wordFreq = featureExtractionService.extractWordFrequencies(track.getLyrics());
            wordFreq.forEach((word, freq) -> globalFrequencies.merge(word, freq, Double::sum));
        }

        return globalFrequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(VOCABULARY_SIZE)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<Attribute> buildAttributes(List<String> vocabulary) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("avgWordLength"));
        attributes.add(new Attribute("rhymeDensity"));
        attributes.add(new Attribute("uniqueWordRatio"));
        attributes.add(new Attribute("sentimentScore"));

        for (String word : vocabulary) {
            attributes.add(new Attribute("word_" + word));
        }

        ArrayList<String> themeValues = new ArrayList<>();
        Arrays.stream(Theme.values()).forEach(t -> themeValues.add(t.name()));
        attributes.add(new Attribute("theme", themeValues));

        return attributes;
    }

    /**
     * Trainiert den Themen-Klassifikator mit gelabelten Tracks.
     * Erzeugt intern ein komplett neues {@link TrainedModel} und ersetzt
     * {@link #currentModel} erst ganz am Ende durch eine einzige atomare Zuweisung -
     * bis dahin sehen parallele Leser noch das alte (konsistente) Modell.
     */
    public synchronized void trainThemeClassifier(List<Track> labeledTracks) {
        try {
            if (labeledTracks == null || labeledTracks.isEmpty()) {
                throw new IllegalArgumentException("No labeled tracks provided for training");
            }

            List<String> vocabulary = buildVocabulary(labeledTracks);
            List<Attribute> attributes = buildAttributes(vocabulary);

            Instances trainingData = new Instances("ThemeTraining", new ArrayList<>(attributes), labeledTracks.size());
            trainingData.setClassIndex(trainingData.numAttributes() - 1);

            for (Track track : labeledTracks) {
                if (track.getTheme() == null || track.getLyrics() == null || track.getLyrics().isBlank()) {
                    continue;
                }

                Instance instance = buildInstance(track.getLyrics(), vocabulary, attributes.size(),
                        track.getSentimentScore() != null ? track.getSentimentScore() : 0.0);
                trainingData.add(instance);
                instance.setValue(attributes.size() - 1, track.getTheme().name());
            }

            if (trainingData.numInstances() == 0) {
                throw new IllegalStateException("No valid training instances created");
            }

            Classifier classifier = new NaiveBayes();
            classifier.buildClassifier(trainingData);

            TrainedModel newModel = new TrainedModel(classifier, trainingData, vocabulary);
            this.currentModel = newModel; // einzige atomare Schreiboperation

            persistModel(newModel);

        } catch (Exception e) {
            throw new RuntimeException("Failed to train theme classifier: " + e.getMessage(), e);
        }
    }

    /**
     * Erstellt eine Instanz aus Songtext + festem Vokabular. {@code sentimentScore} wird als
     * Parameter übergeben, da er beim Training (vom Track) und bei der Klassifikation
     * (Placeholder 0.0, da zu diesem Zeitpunkt oft noch nicht final bekannt) unterschiedlich
     * beschafft wird.
     */
    private Instance buildInstance(String lyrics, List<String> vocabulary, int totalAttributes, double sentimentScore) {
        ArtistStyleFeatures features = featureExtractionService.extractStyleFeatures(lyrics);
        Map<String, Double> wordFrequencies = featureExtractionService.extractWordFrequencies(lyrics);

        Instance instance = new DenseInstance(totalAttributes);
        instance.setValue(0, features.avgWordLength());
        instance.setValue(1, features.rhymeDensity());
        instance.setValue(2, features.uniqueWordRatio());
        instance.setValue(3, sentimentScore);

        for (int i = 0; i < vocabulary.size(); i++) {
            double freq = wordFrequencies.getOrDefault(vocabulary.get(i), 0.0);
            instance.setValue(4 + i, freq);
        }

        return instance;
    }

    /**
     * Klassifiziert das Thema eines Songtextes. Liest {@link #currentModel} genau einmal
     * in eine lokale Variable, sodass der Rest der Methode auch bei parallelem Training
     * mit einem konsistenten Snapshot arbeitet.
     */
    public Theme classifyTheme(String lyrics) {
        TrainedModel model = this.currentModel;
        if (model == null) {
            throw new IllegalStateException("Classifier not trained. Call trainThemeClassifier() first.");
        }
        if (lyrics == null || lyrics.isBlank()) {
            return null;
        }

        try {
            Instance instance = buildInstance(lyrics, model.vocabulary(), model.schema().numAttributes(), 0.0);
            instance.setDataset(model.schema());

            double prediction = model.classifier().classifyInstance(instance);
            String themeName = model.schema().classAttribute().value((int) prediction);

            return Theme.valueOf(themeName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to classify theme: " + e.getMessage(), e);
        }
    }

    /**
     * Gibt die Themenverteilung (Wahrscheinlichkeiten) zurück. Wie {@link #classifyTheme},
     * mit demselben konsistenten Snapshot-Zugriff.
     */
    public Map<Theme, Integer> getThemeDistribution(String lyrics) {
        TrainedModel model = this.currentModel;
        if (model == null || lyrics == null || lyrics.isBlank()) {
            return Map.of();
        }

        try {
            Instance instance = buildInstance(lyrics, model.vocabulary(), model.schema().numAttributes(), 0.0);
            instance.setDataset(model.schema());

            double[] probs = model.classifier().distributionForInstance(instance);
            Map<Theme, Integer> distribution = new EnumMap<>(Theme.class);

            for (int i = 0; i < probs.length; i++) {
                Theme theme = Theme.valueOf(model.schema().classAttribute().value(i));
                distribution.put(theme, (int) Math.round(probs[i] * 100));
            }

            return distribution;
        } catch (Exception e) {
            log.warn("Themenverteilung konnte nicht berechnet werden: {}", e.getMessage());
            return Map.of();
        }
    }

    public boolean isTrained() {
        return this.currentModel != null;
    }

    public int getTrainingDataSize() {
        TrainedModel model = this.currentModel;
        return model != null ? model.schema().numInstances() : 0;
    }

    // ==================== PERSISTENZ ====================

    /**
     * Speichert Klassifikator + Trainingsdaten-Schema + Vokabular in einer Datei, damit das
     * Modell nach einem Neustart der Anwendung nicht erneut trainiert werden muss.
     * Ein Fehlschlag beim Speichern lässt das Training selbst nicht fehlschlagen - das
     * frisch trainierte Modell ist bereits im Speicher aktiv, nur die Persistenz für den
     * nächsten Neustart wäre dann nicht gegeben.
     */
    private void persistModel(TrainedModel model) {
        try {
            File file = new File(modelFilePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            // Weka's SerializationHelper.writeAll speichert mehrere Objekte in eine Datei -
            // hier: Classifier, Instances-Schema (für ClassAttribute-Werte) und das Vokabular
            // (als String[], da Weka nicht direkt mit List<String> serialisiert).
            SerializationHelper.writeAll(modelFilePath, new Object[]{
                    model.classifier(),
                    model.schema(),
                    model.vocabulary().toArray(new String[0])
            });

            log.info("Theme-Klassifikator nach '{}' gespeichert ({} Trainingsbeispiele, {} Vokabular-Wörter)",
                    modelFilePath, model.schema().numInstances(), model.vocabulary().size());
        } catch (Exception e) {
            log.warn("Theme-Klassifikator konnte nicht persistiert werden ({}): {}", modelFilePath, e.getMessage());
        }
    }

    /**
     * Lädt ein zuvor persistiertes Modell beim Start der Anwendung, falls vorhanden.
     * Fehlt die Datei (z.B. erster Start) oder ist sie beschädigt/inkompatibel (z.B. nach
     * einem Weka-Versionswechsel), bleibt {@link #currentModel} einfach {@code null} -
     * der Klassifikator muss dann über die GUI erneut trainiert werden. Das verhindert,
     * dass die Anwendung wegen eines Persistenz-Problems gar nicht erst startet.
     */
    private void loadPersistedModelIfPresent() {
        File file = new File(modelFilePath);
        if (!file.exists()) {
            log.info("Kein persistiertes Theme-Modell unter '{}' gefunden - Klassifikator muss trainiert werden.",
                    modelFilePath);
            return;
        }

        try {
            Object[] loaded = SerializationHelper.readAll(modelFilePath);
            Classifier classifier = (Classifier) loaded[0];
            Instances schema = (Instances) loaded[1];
            String[] vocabularyArray = (String[]) loaded[2];

            this.currentModel = new TrainedModel(classifier, schema, Arrays.asList(vocabularyArray));
            log.info("Theme-Klassifikator aus '{}' geladen ({} Trainingsbeispiele).",
                    modelFilePath, schema.numInstances());
        } catch (Exception e) {
            log.warn("Persistiertes Theme-Modell unter '{}' konnte nicht geladen werden ({}) - " +
                    "Klassifikator muss erneut trainiert werden.", modelFilePath, e.getMessage());
        }
    }
}