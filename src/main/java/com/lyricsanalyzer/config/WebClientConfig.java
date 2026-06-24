package com.lyricsanalyzer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(LyricsAnalyzerProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient deezerWebClient(LyricsAnalyzerProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getDeezer().getBaseUrl())
                .build();
    }

    @Bean
    public WebClient lyricsOvhWebClient(LyricsAnalyzerProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getLyricsOvh().getBaseUrl())
                .build();
    }
}
