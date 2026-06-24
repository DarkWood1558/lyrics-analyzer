package com.lyricsanalyzer.client;

/**
 * Ergebnis der Metadaten-Anreicherung über die Deezer Album-API.
 * genre und releaseYear sind beide nullable: Deezer liefert nicht für jedes Album
 * eine genre_id, und das release_date kann fehlen oder unparsbar sein.
 */
public record DeezerAlbumMetadata(String genre, Integer releaseYear) {

    public static final DeezerAlbumMetadata EMPTY = new DeezerAlbumMetadata(null, null);
}
