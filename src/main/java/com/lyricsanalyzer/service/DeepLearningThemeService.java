package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.model.Track;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.DropoutLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.evaluation.classification.Evaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Deep-Learning-Themenklassifikation mit Deeplearning4j (DL4J).
 *
 * <h2>Architektur</h2>
 * Das Netz verarbeitet Songtexte als Bag-of-Words-Vektoren (TF-IDF-normalisiert)
 * und klassifiziert sie in {@link Theme}-Kategorien:
 *
 * <pre>
 * Input (Vokabular-Größe, TF-IDF-Vektor)
 *   │
 *   ▼
 * Dense(512, ReLU) + Dropout(0.4)
 *   │
 *   ▼
 * Dense(256, ReLU) + Dropout(0.3)
 *   │
 *   ▼
 * Dense(128, ReLU) + Dropout(0.2)
 *   │
 *   ▼
 * Output(numClasses, Softmax)  ← Wahrscheinlichkeit pro Theme
 * </pre>
 */
@Service
public class DeepLearningThemeService {

    private static final Logger log = LoggerFactory.getLogger(DeepLearningThemeService.class);

    /** Maximale Vokabular-Größe (häufigste N Wörter werden als Features genutzt). */
    private static final int MAX_VOCAB_SIZE = 3000;

    /** Minimale Dokumenthäufigkeit: Wort muss in mindestens N Tracks vorkommen. */
    private static final int MIN_DOC_FREQ = 2;

    private static final int EPOCHS = 1000;
    private static final int BATCH_SIZE = 16;

    /** Theme-Reihenfolge */
    private static final List<Theme> THEME_ORDER = List.of(Theme.values());
    private static final int NUM_CLASSES = THEME_ORDER.size();

    private final FeatureExtractionService featureExtractionService;
    private final String modelFilePath;

    private volatile TrainedDLModel currentModel;

    private final AtomicBoolean isTraining = new AtomicBoolean(false);

    /**
     * Unveränderliches Bundle: Netz + Vokabular + IDF-Gewichte.
     * Wird bei jedem Training komplett neu erzeugt und erst am Ende atomar gesetzt.
     */
    private record TrainedDLModel(
            MultiLayerNetwork network,
            List<String> vocabulary,
            Map<String, Double> idfWeights,
            int trainingSamples
    ) {}

    public DeepLearningThemeService(
            FeatureExtractionService featureExtractionService,
            @Value("${lyrics-analyzer.dl-model.path:./data/dl-theme-classifier.zip}")
            String modelFilePath) {
        this.featureExtractionService = featureExtractionService;
        this.modelFilePath = modelFilePath;
        loadPersistedModelIfPresent();
    }

    // ==================== TRAINING ====================

