package com.lyricsanalyzer.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Antwort-Wrapper für paginierte Track-Listen.
 * Enthält neben den eigentlichen Daten auch Pagination-Metadaten,
 * damit das Frontend Seitenzahlen/Navigation korrekt anzeigen kann.
 */
public record PagedTrackResponse(
        List<TrackResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PagedTrackResponse from(Page<TrackResponse> page) {
        return new PagedTrackResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
