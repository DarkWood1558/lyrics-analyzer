package com.lyricsanalyzer.controller;

import com.lyricsanalyzer.dto.IngestionRequest;
import com.lyricsanalyzer.service.LyricsIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stößt die Ingestion-Pipeline an: Songs über Deezer suchen,
 * Lyrics bei lyrics.ovh nachladen (nur für Songs, die noch nicht in der DB liegen)
 * und dauerhaft speichern.
 */
@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final LyricsIngestionService ingestionService;

    public IngestionController(LyricsIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<LyricsIngestionService.IngestionSummary> ingest(
            @Valid @RequestBody IngestionRequest request) {

        LyricsIngestionService.IngestionSummary summary =
                ingestionService.ingestBySearchQuery(request.searchQuery(), request.limitOrDefault());

        return ResponseEntity.ok(summary);
    }

    /**
     * Versucht den Lyrics-Abruf für Tracks mit Status ERROR erneut
     * (z.B. nach einem vorübergehenden Netzwerkproblem bei lyrics.ovh).
     */
    @PostMapping("/retry-errors")
    public ResponseEntity<LyricsIngestionService.RetrySummary> retryErrors(
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(ingestionService.retryErrorTracks(limit));
    }

    /**
     * Lädt Genre + Erscheinungsjahr für bereits vorhandene Tracks nach, die vor Einführung
     * der Metadaten-Anreicherung angelegt wurden (und daher noch keine bzw. unvollständige
     * Genre-/Jahr-Daten haben). Macht die Statistik-Auswertungen "Sentiment pro Genre/Jahr"
     * auch für Altdaten aussagekräftig (siehe README).
     */
    @PostMapping("/backfill-metadata")
    public ResponseEntity<LyricsIngestionService.MetadataBackfillSummary> backfillMetadata(
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(ingestionService.backfillMetadata(limit));
    }
}
