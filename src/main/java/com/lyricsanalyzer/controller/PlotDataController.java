package com.lyricsanalyzer.controller;

import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import com.lyricsanalyzer.service.FeatureExtractionService;
import com.lyricsanalyzer.service.SentimentAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Liefert aggregierte Feature-Daten für die Plotly.js-Visualisierungen im Frontend.
 *
 * Zwei Datenebenen:
 * - /plot-data/tracks   – Track-Level (ein Datenpunkt pro Track mit Lyrics + Sentiment)
 * - /plot-data/artists  – Künstler-Level (aggregiert über alle Tracks eines Künstlers)
 *
 * Alle numerischen Werte sind bereits so aufbereitet, dass Plotly sie direkt verwenden kann.
 */
@RestController
@RequestMapping("/api/plot-data")
public class PlotDataController {

    private final TrackRepository trackRepository;
    private final FeatureExtractionService featureExtractionService;

    public PlotDataController(TrackRepository trackRepository,
                              FeatureExtractionService featureExtractionService) {
        this.trackRepository = trackRepository;
        this.featureExtractionService = featureExtractionService;
    }

    /**
     * Track-Level-Daten für Scatter-Matrix, Violin-Plots und Parallel-Coordinates.
     * Enthält alle Tracks mit Lyrics und berechnet Style-Features on-the-fly.
     *
     * Felder pro Track:
     *   id, artist, title, genre, theme, sentimentLabel, sentimentScore (normalisiert 0-100)
     *   avgWordLength, rhymeDensity, uniqueWordRatio,
     *   avgLineLength, exclamationDensity, questionMarkDensity,
     *   capitalWordRatio, lineCount
     */
    @GetMapping("/tracks")
    public ResponseEntity<List<Map<String, Object>>> getTrackPlotData() {
        List<Track> tracks = trackRepository.findAll().stream()
                .filter(t -> t.getLyrics() != null && !t.getLyrics().isBlank())
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();

        for (Track track : tracks) {
            try {
                var features = featureExtractionService.extractStyleFeatures(track.getLyrics());

                Map<String, Object> point = new LinkedHashMap<>();
                point.put("id", track.getId());
                point.put("artist", track.getArtistName());
                point.put("title", track.getTitle());
                point.put("genre", track.getGenre() != null ? track.getGenre() : "Unbekannt");
                point.put("theme", track.getTheme() != null ? track.getTheme().name() : "Ohne Label");
                point.put("sentimentLabel", track.getSentimentLabel() != null
                        ? track.getSentimentLabel().name() : null);
                point.put("sentimentScore", track.getSentimentScore() != null
                        ? SentimentAnalysisService.normalizeScore(track.getSentimentScore()) : null);

                // Style-Features
                point.put("avgWordLength",        round(features.avgWordLength(), 3));
                point.put("rhymeDensity",          round(features.rhymeDensity(), 3));
                point.put("uniqueWordRatio",       round(features.uniqueWordRatio(), 3));
                point.put("avgLineLength",         round(features.avgLineLength(), 3));
                point.put("exclamationDensity",    round(features.exclamationDensity(), 3));
                point.put("questionMarkDensity",   round(features.questionMarkDensity(), 3));
                point.put("capitalWordRatio",      round(features.capitalWordRatio(), 3));
                point.put("lineCount",             features.lineCount());

                result.add(point);
            } catch (Exception e) {
                // Einzelner Track schlägt fehl → überspringen, Rest weiter verarbeiten
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Aggregierte Künstler-Daten für den Parallel-Coordinates-Plot und Artist-SPLOM.
     * Pro Künstler ein Datenpunkt mit Durchschnittswerten über alle seine Tracks.
     */
    @GetMapping("/artists")
    public ResponseEntity<List<Map<String, Object>>> getArtistPlotData() {
        List<Track> all = trackRepository.findAll();

        Map<String, List<Track>> byArtist = all.stream()
                .filter(t -> t.getArtistName() != null)
                .collect(Collectors.groupingBy(Track::getArtistName));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<String, List<Track>> entry : byArtist.entrySet()) {
            String artist = entry.getKey();
            List<Track> tracks = entry.getValue();

            // Nur Tracks mit Lyrics für Feature-Berechnung
            List<Track> withLyrics = tracks.stream()
                    .filter(t -> t.getLyrics() != null && !t.getLyrics().isBlank())
                    .collect(Collectors.toList());

            if (withLyrics.isEmpty()) continue;

            // Stil-Features aggregieren
            double avgWordLength = 0, rhymeDensity = 0, uniqueWordRatio = 0;
            double avgLineLength = 0, exclamationDensity = 0, questionMarkDensity = 0;
            double capitalWordRatio = 0;
            int lineCount = 0;
            int n = 0;

            for (Track t : withLyrics) {
                try {
                    var f = featureExtractionService.extractStyleFeatures(t.getLyrics());
                    avgWordLength      += f.avgWordLength();
                    rhymeDensity       += f.rhymeDensity();
                    uniqueWordRatio    += f.uniqueWordRatio();
                    avgLineLength      += f.avgLineLength();
                    exclamationDensity += f.exclamationDensity();
                    questionMarkDensity += f.questionMarkDensity();
                    capitalWordRatio   += f.capitalWordRatio();
                    lineCount          += f.lineCount();
                    n++;
                } catch (Exception ignored) {}
            }

            if (n == 0) continue;

            // Durchschnittliches Sentiment
            OptionalDouble avgSentimentOpt = tracks.stream()
                    .filter(t -> t.getSentimentScore() != null)
                    .mapToDouble(t -> SentimentAnalysisService.normalizeScore(t.getSentimentScore()))
                    .average();

            // Hauptgenre (häufigstes unter allen Tracks des Künstlers)
            String mainGenre = tracks.stream()
                    .filter(t -> t.getGenre() != null)
                    .collect(Collectors.groupingBy(Track::getGenre, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Unbekannt");

            // Häufigstes Theme
            String mainTheme = withLyrics.stream()
                    .filter(t -> t.getTheme() != null)
                    .collect(Collectors.groupingBy(t -> t.getTheme().name(), Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Ohne Label");

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("artist",              artist);
            point.put("trackCount",          tracks.size());
            point.put("tracksWithLyrics",    n);
            point.put("genre",               mainGenre);
            point.put("theme",               mainTheme);
            point.put("sentimentScore",      avgSentimentOpt.isPresent()
                    ? round(avgSentimentOpt.getAsDouble(), 2) : null);

            point.put("avgWordLength",       round(avgWordLength / n, 3));
            point.put("rhymeDensity",        round(rhymeDensity / n, 3));
            point.put("uniqueWordRatio",     round(uniqueWordRatio / n, 3));
            point.put("avgLineLength",       round(avgLineLength / n, 3));
            point.put("exclamationDensity",  round(exclamationDensity / n, 3));
            point.put("questionMarkDensity", round(questionMarkDensity / n, 3));
            point.put("capitalWordRatio",    round(capitalWordRatio / n, 3));
            point.put("avgLineCount",        round((double) lineCount / n, 1));

            result.add(point);
        }

        // Nach Trackanzahl sortieren (interessanteste Künstler zuerst)
        result.sort((a, b) -> Integer.compare(
                (Integer) b.get("tracksWithLyrics"),
                (Integer) a.get("tracksWithLyrics")));

        return ResponseEntity.ok(result);
    }

    /**
     * Genre-Sentiment-Verteilung für Bar-/Box-Charts.
     * Liefert alle Tracks mit Sentiment und Genre, für client-seitige Aggregation.
     */
    @GetMapping("/sentiment-by-genre")
    public ResponseEntity<List<Map<String, Object>>> getSentimentByGenre() {
        List<Map<String, Object>> result = trackRepository.findAll().stream()
                .filter(t -> t.getSentimentScore() != null && t.getGenre() != null)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("genre", t.getGenre());
                    m.put("sentimentScore", round(
                            SentimentAnalysisService.normalizeScore(t.getSentimentScore()), 2));
                    m.put("sentimentLabel", t.getSentimentLabel() != null
                            ? t.getSentimentLabel().name() : null);
                    m.put("artist", t.getArtistName());
                    m.put("title", t.getTitle());
                    m.put("releaseYear", t.getReleaseYear());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Theme-Feature-Daten: Tracks mit Theme-Label und Style-Features.
     * Für Theme-Scatter und Theme-Violin-Plots.
     */
    @GetMapping("/theme-features")
    public ResponseEntity<List<Map<String, Object>>> getThemeFeatures() {
        List<Map<String, Object>> result = new ArrayList<>();

        trackRepository.findAll().stream()
                .filter(t -> t.getTheme() != null
                        && t.getLyrics() != null
                        && !t.getLyrics().isBlank())
                .forEach(t -> {
                    try {
                        var f = featureExtractionService.extractStyleFeatures(t.getLyrics());
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("theme",              t.getTheme().name());
                        m.put("artist",             t.getArtistName());
                        m.put("title",              t.getTitle());
                        m.put("sentimentScore",     t.getSentimentScore() != null
                                ? round(SentimentAnalysisService.normalizeScore(t.getSentimentScore()), 2) : null);
                        m.put("avgWordLength",      round(f.avgWordLength(), 3));
                        m.put("rhymeDensity",       round(f.rhymeDensity(), 3));
                        m.put("uniqueWordRatio",    round(f.uniqueWordRatio(), 3));
                        m.put("avgLineLength",      round(f.avgLineLength(), 3));
                        m.put("exclamationDensity", round(f.exclamationDensity(), 3));
                        m.put("capitalWordRatio",   round(f.capitalWordRatio(), 3));
                        result.add(m);
                    } catch (Exception ignored) {}
                });

        return ResponseEntity.ok(result);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
