package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.ArtistStyleFeatures;
import com.lyricsanalyzer.model.LyricsDNA;
import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für die Generierung und Analyse von Lyrics-DNA (Feature-Vektoren für Künstler).
 *
 * Dies ist die EINZIGE Quelle der Wahrheit für "Lyrics DNA" (Feature-Vektor +
 * Themenverteilung + Sentiment). Vorher existierte eine zweite, leicht abweichende
 * DNA-Berechnung in {@link ArtistStyleAnalysisService} (ohne Themenverteilung), die u.a.
 * vom {@code /dna/similar}-Endpunkt genutzt wurde - mit dem Ergebnis, dass {@code /dna/all}
 * und {@code /dna/similar} unterschiedliche Daten für dieselben Künstler lieferten.
 * {@link ArtistStyleAnalysisService} liefert jetzt nur noch reine Stil-Features und die
 * Cosine-Similarity-Utility-Funktion, die hier verwendet wird.
 */
@Service
public class LyricsDNAService {

    private final TrackRepository trackRepository;
    private final ThemeClassificationService themeClassificationService;
    private final ArtistStyleAnalysisService artistStyleAnalysisService;

    public LyricsDNAService(TrackRepository trackRepository,
                            ThemeClassificationService themeClassificationService,
                            ArtistStyleAnalysisService artistStyleAnalysisService) {
        this.trackRepository = trackRepository;
        this.themeClassificationService = themeClassificationService;
        this.artistStyleAnalysisService = artistStyleAnalysisService;
    }

    /**
     * Generiert DNA für alle Künstler mit Themenverteilung.
     */
    public List<LyricsDNA> generateAllDNAWithThemes() {
        List<Track> allTracks = trackRepository.findAll();

        Map<String, List<Track>> tracksByArtist = allTracks.stream()
                .filter(t -> t.getArtistName() != null && !t.getArtistName().isBlank())
                .collect(Collectors.groupingBy(t -> t.getArtistName().toLowerCase()));

        return tracksByArtist.entrySet().stream()
                .map(entry -> {
                    String artistName = capitalize(entry.getKey());
                    List<Track> artistTracks = entry.getValue();

                    double[] featureVector = calculateFeatureVector(artistTracks);
                    double avgSentiment = calculateAvgSentiment(artistTracks);
                    Map<Theme, Integer> themeDistribution = calculateThemeDistribution(artistTracks);

                    return new LyricsDNA(artistName, featureVector, themeDistribution, avgSentiment);
                })
                .toList();
    }

