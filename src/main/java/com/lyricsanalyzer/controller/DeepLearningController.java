package com.lyricsanalyzer.controller;

import com.lyricsanalyzer.model.Theme;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import com.lyricsanalyzer.service.DeepLearningThemeService;
import com.lyricsanalyzer.service.ThemeClassificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-API für das Deep-Learning-Themenklassifikationsmodell.
 *
 * Endpunkte:
 * <ul>
 *   <li>{@code POST /api/analysis/dl/train}        — Modell trainieren</li>
 *   <li>{@code GET  /api/analysis/dl/status}        — Modell-Status abfragen</li>
 *   <li>{@code GET  /api/analysis/dl/classify}      — Einzelnen Track klassifizieren</li>
 *   <li>{@code POST /api/analysis/dl/classify-all}  — Alle unlabelten Tracks klassifizieren</li>
 *   <li>{@code GET  /api/analysis/dl/compare}       — DL- vs. Weka-Vergleich für einen Track</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/analysis/dl")
public class DeepLearningController {

    private final DeepLearningThemeService dlService;
    private final TrackRepository trackRepository;
    private final ThemeClassificationService wekaService;

    public DeepLearningController(DeepLearningThemeService dlService,
                                  TrackRepository trackRepository,
                                  ThemeClassificationService wekaService) {
        this.dlService = dlService;
        this.trackRepository = trackRepository;
        this.wekaService = wekaService;
    }

