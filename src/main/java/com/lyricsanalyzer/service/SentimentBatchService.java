package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wendet die Sentiment-Analyse auf gespeicherte Lyrics an und persistiert
 * das Ergebnis im Track, damit die Analyse nicht bei jeder Abfrage neu berechnet wird.
 */
@Service
public class SentimentBatchService {

    private static final Logger log = LoggerFactory.getLogger(SentimentBatchService.class);

    private final LyricsIngestionService ingestionService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final TrackRepository trackRepository;

    public SentimentBatchService(LyricsIngestionService ingestionService,
                                  SentimentAnalysisService sentimentAnalysisService,
                                  TrackRepository trackRepository) {
        this.ingestionService = ingestionService;
        this.sentimentAnalysisService = sentimentAnalysisService;
        this.trackRepository = trackRepository;
    }

    /**
     * Analysiert bis zu {@code limit} Tracks, die Lyrics aber noch kein Sentiment haben.
     *
     * @return Anzahl der tatsächlich analysierten Tracks
     */
    public int analyzePendingTracks(int limit) {
        List<Track> pending = ingestionService.findTracksPendingSentimentAnalysis(limit);

        for (Track track : pending) {
            SentimentAnalysisService.SentimentResult result =
                    sentimentAnalysisService.analyze(track.getLyrics());

            track.setSentimentLabel(result.label());
            track.setSentimentScore(result.score());
            track.setSentimentAnalyzedAt(LocalDateTime.now());
            trackRepository.save(track);
        }

        log.info("Sentiment-Analyse abgeschlossen für {} Tracks", pending.size());
        return pending.size();
    }
}