    private String capitalize(String lowerCaseArtistName) {
        return Arrays.stream(lowerCaseArtistName.split(" "))
                .map(word -> word.isEmpty() ? word : word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * Berechnet den Feature-Vektor für eine Liste von Tracks.
     * Enthält jetzt deutlich mehr Features für bessere Künstler-Unterscheidung.
     * 
     * Feature-Vektor Struktur:
     * - Index 0-2: Basis-Text-Features (avgWordLength, rhymeDensity, uniqueWordRatio)
     * - Index 3-7: Erweiterte Stil-Features (avgLineLength, exclamationDensity, questionMarkDensity, capitalWordRatio, lineCount)
     * - Index 8: trackCount (Anzahl der Tracks des Künstlers)
     * - Index 9-13: Top-5 Wörter (Häufigkeiten)
     * - Index 14: avgSentiment
     */
    private double[] calculateFeatureVector(List<Track> tracks) {
        double totalAvgWordLength = 0;
        double totalRhymeDensity = 0;
        double totalUniqueWordRatio = 0;
        double totalAvgLineLength = 0;
        double totalExclamationDensity = 0;
        double totalQuestionMarkDensity = 0;
        double totalCapitalWordRatio = 0;
        int totalLineCount = 0;
        int count = 0;

        // Für die Top-Wörter des Künstlers
        Map<String, Double> aggregatedWordFrequencies = new HashMap<>();

        for (Track track : tracks) {
            if (track.getLyrics() == null || track.getLyrics().isBlank()) {
                continue;
            }

            ArtistStyleFeatures features = artistStyleAnalysisService.analyzeStyle(track.getLyrics());
            totalAvgWordLength += features.avgWordLength();
            totalRhymeDensity += features.rhymeDensity();
            totalUniqueWordRatio += features.uniqueWordRatio();
            totalAvgLineLength += features.avgLineLength();
            totalExclamationDensity += features.exclamationDensity();
            totalQuestionMarkDensity += features.questionMarkDensity();
            totalCapitalWordRatio += features.capitalWordRatio();
            totalLineCount += features.lineCount();
            
            // Aggregiere Wortfrequenzen für die Top-Wörter
            features.topWords().forEach((word, freq) -> 
                aggregatedWordFrequencies.merge(word, freq, Double::sum));
            
            count++;
        }

        if (count == 0) {
            // Fallback: 15 Features (9 Basis + 5 Top-Wörter + avgSentiment)
            return new double[15];
        }

        // Berechne die Top-5 häufigsten Wörter dieses Künstlers
        List<String> topWords = aggregatedWordFrequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        // Erstelle Feature-Vektor: 9 Basis-Features + 5 Top-Wörter + avgSentiment = 15 Features
        double[] featureVector = new double[15];
        
        // Basis-Features (Index 0-8)
        featureVector[0] = totalAvgWordLength / count;           // avgWordLength
        featureVector[1] = totalRhymeDensity / count;          // rhymeDensity
        featureVector[2] = totalUniqueWordRatio / count;       // uniqueWordRatio
        featureVector[3] = totalAvgLineLength / count;         // avgLineLength (NEU)
        featureVector[4] = totalExclamationDensity / count;    // exclamationDensity (NEU)
        featureVector[5] = totalQuestionMarkDensity / count;   // questionMarkDensity (NEU)
        featureVector[6] = totalCapitalWordRatio / count;      // capitalWordRatio (NEU)
        featureVector[7] = (double) totalLineCount / count;     // lineCount (NEU)
        featureVector[8] = count;                             // trackCount (NEU - Anzahl der Tracks)
        
        // Top-5 Wörter als Features (Index 9-13) - Häufigkeit pro Track
        for (int i = 0; i < 5; i++) {
            String word = i < topWords.size() ? topWords.get(i) : "";
            featureVector[9 + i] = aggregatedWordFrequencies.getOrDefault(word, 0.0) / count;
        }
        
        // avgSentiment als letztes Feature (Index 14)
        featureVector[14] = calculateAvgSentiment(tracks);

        return featureVector;
    }

    /**
     * Berechnet den durchschnittlichen Sentiment-Score für eine Liste von Tracks.
     */
    private double calculateAvgSentiment(List<Track> tracks) {
        return tracks.stream()
                .mapToDouble(t -> t.getSentimentScore() != null ? t.getSentimentScore() : 0.0)
                .average()
                .orElse(0.0);
    }

    /**
     * Berechnet die Themenverteilung für eine Liste von Tracks.
     */
    private Map<Theme, Integer> calculateThemeDistribution(List<Track> tracks) {
        Map<Theme, Integer> distribution = new EnumMap<>(Theme.class);
        Arrays.stream(Theme.values()).forEach(t -> distribution.put(t, 0));

        int total = 0;
        for (Track track : tracks) {
            if (track.getLyrics() == null || track.getLyrics().isBlank()) {
                continue;
            }

            try {
                Theme theme = themeClassificationService.classifyTheme(track.getLyrics());
                if (theme != null) {
                    distribution.merge(theme, 1, Integer::sum);
                    total++;
                }
            } catch (Exception e) {
                // Klassifikator nicht trainiert oder Fehler
                continue;
            }
        }

        if (total > 0) {
            for (Theme theme : distribution.keySet()) {
                distribution.put(theme, (int) ((double) distribution.get(theme) / total * 100));
            }
        }

        return distribution;
    }

    /**
     * Generiert DNA für einen spezifischen Künstler.
     */
    public LyricsDNA generateDNA(String artistName) {
        List<Track> tracks = trackRepository.findByArtistNameIgnoreCase(artistName);

        if (tracks.isEmpty()) {
            return null;
        }

        double[] featureVector = calculateFeatureVector(tracks);
        double avgSentiment = calculateAvgSentiment(tracks);
        Map<Theme, Integer> themeDistribution = calculateThemeDistribution(tracks);

        return new LyricsDNA(artistName, featureVector, themeDistribution, avgSentiment);
    }

    /**
     * Bereitet Visualisierungsdaten für die DNA-Scatterplot vor.
     * Nutzt jetzt die ersten beiden Features (avgWordLength, rhymeDensity) für die Position
     * und weitere Features für Farbkodierung und Größe.
     */
    public List<Map<String, Object>> getDNAVisualizationData() {
        List<LyricsDNA> allDNA = generateAllDNAWithThemes();

        // Find min/max for normalization
        double maxAvgWordLength = allDNA.stream().mapToDouble(d -> d.featureVector()[0]).max().orElse(1.0);
        double maxRhymeDensity = allDNA.stream().mapToDouble(d -> d.featureVector()[1]).max().orElse(1.0);
        double maxSentiment = allDNA.stream().mapToDouble(LyricsDNA::averageSentiment).map(Math::abs).max().orElse(1.0);
        double maxExclamationDensity = allDNA.stream().mapToDouble(d -> d.featureVector()[4]).max().orElse(1.0);

        return allDNA.stream()
                .map(dna -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("artist", dna.artistName());

                    // Normalisierte Koordinaten für Visualisierung (0-50)
                    // x = avgWordLength normalisiert
                    // y = rhymeDensity normalisiert
                    result.put("x", (dna.featureVector()[0] / Math.max(maxAvgWordLength, 0.1)) * 50);
                    result.put("y", (dna.featureVector()[1] / Math.max(maxRhymeDensity, 0.1)) * 50);

                    // Blasengröße basierend auf Sentiment und ExclamationDensity (mehr Dynamik)
                    double size = ((Math.abs(dna.averageSentiment()) / Math.max(maxSentiment, 0.1)) * 10 
                            + (dna.featureVector()[4] / Math.max(maxExclamationDensity, 0.1)) * 5) + 5;
                    result.put("size", Math.min(size, 25)); // Max 25px

                    // Farbe basierend auf Sentiment
                    result.put("color", dna.averageSentiment() >= 0 ? "positive" : "negative");

                    // Themenverteilung
                    result.put("themes", dna.themeDistribution());

                    // Top Thema
                    result.put("topTheme", dna.themeDistribution().entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .map(Theme::name)
                            .orElse("UNKNOWN"));

                    result.put("averageSentiment", dna.averageSentiment());
                    result.put("averageSentimentNormalized", SentimentAnalysisService.normalizeScore(dna.averageSentiment()));
                    
                    // Detaillierte Features für Frontend
                    result.put("features", Map.of(
                            "avgWordLength", String.format("%.2f", dna.featureVector()[0]),
                            "rhymeDensity", String.format("%.2f", dna.featureVector()[1]),
                            "uniqueWordRatio", String.format("%.2f", dna.featureVector()[2]),
                            "avgLineLength", String.format("%.2f", dna.featureVector()[3]),
                            "exclamationDensity", String.format("%.2f", dna.featureVector()[4]),
                            "questionMarkDensity", String.format("%.2f", dna.featureVector()[5]),
                            "capitalWordRatio", String.format("%.2f", dna.featureVector()[6]),
                            "lineCount", String.format("%.0f", dna.featureVector()[7]),
                            "trackCount", (int) dna.featureVector()[8]
                    ));

                    return result;
                })
                .toList();
    }

    /**
     * Findet die ähnlichsten Künstler basierend auf DNA.
     * Nutzt jetzt dieselbe DNA-Quelle ({@link #generateAllDNAWithThemes()}) wie
     * {@code /dna/all} und {@code /dna/visualization} - vorher delegierte diese Methode
     * an ArtistStyleAnalysisService und nutzte dadurch eine andere (themen-lose)
     * DNA-Berechnung als der Rest der DNA-Endpunkte.
     */
    public Map<String, Double> findSimilarArtists(String artistName, int limit) {
        List<LyricsDNA> allDNA = generateAllDNAWithThemes();

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
                        dna -> artistStyleAnalysisService.calculateSimilarity(targetDNA, dna)
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

    /**
     * Berechnet die Ähnlichkeit zwischen zwei Künstlern.
     */
    public double calculateSimilarity(String artist1, String artist2) {
        LyricsDNA dna1 = generateDNA(artist1);
        LyricsDNA dna2 = generateDNA(artist2);

        if (dna1 == null || dna2 == null) {
            return 0.0;
        }

        return artistStyleAnalysisService.calculateSimilarity(dna1, dna2);
    }

    /**
     * Gibt alle einzigartigen Themen zurück, die verwendet werden.
     */
    public List<Theme> getAllThemes() {
        return Arrays.asList(Theme.values());
    }
}
