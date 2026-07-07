package com.lyricsanalyzer.controller;

import com.lyricsanalyzer.model.ArtistStyleFeatures;
import com.lyricsanalyzer.model.LyricsDNA;
import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.service.*;
import com.lyricsanalyzer.repository.TrackRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-Controller für KI-basierte Analysen.
 *
 * Hinweis zu /artist/compare und /artist/similar: Diese delegieren inhaltlich an dieselbe
 * DNA-Berechnung wie /dna/{artistName} und /dna/similar (siehe LyricsDNAService - jetzt die
 * einzige Quelle der Wahrheit für Lyrics-DNA). Sie bleiben als eigene Endpunkte erhalten,
 * da das bestehende Frontend (js/api.js: compareArtists, findSimilarArtists) sie nutzt
 * und ihr Antwortformat (z.B. "similarity" als formatierter Prozent-String) sich von
 * /dna/similar unterscheidet.
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final ThemeClassificationService themeClassificationService;
    private final ArtistStyleAnalysisService artistStyleAnalysisService;
    private final LyricsDNAService lyricsDNAService;
    private final TrackRepository trackRepository;

    public AnalysisController(ThemeClassificationService themeClassificationService,
                              ArtistStyleAnalysisService artistStyleAnalysisService,
                              LyricsDNAService lyricsDNAService,
                              TrackRepository trackRepository) {
        this.themeClassificationService = themeClassificationService;
        this.artistStyleAnalysisService = artistStyleAnalysisService;
        this.lyricsDNAService = lyricsDNAService;
        this.trackRepository = trackRepository;
    }

    // ==================== THEME CLASSIFICATION ====================

    @PostMapping("/theme/train")
    public ResponseEntity<Map<String, Object>> trainThemeClassifier() {
        try {
            List<com.lyricsanalyzer.model.Track> labeledTracks = trackRepository.findAll().stream()
                    .filter(t -> t.getTheme() != null)
                    .toList();

            if (labeledTracks.isEmpty()) {
                Map<String, Object> errorBody = new LinkedHashMap<>();
                errorBody.put("error", "No tracks with theme labels found. Please label some tracks first.");
                errorBody.put("message", "Add theme labels to tracks in the database and try again.");
                return ResponseEntity.badRequest().body(errorBody);
            }

            themeClassificationService.trainThemeClassifier(labeledTracks);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Theme classifier trained successfully");
            result.put("trainingSamples", themeClassificationService.getTrainingDataSize());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Failed to train classifier");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    @GetMapping("/theme/classify")
    public ResponseEntity<Map<String, Object>> classifyTheme(
            @RequestParam String artist,
            @RequestParam String title) {
        try {
            com.lyricsanalyzer.model.Track track = trackRepository
                    .findByArtistNameIgnoreCaseAndTitleIgnoreCase(artist, title)
                    .orElseThrow(() -> new RuntimeException("Track not found"));

            if (track.getLyrics() == null || track.getLyrics().isBlank()) {
                Map<String, Object> errorBody = new LinkedHashMap<>();
                errorBody.put("error", "No lyrics available for this track");
                return ResponseEntity.badRequest().body(errorBody);
            }

            Theme theme = themeClassificationService.classifyTheme(track.getLyrics());
            Map<Theme, Integer> distribution = themeClassificationService.getThemeDistribution(track.getLyrics());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("trackId", track.getId());
            result.put("artist", artist);
            result.put("title", title);
            result.put("predictedTheme", theme != null ? theme.name() : null);
            result.put("themeDistribution", distribution);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Classification failed");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    @PostMapping("/theme/classify-all")
    public ResponseEntity<Map<String, Object>> classifyAllTracks() {
        try {
            List<com.lyricsanalyzer.model.Track> tracks = trackRepository.findAll();
            int classified = 0;
            int skipped = 0;

            for (com.lyricsanalyzer.model.Track track : tracks) {
                if (track.getLyrics() == null || track.getLyrics().isBlank() || track.getTheme() != null) {
                    skipped++;
                    continue;
                }

                Theme theme = themeClassificationService.classifyTheme(track.getLyrics());
                track.setTheme(theme);
                trackRepository.save(track);
                classified++;
            }

            Map<String, Object> classificationResult = new LinkedHashMap<>();
            classificationResult.put("message", "Theme classification completed");
            classificationResult.put("classifiedTracks", classified);
            classificationResult.put("skippedTracks", skipped);
            return ResponseEntity.ok(classificationResult);
        } catch (Exception e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Classification failed");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    @GetMapping("/theme/trained")
    public ResponseEntity<Map<String, Object>> isThemeClassifierTrained() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trained", themeClassificationService.isTrained());
        result.put("trainingSamples", themeClassificationService.getTrainingDataSize());
        return ResponseEntity.ok(result);
    }

    // ==================== ARTIST STYLE ANALYSIS ====================

    @GetMapping("/artist/style/{artistName}")
    public ResponseEntity<Map<String, Object>> getArtistStyle(@PathVariable String artistName) {
        try {
            ArtistStyleFeatures features = artistStyleAnalysisService.analyzeStyleForArtist(artistName);

            if (features == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("artist", artistName);
            result.put("avgWordLength", features.avgWordLength());
            result.put("rhymeDensity", features.rhymeDensity());
            result.put("uniqueWordRatio", features.uniqueWordRatio());
            result.put("topWords", features.topWords());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Style analysis failed");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    /**
     * Nutzt jetzt lyricsDNAService.generateDNA() statt der entfernten
     * artistStyleAnalysisService.generateLyricsDNA() - liefert dadurch dieselben,
     * vollständigen DNA-Daten (inkl. Themenverteilung) wie /dna/{artistName}.
     */
    @GetMapping("/artist/compare")
    public ResponseEntity<Map<String, Object>> compareArtists(
            @RequestParam String artist1,
            @RequestParam String artist2) {
        try {
            LyricsDNA dna1 = lyricsDNAService.generateDNA(artist1);
            LyricsDNA dna2 = lyricsDNAService.generateDNA(artist2);

            if (dna1 == null || dna2 == null) {
                Map<String, Object> errorBody = new LinkedHashMap<>();
                errorBody.put("error", "One or both artists not found or have no lyrics");
                return ResponseEntity.badRequest().body(errorBody);
            }

            double similarity = artistStyleAnalysisService.calculateSimilarity(dna1, dna2);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("artist1", artist1);
            result.put("artist2", artist2);
            result.put("similarity", String.format("%.2f", similarity * 100) + "%");
            result.put("similarityScore", similarity);

            Map<String, Object> featuresArtist1 = new LinkedHashMap<>();
            // Alle 20 Features anzeigen
            featuresArtist1.put("avgWordLength", dna1.featureVector()[0]);
            featuresArtist1.put("rhymeDensity", dna1.featureVector()[1]);
            featuresArtist1.put("uniqueWordRatio", dna1.featureVector()[2]);
            featuresArtist1.put("avgLineLength", dna1.featureVector()[3]);
            featuresArtist1.put("exclamationDensity", dna1.featureVector()[4]);
            featuresArtist1.put("questionMarkDensity", dna1.featureVector()[5]);
            featuresArtist1.put("capitalWordRatio", dna1.featureVector()[6]);
            featuresArtist1.put("lineCount", dna1.featureVector()[7]);
            // trackCount als normalisierter Wert (0-1) + Originalwert
            featuresArtist1.put("trackCount", dna1.featureVector()[8]);
            featuresArtist1.put("trackCountOriginal", Math.round(dna1.featureVector()[8] * 50));
            // Top-5 Wörter
            featuresArtist1.put("topWord1", dna1.featureVector()[9]);
            featuresArtist1.put("topWord2", dna1.featureVector()[10]);
            featuresArtist1.put("topWord3", dna1.featureVector()[11]);
            featuresArtist1.put("topWord4", dna1.featureVector()[12]);
            featuresArtist1.put("topWord5", dna1.featureVector()[13]);
            // Genre One-Hot-Encoding (5 binäre Features: Rock, Pop, Schlager, Rap, Sonstige)
            featuresArtist1.put("genreRock", dna1.featureVector()[14]);
            featuresArtist1.put("genrePop", dna1.featureVector()[15]);
            featuresArtist1.put("genreSchlager", dna1.featureVector()[16]);
            featuresArtist1.put("genreRap", dna1.featureVector()[17]);
            featuresArtist1.put("genreSonstige", dna1.featureVector()[18]);
            
            // Genre-Kategorie und Name für bessere Lesbarkeit
            int genreCategory1 = 0;
            if (dna1.featureVector()[14] == 1.0) genreCategory1 = 1;
            else if (dna1.featureVector()[15] == 1.0) genreCategory1 = 2;
            else if (dna1.featureVector()[16] == 1.0) genreCategory1 = 3;
            else if (dna1.featureVector()[17] == 1.0) genreCategory1 = 4;
            else if (dna1.featureVector()[18] == 1.0) genreCategory1 = 5;
            featuresArtist1.put("genreCategory", genreCategory1);
            featuresArtist1.put("genreName", getGenreName(genreCategory1));
            
            featuresArtist1.put("avgSentiment", dna1.averageSentiment());
            featuresArtist1.put("avgSentimentNormalized", SentimentAnalysisService.normalizeScore(dna1.averageSentiment()));

            Map<String, Object> featuresArtist2 = new LinkedHashMap<>();
            // Alle 20 Features anzeigen
            featuresArtist2.put("avgWordLength", dna2.featureVector()[0]);
            featuresArtist2.put("rhymeDensity", dna2.featureVector()[1]);
            featuresArtist2.put("uniqueWordRatio", dna2.featureVector()[2]);
            featuresArtist2.put("avgLineLength", dna2.featureVector()[3]);
            featuresArtist2.put("exclamationDensity", dna2.featureVector()[4]);
            featuresArtist2.put("questionMarkDensity", dna2.featureVector()[5]);
            featuresArtist2.put("capitalWordRatio", dna2.featureVector()[6]);
            featuresArtist2.put("lineCount", dna2.featureVector()[7]);
            // trackCount als normalisierter Wert (0-1) + Originalwert
            featuresArtist2.put("trackCount", dna2.featureVector()[8]);
            featuresArtist2.put("trackCountOriginal", Math.round(dna2.featureVector()[8] * 50));
            // Top-5 Wörter
            featuresArtist2.put("topWord1", dna2.featureVector()[9]);
            featuresArtist2.put("topWord2", dna2.featureVector()[10]);
            featuresArtist2.put("topWord3", dna2.featureVector()[11]);
            featuresArtist2.put("topWord4", dna2.featureVector()[12]);
            featuresArtist2.put("topWord5", dna2.featureVector()[13]);
            // Genre One-Hot-Encoding (5 binäre Features: Rock, Pop, Schlager, Rap, Sonstige)
            featuresArtist2.put("genreRock", dna2.featureVector()[14]);
            featuresArtist2.put("genrePop", dna2.featureVector()[15]);
            featuresArtist2.put("genreSchlager", dna2.featureVector()[16]);
            featuresArtist2.put("genreRap", dna2.featureVector()[17]);
            featuresArtist2.put("genreSonstige", dna2.featureVector()[18]);
            
            // Genre-Kategorie und Name für bessere Lesbarkeit
            int genreCategory2 = 0;
            if (dna2.featureVector()[14] == 1.0) genreCategory2 = 1;
            else if (dna2.featureVector()[15] == 1.0) genreCategory2 = 2;
            else if (dna2.featureVector()[16] == 1.0) genreCategory2 = 3;
            else if (dna2.featureVector()[17] == 1.0) genreCategory2 = 4;
            else if (dna2.featureVector()[18] == 1.0) genreCategory2 = 5;
            featuresArtist2.put("genreCategory", genreCategory2);
            featuresArtist2.put("avgSentiment", dna2.averageSentiment());
            featuresArtist2.put("avgSentimentNormalized", SentimentAnalysisService.normalizeScore(dna2.averageSentiment()));
            // Genre-Name für bessere Lesbarkeit
            featuresArtist2.put("genreName", getGenreName(genreCategory2));

            result.put("featuresArtist1", featuresArtist1);
            result.put("featuresArtist2", featuresArtist2);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Comparison failed");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    /**
     * Nutzt jetzt lyricsDNAService.findSimilarArtists() statt der entfernten
     * artistStyleAnalysisService.findSimilarArtists() - liefert dadurch identische
     * Ergebnisse wie /dna/similar (einheitliche DNA-Quelle).
     */
    @GetMapping("/artist/similar")
    public ResponseEntity<Map<String, Double>> findSimilarArtists(
            @RequestParam String artistName,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            Map<String, Double> similar = lyricsDNAService.findSimilarArtists(artistName, limit);
            return ResponseEntity.ok(similar);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new LinkedHashMap<String, Double>());
        }
    }

    // ==================== LYRICS DNA ====================

    @GetMapping("/dna/all")
    public ResponseEntity<List<LyricsDNA>> getAllDNA() {
        try {
            List<LyricsDNA> dnaList = lyricsDNAService.generateAllDNAWithThemes();
            return ResponseEntity.ok(dnaList);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(List.of());
        }
    }

    @GetMapping("/dna/visualization")
    public ResponseEntity<List<Map<String, Object>>> getDNAVisualization() {
        try {
            List<Map<String, Object>> visualizationData = lyricsDNAService.getDNAVisualizationData();
            return ResponseEntity.ok(visualizationData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(List.of());
        }
    }

    @GetMapping("/dna/{artistName}")
    public ResponseEntity<Map<String, Object>> getArtistDNA(@PathVariable String artistName) {
        try {
            LyricsDNA dna = lyricsDNAService.generateDNA(artistName);

            if (dna == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("artist", dna.artistName());
            result.put("featureVector", dna.featureVector());
            result.put("averageSentiment", dna.averageSentiment());
            result.put("averageSentimentNormalized", SentimentAnalysisService.normalizeScore(dna.averageSentiment()));
            result.put("themeDistribution", dna.themeDistribution());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Failed to generate DNA");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    @GetMapping("/dna/similar")
    public ResponseEntity<Map<String, Double>> getSimilarArtists(
            @RequestParam String artistName,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            Map<String, Double> similar = lyricsDNAService.findSimilarArtists(artistName, limit);
            return ResponseEntity.ok(similar);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new LinkedHashMap<>());
        }
    }

    @GetMapping("/themes")
    public ResponseEntity<List<Theme>> getAllThemes() {
        return ResponseEntity.ok(lyricsDNAService.getAllThemes());
    }

    /**
     * Hilfsmethode: Konvertiert Genre-Kategorie in lesbaren Namen.
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