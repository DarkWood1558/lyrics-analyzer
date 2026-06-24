package com.lyricsanalyzer.model;

/**
 * Status des Lyrics-Abrufs für einen Track.
 * Verhindert, dass derselbe Song mehrfach unnötig bei lyrics.ovh angefragt wird.
 */
public enum LyricsStatus {
    PENDING,    // noch nicht versucht
    FOUND,      // Lyrics erfolgreich gefunden und gespeichert
    NOT_FOUND,  // API hat 404 zurückgegeben, Song hat keine Lyrics in den Quellen
    ERROR       // technischer Fehler beim Abruf, kann später erneut versucht werden
}
