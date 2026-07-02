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
     */
    private double[] calculateFeatureVector(List<Track> tracks) {
        double totalAvgWordLength = 0;
        double totalRhymeDensity = 0;
        double totalUniqueWordRatio = 0;
        int count = 0;

        for (Track track : tracks) {
            if (track.getLyrics() == null || track.getLyrics().isBlank()) {
                continue;
            }

            ArtistStyleFeatures features = artistStyleAnalysisService.analyzeStyle(track.getLyrics());
            totalAvgWordLength += features.avgWordLength();
            totalRhymeDensity += features.rhymeDensity();
            totalUniqueWordRatio += features.uniqueWordRatio();
            count++;
        }

        if (count == 0) {
            return new double[]{0, 0, 0, 0};
        }

        return new double[]{
                totalAvgWordLength / count,
                totalRhymeDensity / count,
                totalUniqueWordRatio / count,
                calculateAvgSentiment(tracks)
        };
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
     */
    public List<Map<String, Object>> getDNAVisualizationData() {
        List<LyricsDNA> allDNA = generateAllDNAWithThemes();

        // Find min/max for normalization
        double maxX = allDNA.stream().mapToDouble(d -> d.featureVector()[0]).max().orElse(1.0);
        double maxY = allDNA.stream().mapToDouble(d -> d.featureVector()[1]).max().orElse(1.0);
        double maxSentiment = allDNA.stream().mapToDouble(LyricsDNA::averageSentiment).map(Math::abs).max().orElse(1.0);

        return allDNA.stream()
                .map(dna -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("artist", dna.artistName());

                    // Normalisierte Koordinaten für Visualisierung (0-50)
                    // x = avgWordLength normalisiert
                    // y = rhymeDensity normalisiert
                    result.put("x", (dna.featureVector()[0] / Math.max(maxX, 0.1)) * 50);
                    result.put("y", (dna.featureVector()[1] / Math.max(maxY, 0.1)) * 50);

                    // Blasengröße basierend auf Sentiment (8-20px)
                    result.put("size", (Math.abs(dna.averageSentiment()) / Math.max(maxSentiment, 0.1)) * 15 + 5);

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