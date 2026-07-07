package com.lyricsanalyzer.service;

import com.lyricsanalyzer.model.SentimentLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Führt die Sentiment-Analyse auf Songtexten aus.
 * Nutzt ausschließlich das vortrainierte Sentiment-Modell aus Stanford CoreNLP -
 * es wird kein eigenes Modell trainiert oder implementiert.
 *
 * CoreNLP klassifiziert pro Satz auf einer 5-stufigen Skala (0-4):
 * 0 = very negative, 1 = negative, 2 = neutral, 3 = positive, 4 = very positive
 * Wir aggregieren über alle "Sätze" (= Zeilen/Strophen) eines Songtexts hinweg,
 * indem wir den Durchschnitt der numerischen Werte bilden.
 */
@Service
public class SentimentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SentimentAnalysisService.class);

    private final StanfordCoreNLP sentimentPipeline;

    public SentimentAnalysisService(StanfordCoreNLP sentimentPipeline) {
        this.sentimentPipeline = sentimentPipeline;
    }

    public record SentimentResult(SentimentLabel label, double score) {
    }

    /**
     * Analysiert einen vollständigen Songtext und liefert ein aggregiertes Sentiment.
     *
     * @param lyrics der vollständige, bereits gespeicherte Songtext
     * @return aggregiertes Sentiment-Label + numerischer Score (0.0 - 4.0)
     */
    public SentimentResult analyze(String lyrics) {
        if (lyrics == null || lyrics.isBlank()) {
            return new SentimentResult(SentimentLabel.NEUTRAL, 2.0);
        }

        String preprocessed = preprocessForSentenceSplitting(lyrics);

        Annotation annotation = new Annotation(preprocessed);
        sentimentPipeline.annotate(annotation);

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

        if (sentences == null || sentences.isEmpty()) {
            return new SentimentResult(SentimentLabel.NEUTRAL, 2.0);
        }

        double sum = 0.0;
        int count = 0;
        int skippedTooLong = 0;

        for (CoreMap sentence : sentences) {
            Tree sentimentTree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
            if (sentimentTree == null) {
                // Tritt u.a. ein, wenn parse.maxlen überschritten wurde (siehe NlpConfig) -
                // der Parser hat diesen "Satz" bewusst übersprungen, statt OOM zu riskieren.
                skippedTooLong++;
                continue;
            }
            // predictedClass liefert 0-4, siehe Skala oben
            int predictedClass = RNNCoreAnnotations.getPredictedClass(sentimentTree);
            sum += predictedClass;
            count++;
        }

        if (skippedTooLong > 0) {
            log.debug("{} Segment(e) beim Sentiment-Parsing übersprungen (zu lang)", skippedTooLong);
        }

        if (count == 0) {
            return new SentimentResult(SentimentLabel.NEUTRAL, 2.0);
        }

        double averageScore = sum / count;
        SentimentLabel label = toLabel(averageScore);

        return new SentimentResult(label, averageScore);
    }

    /**
     * lyrics.ovh liefert Songtexte ohne Zeilenumbrüche - der ganze Text ist meist
     * eine durchgehende Wortkette, nur durch Kommas/Klammern unterbrochen. Ohne echte
     * Satzgrenzen erkennt CoreNLPs ssplit-Annotator oft den GESAMTEN Songtext als einen
     * einzigen, extrem langen "Satz". Das lässt den statistischen Parser (parse-Annotator)
     * im Speicherbedarf explodieren (sichtbar als "out of memory"-Warnung im Log).
     * <p>
     * Um das von vornherein zu vermeiden, fügen wir nach jedem Komma sowie vor typischen
     * Songtext-Strukturmarkierungen (Klammern für Background-Vocals etc.) einen Zeilenumbruch
     * ein. Das ist eine Heuristik, keine exakte Satzgrenzen-Erkennung - reicht aber aus, damit
     * ssplit den Text in kurze, handhabbare Segmente zerlegt. parse.maxlen bleibt zusätzlich
     * als Sicherheitsnetz für die seltenen Fälle, in denen ein Segment trotzdem noch lang ist.
     */
    private String preprocessForSentenceSplitting(String lyrics) {
        return lyrics
                .replace(",", ",\n")
                .replace(")", ")\n")
                .replaceAll("\\s+\n", "\n")
                .replaceAll("\n{2,}", "\n");
    }

    private SentimentLabel toLabel(double averageScore) {
        // Rundung auf die nächste der 5 Klassen
        int rounded = (int) Math.round(averageScore);
        return switch (rounded) {
            case 0 -> SentimentLabel.VERY_NEGATIVE;
            case 1 -> SentimentLabel.NEGATIVE;
            case 2 -> SentimentLabel.NEUTRAL;
            case 3 -> SentimentLabel.POSITIVE;
            default -> SentimentLabel.VERY_POSITIVE;
        };
    }

    /**
     * Skaliert den internen Sentiment-Score (0-4) auf eine leserfreundliche
     * 0-100 Skala für die Darstellung in der GUI.
     * 
     * Mapping:
     * - 0.0 (very negative) -> 0
     * - 2.0 (neutral) -> 50
     * - 4.0 (very positive) -> 100
     * 
     * @param rawScore der Rohwert aus CoreNLP (0.0 - 4.0)
     * @return normalisierter Score (0 - 100)
     */
    public static double normalizeScore(double rawScore) {
        // Lineare Transformation: [0, 4] -> [0, 100]
        return rawScore * 25.0;
    }

    /**
     * Konvertiert einen normalisierten Score (0-100) zurück in den Rohwert (0-4).
     * Nützlich falls wir den Rohwert aus dem normalisierten berechnen müssen.
     * 
     * @param normalizedScore der normalisierte Score (0 - 100)
     * @return Rohwert (0.0 - 4.0)
     */
    public static double denormalizeScore(double normalizedScore) {
        return normalizedScore / 25.0;
    }
}
