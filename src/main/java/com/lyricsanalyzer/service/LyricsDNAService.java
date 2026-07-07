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
     * Feature-Vektor Struktur (20 Features):
     * - Index 0-2: Basis-Text-Features (avgWordLength, rhymeDensity, uniqueWordRatio)
     * - Index 3-7: Erweiterte Stil-Features (avgLineLength, exclamationDensity, questionMarkDensity, capitalWordRatio, lineCount)
     * - Index 8: trackCount (Anzahl der Tracks des Künstlers MIT Lyrics)
     * - Index 9-13: Top-5 Wörter (Häufigkeiten)
     * - Index 14-18: Genre One-Hot-Encoding (5 binäre Features: Rock, Pop, Schlager, Rap, Sonstige)
     * - Index 19: avgSentiment
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
        
        // Für Genre-Bestimmung: Sammle ALLE Genres der Tracks (auch ohne Lyrics)
        Map<String, Integer> genreCounts = new HashMap<>();

        for (Track track : tracks) {
            // Sammle Genre-Informationen von ALLEN Tracks (auch ohne Lyrics)
            if (track.getGenre() != null && !track.getGenre().isBlank()) {
                genreCounts.merge(track.getGenre(), 1, Integer::sum);
            }
            
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

        // Bestimme das Hauptgenre (häufigstes Genre) - jetzt basierend auf ALLEN Tracks
        int genreCategory = 5; // Default: Sonstige
        if (!genreCounts.isEmpty()) {
            String mainGenre = genreCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
            genreCategory = getGenreCategory(mainGenre);
        }

        if (count == 0) {
            // Fallback: 20 Features (10 Basis + 5 Top-Wörter + 5 Genre + avgSentiment)
            return new double[20];
        }

        // Berechne die Top-5 häufigsten Wörter dieses Künstlers
        List<String> topWords = aggregatedWordFrequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        // Erstelle Feature-Vektor: 10 Basis-Features + 5 Top-Wörter + 5 Genre-Features + avgSentiment = 20 Features
        double[] featureVector = new double[20];
        
        // Basis-Features (Index 0-8) - NORMALISIERT auf ähnliche Skalen
        // Typische Werte: avgWordLength 4-6 → /10 = 0.4-0.6
        featureVector[0] = (totalAvgWordLength / count) / 10.0;           // avgWordLength normalisiert
        featureVector[1] = totalRhymeDensity / count;          // rhymeDensity (bereits 0-1)
        featureVector[2] = totalUniqueWordRatio / count;       // uniqueWordRatio (bereits 0-1)
        // avgLineLength typisch 20-40 → /40 = 0.5-1.0
        featureVector[3] = (totalAvgLineLength / count) / 40.0;         // avgLineLength normalisiert
        // exclamationDensity typisch 0-5 → /5 = 0-1
        featureVector[4] = (totalExclamationDensity / count) / 5.0;    // exclamationDensity normalisiert
        // questionMarkDensity typisch 0-5 → /5 = 0-1
        featureVector[5] = (totalQuestionMarkDensity / count) / 5.0;   // questionMarkDensity normalisiert
        featureVector[6] = totalCapitalWordRatio / count;      // capitalWordRatio (bereits 0-1)
        // lineCount typisch 10-60 → /60 = 0.17-1.0
        featureVector[7] = ((double) totalLineCount / count) / 60.0;     // lineCount normalisiert
        // trackCount typisch 1-50 → /50 = 0.02-1.0
        featureVector[8] = count / 50.0;                             // trackCount normalisiert
        
        // Top-5 Wörter als Features (Index 9-13) - bereits im Bereich 0-0.1
        for (int i = 0; i < 5; i++) {
            String word = i < topWords.size() ? topWords.get(i) : "";
            featureVector[9 + i] = aggregatedWordFrequencies.getOrDefault(word, 0.0) / count;
        }
        
        // Genre One-Hot-Encoding (Index 14-18): 5 binäre Features
        // Jedes Feature ist 1.0 wenn der Künstler dieses Genre hat, sonst 0.0
        // Dies gibt dem Genre viel mehr Gewicht in der Cosine Similarity
        featureVector[14] = (genreCategory == 1) ? 1.0 : 0.0;  // Rock
        featureVector[15] = (genreCategory == 2) ? 1.0 : 0.0;  // Pop  
        featureVector[16] = (genreCategory == 3) ? 1.0 : 0.0;  // Schlager
        featureVector[17] = (genreCategory == 4) ? 1.0 : 0.0;  // Rap
        featureVector[18] = (genreCategory == 5) ? 1.0 : 0.0;  // Sonstige
        
        // avgSentiment als letztes Feature (Index 19) - typisch -2 bis 2
        // Normalisierung: -2→0.0, 0→0.5, 2→1.0 (Linear transformation)
        double avgSentiment = calculateAvgSentiment(tracks);
        featureVector[19] = (avgSentiment + 2.0) / 4.0;  // Normalisiert auf 0.0-1.0

        return featureVector;
    }

    /**
     * Berechnet den durchschnittlichen Sentiment-Score für eine Liste von Tracks.
     * Berücksichtigt nur Tracks mit Sentiment-Scores.
     */
    private double calculateAvgSentiment(List<Track> tracks) {
        return tracks.stream()
                .filter(t -> t.getSentimentScore() != null)
                .mapToDouble(Track::getSentimentScore)
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
        
        // Genre-Kategorien für Farbkodierung
        Map<Integer, String> genreColors = Map.of(
            1, "rock",    // Rock
            2, "pop",    // Pop  
            3, "schlager", // Schlager
            4, "rap",    // Rap
            5, "other"   // Sonstige
        );

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

                    // Farbe basierend auf Genre-Kategorie (besser als nur Sentiment!)
                    // One-Hot-Encoding: finde welches Genre-Feature 1.0 ist
                    int genreCategory = 0;
                    if (dna.featureVector()[14] == 1.0) genreCategory = 1; // Rock
                    else if (dna.featureVector()[15] == 1.0) genreCategory = 2; // Pop
                    else if (dna.featureVector()[16] == 1.0) genreCategory = 3; // Schlager
                    else if (dna.featureVector()[17] == 1.0) genreCategory = 4; // Rap
                    else if (dna.featureVector()[18] == 1.0) genreCategory = 5; // Sonstige
                    result.put("color", genreColors.getOrDefault(genreCategory, "other"));

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
                    
                    // Genre-Information für bessere Lesbarkeit
                    // One-Hot-Encoding: finde welches Genre-Feature 1.0 ist
                    int genreCat = 0;
                    if (dna.featureVector()[14] == 1.0) genreCat = 1; // Rock
                    else if (dna.featureVector()[15] == 1.0) genreCat = 2; // Pop
                    else if (dna.featureVector()[16] == 1.0) genreCat = 3; // Schlager
                    else if (dna.featureVector()[17] == 1.0) genreCat = 4; // Rap
                    else if (dna.featureVector()[18] == 1.0) genreCat = 5; // Sonstige
                    result.put("genreCategory", genreCat);
                    result.put("genreName", getGenreName(genreCat));

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

    /**
     * Konvertiert ein Genre-String in eine numerische Kategorie.
     * Dies ermöglicht bessere Unterscheidung zwischen Künstlern verschiedener Genres.
     * Erweitert um mehr Genre-Varianten, insbesondere für deutsche Musik.
     */
    private int getGenreCategory(String genre) {
        if (genre == null || genre.isBlank()) {
            return 5; // Sonstige
        }
        
        String lowerGenre = genre.toLowerCase();
        
        // Rock-Familie
        if (lowerGenre.contains("rock") || lowerGenre.contains("metal") || 
            lowerGenre.contains("alternative") || lowerGenre.contains("punk") ||
            lowerGenre.contains("hard rock") || lowerGenre.contains("grunge") ||
            lowerGenre.contains("indie") || lowerGenre.contains("gothic") ||
            lowerGenre.contains("progressive") || lowerGenre.contains("symphonic")) {
            return 1; // Rock
        }
        
        // Pop-Familie
        if (lowerGenre.contains("pop") || lowerGenre.contains("dance") || 
            lowerGenre.contains("disco") || lowerGenre.contains("electro") ||
            lowerGenre.contains("house") || lowerGenre.contains("techno") ||
            lowerGenre.contains("synth") || lowerGenre.contains("edm") ||
            lowerGenre.contains("r&b") || lowerGenre.contains("soul") ||
            lowerGenre.contains("funk")) {
            return 2; // Pop
        }
        
        // Schlager/Volksmusik
        if (lowerGenre.contains("schlager") || lowerGenre.contains("volksmusik") ||
            lowerGenre.contains("deutsche musik") || lowerGenre.contains("chanson") ||
            lowerGenre.contains("volkslied") || lowerGenre.contains("schlager & volksmusik") ||
            lowerGenre.contains("deutscher schlager") || lowerGenre.contains("partyschlager")) {
            return 3; // Schlager
        }
        
        // Rap/Hip-Hop
        if (lowerGenre.contains("rap") || lowerGenre.contains("hip hop") ||
            lowerGenre.contains("hip-hop") || lowerGenre.contains("trap") ||
            lowerGenre.contains("drill") || lowerGenre.contains("gangsta")) {
            return 4; // Rap
        }
        
        // Sonstige - jetzt mit mehr Kategorien
        // Kinderlieder/Kids
        if (lowerGenre.contains("kids") || lowerGenre.contains("kind") || 
            lowerGenre.contains("children") || lowerGenre.contains("kinder")) {
            return 5; // Sonstige
        }
        
        // Elektro/Synth
        if (lowerGenre.contains("electronic") || lowerGenre.contains("trance") || 
            lowerGenre.contains("ambient") || lowerGenre.contains("dubstep")) {
            return 2; // Pop (als Oberkategorie)
        }
        
        // Klassik/Jazz
        if (lowerGenre.contains("classic") || lowerGenre.contains("klassik") || 
            lowerGenre.contains("jazz") || lowerGenre.contains("blues") ||
            lowerGenre.contains("opera")) {
            return 5; // Sonstige
        }
        
        // Weltmusik/Reggae etc.
        if (lowerGenre.contains("reggae") || lowerGenre.contains("ska") || 
            lowerGenre.contains("world") || lowerGenre.contains("folk") ||
            lowerGenre.contains("country") || lowerGenre.contains("blues")) {
            return 5; // Sonstige
        }
        
        // Default Sonstige
        return 5;
    }

    /**
     * Konvertiert Genre-Kategorie in lesbaren Namen.
     */
    private String getGenreName(int genreCategory) {
        return switch (genreCategory) {
            case 1 -> "Rock";
            case 2 -> "Pop";
            case 3 -> "Schlager";
            case 4 -> "Rap";
            default -> "Sonstige";
        };
    }
}
