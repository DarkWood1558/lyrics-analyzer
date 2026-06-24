package com.lyricsanalyzer.config;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class NlpConfig {

    /**
     * Die CoreNLP-Pipeline ist teuer zu initialisieren (lädt Modelle von der Klassenpfad-JAR),
     * deshalb wird sie als Singleton-Bean genau einmal beim Start erzeugt
     * und danach für alle Analysen wiederverwendet.
     *
     * Annotatoren:
     * - tokenize, ssplit: Text in Sätze/Wörter zerlegen
     * - pos, parse: für den Parse-Baum, den das Sentiment-Modell benötigt
     * - sentiment: das fertige, vortrainierte Sentiment-Modell aus der Bibliothek
     *
     * WICHTIG für Songtexte: lyrics.ovh liefert Lyrics ohne Zeilenumbrüche, nur durch
     * Leerzeichen getrennt. Ohne Satzzeichen/Zeilenumbrüche erkennt ssplit oft den
     * gesamten Songtext als EINEN riesigen "Satz". Der Parser (parse) ist statistisch und
     * sein Speicherbedarf wächst etwa quadratisch bis kubisch mit der Satzlänge - bei
     * 300+ Wörtern in einem "Satz" kann das den Heap sprengen ("out of memory" im Log).
     * parse.maxlen lässt solche überlangen Sätze einfach überspringen, statt zu crashen;
     * sie bekommen dann kein Sentiment-Tree und werden in der Aggregation ignoriert
     * (siehe SentimentAnalysisService - sentimentTree == null wird übersprungen).
     */
    @Bean
    public StanfordCoreNLP sentimentPipeline() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,parse,sentiment");
        props.setProperty("parse.maxlen", "70");
        return new StanfordCoreNLP(props);
    }
}