    /**
     * Trainiert das Deep-Learning-Modell mit gelabelten Tracks.
     *
     * <p>Ablauf:
     * <ol>
     *   <li>Vokabular + IDF-Gewichte aus allen Lyrics berechnen</li>
     *   <li>Jeden Track in einen TF-IDF-Vektor umwandeln</li>
     *   <li>Netzarchitektur aufbauen und initialisieren</li>
     *   <li>{@code EPOCHS} Durchläufe über alle Trainingsdaten</li>
     *   <li>Modell atomar ersetzen und persistieren</li>
     * </ol>
     *
     * @param labeledTracks Tracks mit gesetztem {@link Track#getTheme()}
     *                      und nicht-leerem {@link Track#getLyrics()}
     * @return Trainings-Zusammenfassung
     */
    public TrainingSummary train(List<Track> labeledTracks) {
        if (!isTraining.compareAndSet(false, true)) {
            throw new IllegalStateException("Training läuft bereits. Bitte warten.");
        }

        try {
            // --- 1. Vorbereitung ---
            List<Track> validTracks = labeledTracks.stream()
                    .filter(t -> t.getTheme() != null)
                    .filter(t -> t.getLyrics() != null && !t.getLyrics().isBlank())
                    .collect(Collectors.toList());

            if (validTracks.size() < NUM_CLASSES) {
                throw new IllegalArgumentException(
                        "Zu wenige gelabelte Tracks (" + validTracks.size() + "). " +
                                "Es werden mindestens " + NUM_CLASSES + " benötigt (eine pro Theme-Klasse).");
            }

            log.info("DL-Training startet mit {} Tracks, {} Epochen", validTracks.size(), EPOCHS);

            // --- 2. Vokabular + IDF ---
            List<String> vocabulary = buildVocabulary(validTracks);
            Map<String, Double> idfWeights = computeIdf(validTracks, vocabulary);
            int vocabSize = vocabulary.size();

            log.info("Vokabular: {} Wörter (gefiltert aus max. {})", vocabSize, MAX_VOCAB_SIZE);

            // --- 3. Features + Labels ---
            INDArray features = buildFeatureMatrix(validTracks, vocabulary, idfWeights);
            INDArray labels = buildLabelMatrix(validTracks);

            // --- 4. Netz aufbauen ---
            MultiLayerNetwork network = buildNetwork(vocabSize);
            network.init();
            network.setListeners(new ScoreIterationListener(20));

            // --- 5. Training ---
            DataSet fullDataSet = new DataSet(features, labels);
            List<DataSet> batches = fullDataSet.asList();
            Collections.shuffle(batches, new Random(42));

            List<Double> lossHistory = new ArrayList<>();

            for (int epoch = 0; epoch < EPOCHS; epoch++) {
                double epochLoss = 0;
                int batchCount = 0;
                // Mini-Batch-Training: DataSet in Batches aufteilen
                for (int batchStart = 0; batchStart < batches.size(); batchStart += BATCH_SIZE) {
                    int batchEnd = Math.min(batchStart + BATCH_SIZE, batches.size());
                    List<DataSet> batchList = batches.subList(batchStart, batchEnd);
                    DataSet batch = DataSet.merge(batchList);
                    network.fit(batch);
                    epochLoss += network.score();
                    batchCount++;
                }
                
                double avgLoss = epochLoss / batchCount;
                lossHistory.add(avgLoss);

                if ((epoch + 1) % 5 == 0) {
                    log.info("Epoche {}/{} - Avg Loss: {}", epoch + 1, EPOCHS, String.format("%.4f", avgLoss));
                }
            }

            // --- 6. Evaluation auf Trainingsdaten ---
            INDArray output = network.output(features);

            // Accuracy wie bisher
            double accuracy = computeAccuracy(output, labels, validTracks.size());

            // DL4J-Evaluation erzeugen
            Evaluation eval = new Evaluation(NUM_CLASSES);
            eval.eval(labels, output);

            log.info("DL-Training abgeschlossen. Train-Accuracy: {}%",
                    String.format("%.1f", accuracy * 100));

            // Zusätzliche Statistiken
            System.out.println(eval.stats());

            // Confusion Matrix
            System.out.println("===== Confusion Matrix =====");
            System.out.println(eval.getConfusionMatrix());

            double[][] cmData = new double[NUM_CLASSES][NUM_CLASSES];
            for (int i = 0; i < NUM_CLASSES; i++) {
                for (int j = 0; j < NUM_CLASSES; j++) {
                    cmData[i][j] = eval.getConfusionMatrix().getCount(i, j);
                }
            }
            List<String> themeNames = THEME_ORDER.stream().map(Enum::name).toList();

            // --- 7. Atomar ersetzen + persistieren ---
            TrainedDLModel newModel = new TrainedDLModel(network, vocabulary, idfWeights, validTracks.size());
            this.currentModel = newModel;
            persistModel(newModel);

            return new TrainingSummary(validTracks.size(), vocabSize, EPOCHS, accuracy, lossHistory, cmData, themeNames);

        } finally {
            isTraining.set(false);
        }
    }

    // ==================== INFERENZ ====================

    /**
     * Klassifiziert das wahrscheinlichste Theme für einen Songtext.
     *
     * toTfIdfVector liefert Shape [1, vocabSize]. network.output() gibt [1, numClasses] zurück.
     * argmax(1) arbeitet auf der Klassen-Dimension (Achse 1) des 2D-Tensors – korrekt.
     * Wir lesen das Ergebnis über getInt(0) aus dem resultierenden [1]-Shape-Array.
     */
    public Theme classify(String lyrics) {
        TrainedDLModel model = requireModel();
        INDArray vector = toTfIdfVector(lyrics, model.vocabulary(), model.idfWeights());
        // output Shape: [1, numClasses]
        INDArray output = model.network().output(vector).reshape(1, NUM_CLASSES);
        int predicted = Nd4j.argMax(output, 1).getInt(0);
        return THEME_ORDER.get(predicted);
    }

