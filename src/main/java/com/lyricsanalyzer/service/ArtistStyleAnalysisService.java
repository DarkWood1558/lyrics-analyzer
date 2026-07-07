package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.ArtistStyleFeatures;
import com.lyricsanalyzer.model.LyricsDNA;
import com.lyricsanalyzer.model.Track;
import com.lyricsanalyzer.repository.TrackRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service für die Analyse von Künstler-Stilen.
 *
 * <h2>Verantwortungsabgrenzung zu LyricsDNAService</h2>
 * Früher berechnete dieser Service zusätzlich vollständige {@link LyricsDNA}-Objekte
 * (generateLyricsDNA, generateDNAForAllArtists, findSimilarArtists) parallel zu
 * {@link LyricsDNAService} - mit fast identischem Feature-Vector-Code, aber abweichendem
 * Verhalten: hier war {@code themeDistribution} immer leer (Map.of()), während
 * LyricsDNAService sie korrekt über den ThemeClassificationService befüllte. Zudem nutzte
 * der Endpunkt {@code /dna/similar} (LyricsDNAService.findSimilarArtists) intern wiederum
 * DIESEN Service, wodurch er andere (themen-lose) DNA-Daten lieferte als {@code /dna/all}
 * und {@code /dna/visualization}.
 * <p>
 * Diese Klasse ist daher jetzt bewusst auf reine Stil-Feature-Extraktion (ohne Themen,
 * ohne vollständige DNA) plus die Cosine-Similarity-Berechnung als gemeinsam genutzte
 * Utility-Funktion reduziert. Die vollständige DNA-Erzeugung (Feature-Vektor + Themen-
 * verteilung) liegt jetzt ausschließlich in {@link LyricsDNAService} - es gibt nur noch
 * EINE Quelle der Wahrheit für "Lyrics DNA".
 */
@Service
public class ArtistStyleAnalysisService {

    private final FeatureExtractionService featureExtractionService;
    private final TrackRepository trackRepository;

    public ArtistStyleAnalysisService(FeatureExtractionService featureExtractionService,
                                      TrackRepository trackRepository) {
        this.featureExtractionService = featureExtractionService;
        this.trackRepository = trackRepository;
    }

    /**
     * Analysiert den Stil eines einzelnen Songtextes.
     */
    public ArtistStyleFeatures analyzeStyle(String lyrics) {
        if (lyrics == null || lyrics.isBlank()) {
            return new ArtistStyleFeatures(0, 0, 0, null, Map.of(), 0, 0, 0, 0, 0);
        }
        return featureExtractionService.extractStyleFeatures(lyrics);
    }

    /**
     * Analysiert den Stil für einen bestimmten Künstler (Aggregation aller seiner Tracks).
     * Liefert reine Stil-Features ohne Sentiment/Themen - genutzt vom
     * GET /api/analysis/artist/style/{artistName}-Endpunkt.
     */
    public ArtistStyleFeatures analyzeStyleForArtist(String artistName) {
        List<Track> tracks = trackRepository.findByArtistNameIgnoreCase(artistName);

        List<String> lyricsList = tracks.stream()
                .map(Track::getLyrics)
                .filter(Objects::nonNull)
                .filter(l -> !l.isBlank())
                .toList();

        return featureExtractionService.extractStyleFeaturesForArtist(lyricsList);
    }

    /**
     * Berechnet die Ähnlichkeit zwischen zwei Lyrics-DNAs (Cosine Similarity) anhand ihrer
     * Feature-Vektoren. Reine, zustandslose Vektor-Mathematik - bewusst hier als Utility
     * gehalten (statt in LyricsDNAService dupliziert), da Ähnlichkeitsberechnung
     * unabhängig davon ist, WIE die DNA erzeugt wurde.
     */
    public double calculateSimilarity(LyricsDNA dna1, LyricsDNA dna2) {
        if (dna1 == null || dna2 == null) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;

        double[] vec1 = dna1.featureVector();
        double[] vec2 = dna2.featureVector();

        int minLength = Math.min(vec1.length, vec2.length);

        for (int i = 0; i < minLength; i++) {
            dotProduct += vec1[i] * vec2[i];
            magnitude1 += Math.pow(vec1[i], 2);
            magnitude2 += Math.pow(vec2[i], 2);
        }

        if (magnitude1 == 0 || magnitude2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(magnitude1) * Math.sqrt(magnitude2));
    }
}