    /**
     * Startet das Training des Deep-Learning-Modells.
     * Nutzt alle Tracks mit gesetztem Theme-Label als Trainings-Grundlage.
     * Das Training läuft synchron (blockiert den Request-Thread ca. 10-60s).
     */
    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> train() {
        if (dlService.isCurrentlyTraining()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Training läuft bereits. Bitte warten.");
            return ResponseEntity.status(409).body(body);
        }

        List<Track> labeledTracks = trackRepository.findAll().stream()
                .filter(t -> t.getTheme() != null)
                .filter(t -> t.getLyrics() != null && !t.getLyrics().isBlank())
                .toList();

        if (labeledTracks.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Keine gelabelten Tracks mit Lyrics gefunden.");
            body.put("hint", "Bitte zuerst Tracks mit Theme-Labels versehen (Tracks-Tab, Theme-Dropdown).");
            return ResponseEntity.badRequest().body(body);
        }

        try {
            DeepLearningThemeService.TrainingSummary summary = dlService.train(labeledTracks);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "DL-Modell erfolgreich trainiert");
            result.put("trainingSamples", summary.trainingSamples());
            result.put("vocabSize", summary.vocabSize());
            result.put("epochs", summary.epochs());
            result.put("trainAccuracy", String.format("%.1f%%", summary.trainAccuracy() * 100));
            result.put("trainAccuracyRaw", summary.trainAccuracy());
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Training fehlgeschlagen: " + e.getMessage());
            return ResponseEntity.internalServerError().body(body);
        }
    }

    /**
     * Gibt den aktuellen Status des DL-Modells zurück.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trained", dlService.isTrained());
        result.put("training", dlService.isCurrentlyTraining());
        result.put("trainingSamples", dlService.getTrainingSamples());
        result.put("vocabSize", dlService.getVocabSize());
        return ResponseEntity.ok(result);
    }

    /**
     * Klassifiziert das Theme eines Tracks per DL-Modell.
     * Gibt zusätzlich den Konfidenz-Score und die Wahrscheinlichkeitsverteilung zurück.
     */
    @GetMapping("/classify")
    public ResponseEntity<Map<String, Object>> classify(
            @RequestParam String artist,
            @RequestParam String title) {

        Track track = trackRepository
                .findByArtistNameIgnoreCaseAndTitleIgnoreCase(artist, title)
                .orElse(null);

        if (track == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Track nicht gefunden: " + artist + " – " + title);
            return ResponseEntity.notFound().build();
        }

        if (track.getLyrics() == null || track.getLyrics().isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Keine Lyrics für diesen Track verfügbar.");
            return ResponseEntity.badRequest().body(body);
        }

        if (!dlService.isTrained()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "DL-Modell noch nicht trainiert. Bitte zuerst /api/analysis/dl/train aufrufen.");
            return ResponseEntity.badRequest().body(body);
        }

        try {
            Theme predicted = dlService.classify(track.getLyrics());
            Map<Theme, Integer> distribution = dlService.getThemeDistribution(track.getLyrics());
            double confidence = dlService.getConfidence(track.getLyrics());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("trackId", track.getId());
            result.put("artist", track.getArtistName());
            result.put("title", track.getTitle());
            result.put("predictedTheme", predicted != null ? predicted.name() : null);
            result.put("confidence", String.format("%.1f%%", confidence * 100));
            result.put("confidenceRaw", confidence);
            result.put("themeDistribution", distribution);
            result.put("existingThemeLabel", track.getTheme() != null ? track.getTheme().name() : null);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Klassifikation fehlgeschlagen: " + e.getMessage());
            return ResponseEntity.internalServerError().body(body);
        }
    }

    /**
     * Klassifiziert alle Tracks ohne Theme-Label mit dem DL-Modell
     * und speichert das Ergebnis in der Datenbank.
     */
    @PostMapping("/classify-all")
    public ResponseEntity<Map<String, Object>> classifyAll() {
        if (!dlService.isTrained()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "DL-Modell noch nicht trainiert. Bitte zuerst /api/analysis/dl/train aufrufen.");
            return ResponseEntity.badRequest().body(body);
        }

        List<Track> unlabeled = trackRepository.findAll().stream()
                .filter(t -> t.getTheme() == null)
                .filter(t -> t.getLyrics() != null && !t.getLyrics().isBlank())
                .toList();

        int classified = 0;
        int errors = 0;

        for (Track track : unlabeled) {
            try {
                Theme theme = dlService.classify(track.getLyrics());
                track.setTheme(theme);
                trackRepository.save(track);
                classified++;
            } catch (Exception e) {
                errors++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "DL-Klassifikation abgeschlossen");
        result.put("classifiedTracks", classified);
        result.put("errors", errors);
        result.put("skippedTracks", trackRepository.findAll().size() - classified - errors - unlabeled.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Vergleicht die Vorhersagen von Weka- und DL-Modell für einen Track.
     * Nützlich zum Beurteilen, wo die Modelle übereinstimmen oder abweichen.
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareWithWeka(
            @RequestParam String artist,
            @RequestParam String title) {

        Track track = trackRepository
                .findByArtistNameIgnoreCaseAndTitleIgnoreCase(artist, title)
                .orElse(null);

        if (track == null || track.getLyrics() == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trackId", track.getId());
        result.put("artist", track.getArtistName());
        result.put("title", track.getTitle());
        result.put("manualLabel", track.getTheme() != null ? track.getTheme().name() : null);

        // DL-Vorhersage
        if (dlService.isTrained()) {
            try {
                Theme dlPredicted = dlService.classify(track.getLyrics());
                double dlConfidence = dlService.getConfidence(track.getLyrics());
                result.put("dlPredicted", dlPredicted != null ? dlPredicted.name() : null);
                result.put("dlConfidence", String.format("%.1f%%", dlConfidence * 100));
                result.put("dlDistribution", dlService.getThemeDistribution(track.getLyrics()));
            } catch (Exception e) {
                result.put("dlError", e.getMessage());
            }
        } else {
            result.put("dlStatus", "Nicht trainiert");
        }

        // Weka-Vorhersage
        if (wekaService != null && wekaService.isTrained()) {
            try {
                Theme wekaPredicted = wekaService.classifyTheme(track.getLyrics());
                result.put("wekaPredicted", wekaPredicted != null ? wekaPredicted.name() : null);
                result.put("wekaDistribution", wekaService.getThemeDistribution(track.getLyrics()));
            } catch (Exception e) {
                result.put("wekaError", e.getMessage());
            }
        } else {
            result.put("wekaStatus", "Nicht trainiert");
        }

        return ResponseEntity.ok(result);
    }
}