    /**
     * Gibt die Wahrscheinlichkeitsverteilung über alle Themes zurück (0-100%).
     * output.getDouble(0, i) greift auf Zeile 0, Spalte i des [1, numClasses]-Tensors zu.
     */
    public Map<Theme, Integer> getThemeDistribution(String lyrics) {
        TrainedDLModel model = requireModel();
        INDArray vector = toTfIdfVector(lyrics, model.vocabulary(), model.idfWeights());
        // output Shape: [1, numClasses]
        INDArray output = model.network().output(vector).reshape(1, NUM_CLASSES);

        Map<Theme, Integer> distribution = new EnumMap<>(Theme.class);
        for (int i = 0; i < NUM_CLASSES; i++) {
            double prob = output.getDouble(0, i);
            distribution.put(THEME_ORDER.get(i), (int) Math.round(prob * 100));
        }
        return distribution;
    }

    /**
     * Gibt den Konfidenz-Score (0.0-1.0) der wahrscheinlichsten Klasse zurück.
     */
    public double getConfidence(String lyrics) {
        TrainedDLModel model = requireModel();
        INDArray vector = toTfIdfVector(lyrics, model.vocabulary(), model.idfWeights());
        INDArray output = model.network().output(vector).reshape(1, NUM_CLASSES);
        // Max-Wahrscheinlichkeit: Nd4j.max() liefert den globalen Max-Wert als Scalar-Array
        return Nd4j.max(output).getDouble(0);
    }

    public boolean isTrained() {
        return currentModel != null;
    }

    public boolean isCurrentlyTraining() {
        return isTraining.get();
    }

    public int getTrainingSamples() {
        TrainedDLModel m = currentModel;
        return m != null ? m.trainingSamples() : 0;
    }

    public int getVocabSize() {
        TrainedDLModel m = currentModel;
        return m != null ? m.vocabulary().size() : 0;
    }

    // ==================== NETZ-ARCHITEKTUR ====================

    /**
     * Baut das MLP-Netz auf.
     *
     * <pre>
     * Input (vocabSize)
     *   Dense(512, ReLU)
     *   Dropout(0.4)         ← Regularisierung gegen Overfitting
     *   Dense(256, ReLU)
     *   Dropout(0.3)
     *   Dense(128, ReLU)
     *   Dropout(0.2)
     *   Output(numClasses, Softmax)
     * </pre>
     *
     * Adam-Optimizer mit lr=0.001 (Standard, gut für Textklassifikation).
     * L2-Regularisierung (0.0001) zusätzlich zu Dropout.
     */
    private MultiLayerNetwork buildNetwork(int vocabSize) {
        MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
                .seed(42)
                .updater(new Adam(0.001))
                .weightInit(WeightInit.XAVIER)
                .l2(0.0001)
                .list()
                // Block 1
                .layer(new DenseLayer.Builder()
                        .nIn(vocabSize)
                        .nOut(512)
                        .activation(Activation.RELU)
                        .build())
                .layer(new DropoutLayer.Builder(0.4).build())
                // Block 2
                .layer(new DenseLayer.Builder()
                        .nIn(512)
                        .nOut(256)
                        .activation(Activation.RELU)
                        .build())
                .layer(new DropoutLayer.Builder(0.3).build())
                // Block 3
                .layer(new DenseLayer.Builder()
                        .nIn(256)
                        .nOut(128)
                        .activation(Activation.RELU)
                        .build())
                .layer(new DropoutLayer.Builder(0.2).build())
                // Output
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .nIn(128)
                        .nOut(NUM_CLASSES)
                        .activation(Activation.SOFTMAX)
                        .build())
                .build();

