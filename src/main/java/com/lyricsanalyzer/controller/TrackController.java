package com.lyricsanalyzer.controller;

import com.lyricsanalyzer.dto.PagedTrackResponse;
import com.lyricsanalyzer.dto.TrackDetailResponse;
import com.lyricsanalyzer.dto.TrackResponse;
import com.lyricsanalyzer.model.LyricsStatus;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    private final TrackRepository trackRepository;

    public TrackController(TrackRepository trackRepository) {
        this.trackRepository = trackRepository;
    }

    /**
     * Paginierte, filter- und suchbare Track-Liste für die GUI.
     *
     * @param status optionaler Filter nach Lyrics-Status (PENDING/FOUND/NOT_FOUND/ERROR)
     * @param search optionale Volltextsuche über Künstler + Titel
     */
    @GetMapping
    public PagedTrackResponse listTracks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LyricsStatus status,
            @RequestParam(required = false) String search) {

        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        return PagedTrackResponse.from(
                trackRepository.search(status, normalizedSearch, PageRequest.of(page, size))
                        .map(TrackResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrackDetailResponse> getTrack(@PathVariable Long id) {
        return trackRepository.findById(id)
                .map(TrackDetailResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Anzahl Tracks pro Lyrics-Status - dient der GUI als Dashboard-Übersicht
     * (z.B. "7 Fehler" als Badge, mit direktem Link zum Retry).
     */
    @GetMapping("/status-counts")
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LyricsStatus status : LyricsStatus.values()) {
            counts.put(status.name(), trackRepository.countByLyricsStatus(status));
        }
        return counts;
    }

    /**
     * Aggregierte Auswertung: durchschnittliches Sentiment pro Genre.
     * Liefert nur aggregierte Zahlen zurück, keine vollständigen Lyrics-Texte
     * (relevant u.a. aus Urheberrechtsgründen).
     */
    @GetMapping("/stats/by-genre")
    public List<Map<String, Object>> sentimentByGenre() {
        return trackRepository.findAverageSentimentByGenre().stream()
                .map(row -> Map.<String, Object>of(
                        "genre", row.getGenre(),
                        "averageSentimentScore", row.getAvgScore(),
                        "trackCount", row.getTrackCount()))
                .collect(Collectors.toList());
    }

    /**
     * Aggregierte Auswertung: durchschnittliches Sentiment pro Erscheinungsjahr.
     */
    @GetMapping("/stats/by-year")
    public List<Map<String, Object>> sentimentByYear() {
        return trackRepository.findAverageSentimentByYear().stream()
                .map(row -> Map.<String, Object>of(
                        "year", row.getYear(),
                        "averageSentimentScore", row.getAvgScore(),
                        "trackCount", row.getTrackCount()))
                .collect(Collectors.toList());
    }
}