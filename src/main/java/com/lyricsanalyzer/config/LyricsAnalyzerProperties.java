package com.lyricsanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lyrics-analyzer")
public class LyricsAnalyzerProperties {

    private Deezer deezer = new Deezer();
    private LyricsOvh lyricsOvh = new LyricsOvh();
    private Ingestion ingestion = new Ingestion();

    public Deezer getDeezer() {
        return deezer;
    }

    public LyricsOvh getLyricsOvh() {
        return lyricsOvh;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public static class Deezer {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class LyricsOvh {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Ingestion {
        private long requestDelayMs = 300;

        public long getRequestDelayMs() {
            return requestDelayMs;
        }

        public void setRequestDelayMs(long requestDelayMs) {
            this.requestDelayMs = requestDelayMs;
        }
    }
}
