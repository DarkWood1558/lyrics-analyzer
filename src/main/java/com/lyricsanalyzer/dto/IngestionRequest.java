package com.lyricsanalyzer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record IngestionRequest(

        @NotBlank(message = "searchQuery darf nicht leer sein")
        String searchQuery,

        @Min(1) @Max(100)
        Integer limit
) {
    public int limitOrDefault() {
        return limit != null ? limit : 25;
    }
}
