package com.lyricsanalyzer.controller;

import com.lyricsanalyzer.service.SentimentBatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Stößt die Sentiment-Analyse für Tracks an, die bereits Lyrics haben,
 * aber noch nicht analysiert wurden. Ergebnis wird dauerhaft am Track gespeichert.
 */
@RestController
@RequestMapping("/api/sentiment")
public class SentimentController {

    private final SentimentBatchService sentimentBatchService;

    public SentimentController(SentimentBatchService sentimentBatchService) {
        this.sentimentBatchService = sentimentBatchService;
    }

    @PostMapping("/analyze-pending")
    public ResponseEntity<Map<String, Integer>> analyzePending(
            @RequestParam(defaultValue = "50") int limit) {

        int analyzed = sentimentBatchService.analyzePendingTracks(limit);
        return ResponseEntity.ok(Map.of("analyzedCount", analyzed));
    }
}