        return new MultiLayerNetwork(config);
    }

    // ==================== FEATURE-ENGINEERING ====================

    /**
     * Baut das Vokabular aus allen Trainings-Lyrics.
     * Filtert nach Dokumenthäufigkeit (min. MIN_DOC_FREQ Tracks) und
     * begrenzt auf MAX_VOCAB_SIZE Wörter (nach Häufigkeit sortiert).
     */
    private List<String> buildVocabulary(List<Track> tracks) {
        // Dokumenthäufigkeit: in wie vielen Tracks kommt das Wort vor?
        Map<String, Integer> docFreq = new HashMap<>();

        for (Track track : tracks) {
            Set<String> wordsInDoc = new HashSet<>(
                    featureExtractionService.extractWordFrequencies(track.getLyrics()).keySet());
            for (String word : wordsInDoc) {
                docFreq.merge(word, 1, Integer::sum);
            }
        }

        // Gesamthäufigkeit (TF über alle Docs)
        Map<String, Double> totalFreq = new HashMap<>();
        for (Track track : tracks) {
            featureExtractionService.extractWordFrequencies(track.getLyrics())
                    .forEach((word, freq) -> totalFreq.merge(word, freq, Double::sum));
        }

        return totalFreq.entrySet().stream()
                .filter(e -> docFreq.getOrDefault(e.getKey(), 0) >= MIN_DOC_FREQ)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(MAX_VOCAB_SIZE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Berechnet IDF-Gewichte für das Vokabular.
     * IDF = log((N + 1) / (df + 1)) + 1  (scikit-learn smooth IDF)
     */
    private Map<String, Double> computeIdf(List<Track> tracks, List<String> vocabulary) {
        int N = tracks.size();
        Map<String, Integer> docFreq = new HashMap<>();

        for (Track track : tracks) {
            Set<String> wordsInDoc = new HashSet<>(
                    featureExtractionService.extractWordFrequencies(track.getLyrics()).keySet());
            for (String word : wordsInDoc) {
                if (vocabulary.contains(word)) {
                    docFreq.merge(word, 1, Integer::sum);
                }
            }
        }

        Map<String, Double> idf = new HashMap<>();
        for (String word : vocabulary) {
            int df = docFreq.getOrDefault(word, 0);
            idf.put(word, Math.log((double) (N + 1) / (df + 1)) + 1.0);
        }
        return idf;
    }

    /**
     * Wandelt Lyrics in einen L2-normalisierten TF-IDF-Vektor um.
     * Jede Dimension entspricht einem Vokabular-Wort.
     */
    private INDArray toTfIdfVector(String lyrics, List<String> vocabulary, Map<String, Double> idfWeights) {
        Map<String, Double> tf = featureExtractionService.extractWordFrequencies(lyrics);
        double[] vector = new double[vocabulary.size()];

        for (int i = 0; i < vocabulary.size(); i++) {
            String word = vocabulary.get(i);
            double tfVal = tf.getOrDefault(word, 0.0);
            double idfVal = idfWeights.getOrDefault(word, 1.0);
            vector[i] = tfVal * idfVal;
        }

        INDArray ndArray = Nd4j.create(vector).reshape(1, vocabulary.size());

        // L2-Normalisierung
        double norm = ndArray.norm2Number().doubleValue();
        if (norm > 0) {
            ndArray.divi(norm);
        }

        return ndArray;
    }

    /** Feature-Matrix für alle Trainingstracks: Shape [numTracks, vocabSize]. */
    private INDArray buildFeatureMatrix(List<Track> tracks, List<String> vocabulary,
                                        Map<String, Double> idfWeights) {
        INDArray matrix = Nd4j.zeros(tracks.size(), vocabulary.size());
        for (int i = 0; i < tracks.size(); i++) {
            INDArray row = toTfIdfVector(tracks.get(i).getLyrics(), vocabulary, idfWeights);
            matrix.putRow(i, row);
        }
        return matrix;
    }

    /** One-Hot-Label-Matrix: Shape [numTracks, numClasses]. */
    private INDArray buildLabelMatrix(List<Track> tracks) {
        INDArray labels = Nd4j.zeros(tracks.size(), NUM_CLASSES);
        for (int i = 0; i < tracks.size(); i++) {
            int classIdx = THEME_ORDER.indexOf(tracks.get(i).getTheme());
            if (classIdx >= 0) {
                labels.putScalar(new int[]{i, classIdx}, 1.0);
            }
        }
        return labels;
    }

    private double computeAccuracy(INDArray output, INDArray labels, int n) {
        // argMax über die gesamte Matrix auf Achse 1 (Klassen-Dimension) -
        // das ist stabiler als zeilenweises getRow(), weil getRow() in manchen
        // ND4J-Versionen einen 1D-View liefert, auf dem argmax(..., 1) fehlschlägt.
        INDArray predictedClasses = Nd4j.argMax(output, 1);  // Shape [n]
        INDArray actualClasses    = Nd4j.argMax(labels, 1);  // Shape [n]

        int correct = 0;
        for (int i = 0; i < n; i++) {
            if (predictedClasses.getInt(i) == actualClasses.getInt(i)) {
                correct++;
            }
        }
        return (double) correct / n;
    }

    private TrainedDLModel requireModel() {
        TrainedDLModel m = currentModel;
        if (m == null) {
            throw new IllegalStateException(
                    "DL-Modell noch nicht trainiert. Bitte zuerst /api/analysis/dl/train aufrufen.");
        }
        return m;
    }

    // ==================== PERSISTENZ ====================

    /**
     * Speichert Netz + Vokabular + IDF als ZIP-Datei.
     * DL4J's {@link ModelSerializer} serialisiert das Netz (Architektur + Gewichte).
     * Vokabular und IDF werden zusätzlich als Java-Serialisierung beigelegt
     * (per Custom-Wrapper-Datei).
     */
    @SuppressWarnings("unchecked")
    private void persistModel(TrainedDLModel model) {
        try {
            File modelFile = new File(modelFilePath);
            File parent = modelFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            // Netz als DL4J-ZIP
            ModelSerializer.writeModel(model.network(), modelFile, true);

            // Vokabular + IDF als separate Datei neben dem Modell
            File metaFile = new File(modelFilePath + ".meta");
            try (java.io.ObjectOutputStream oos =
                         new java.io.ObjectOutputStream(new java.io.FileOutputStream(metaFile))) {
                oos.writeObject(new ArrayList<>(model.vocabulary()));
                oos.writeObject(new HashMap<>(model.idfWeights()));
                oos.writeInt(model.trainingSamples());
            }

            log.info("DL-Modell gespeichert unter '{}' ({} Tracks, {} Vocab-Wörter)",
                    modelFilePath, model.trainingSamples(), model.vocabulary().size());

        } catch (Exception e) {
            log.warn("DL-Modell konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPersistedModelIfPresent() {
        File modelFile = new File(modelFilePath);
        File metaFile = new File(modelFilePath + ".meta");

        if (!modelFile.exists() || !metaFile.exists()) {
            log.info("Kein persistiertes DL-Modell unter '{}' — muss trainiert werden.", modelFilePath);
            return;
        }

        try {
            MultiLayerNetwork network = ModelSerializer.restoreMultiLayerNetwork(modelFile);

            List<String> vocabulary;
            Map<String, Double> idfWeights;
            int trainingSamples;

            try (java.io.ObjectInputStream ois =
                         new java.io.ObjectInputStream(new java.io.FileInputStream(metaFile))) {
                vocabulary = (List<String>) ois.readObject();
                idfWeights = (Map<String, Double>) ois.readObject();
                trainingSamples = ois.readInt();
            }

            this.currentModel = new TrainedDLModel(network, vocabulary, idfWeights, trainingSamples);
            log.info("DL-Modell geladen: {} Tracks, {} Vocab-Wörter.", trainingSamples, vocabulary.size());

        } catch (Exception e) {
            log.warn("DL-Modell unter '{}' konnte nicht geladen werden ({}). Muss neu trainiert werden.",
                    modelFilePath, e.getMessage());
        }
    }

    // ==================== DTOs ====================

    public record TrainingSummary(
            int trainingSamples,
            int vocabSize,
            int epochs,
            double trainAccuracy,
            List<Double> lossHistory,
            double[][] confusionMatrix,
            List<String> themeLabels
    ) {}
}