package com.lyricsanalyzer;

import com.lyricsanalyzer.config.LyricsAnalyzerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LyricsAnalyzerProperties.class)
public class LyricsAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LyricsAnalyzerApplication.class, args);
    }
}
