package com.lyricsanalyzer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "track")
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deezer_id", unique = true)
    private Long deezerId;

    @Column(name = "deezer_album_id")
    private Long deezerAlbumId;

    @Column(name = "artist_name", nullable = false, columnDefinition = "VARCHAR(255)")
    private String artistName;

    @Column(name = "title", nullable = false, columnDefinition = "VARCHAR(500)")
    private String title;

    @Column(name = "album_name", columnDefinition = "VARCHAR(500)")
    private String albumName;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", columnDefinition = "VARCHAR(20)")
    private Theme theme;

    @Column(name = "genre", columnDefinition = "VARCHAR(255)")
    private String genre;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "lyrics", columnDefinition = "TEXT")
    private String lyrics;

    @Enumerated(EnumType.STRING)
    @Column(name = "lyrics_status", nullable = false, columnDefinition = "VARCHAR(20)")
    private LyricsStatus lyricsStatus = LyricsStatus.PENDING;

    @Column(name = "lyrics_fetched_at")
    private LocalDateTime lyricsFetchedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment_label", columnDefinition = "VARCHAR(20)")
    private SentimentLabel sentimentLabel;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Column(name = "sentiment_analyzed_at")
    private LocalDateTime sentimentAnalyzedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Track() {
        // für JPA
    }

    public Track(String artistName, String title) {
        this.artistName = artistName;
        this.title = title;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getter & Setter ---

    public Long getId() {
        return id;
    }

    public Long getDeezerId() {
        return deezerId;
    }

    public void setDeezerId(Long deezerId) {
        this.deezerId = deezerId;
    }

    public Long getDeezerAlbumId() {
        return deezerAlbumId;
    }

    public void setDeezerAlbumId(Long deezerAlbumId) {
        this.deezerAlbumId = deezerAlbumId;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public LyricsStatus getLyricsStatus() {
        return lyricsStatus;
    }

    public void setLyricsStatus(LyricsStatus lyricsStatus) {
        this.lyricsStatus = lyricsStatus;
    }

    public LocalDateTime getLyricsFetchedAt() {
        return lyricsFetchedAt;
    }

    public void setLyricsFetchedAt(LocalDateTime lyricsFetchedAt) {
        this.lyricsFetchedAt = lyricsFetchedAt;
    }

    public SentimentLabel getSentimentLabel() {
        return sentimentLabel;
    }

    public void setSentimentLabel(SentimentLabel sentimentLabel) {
        this.sentimentLabel = sentimentLabel;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(Double sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public LocalDateTime getSentimentAnalyzedAt() {
        return sentimentAnalyzedAt;
    }

    public void setSentimentAnalyzedAt(LocalDateTime sentimentAnalyzedAt) {
        this.sentimentAnalyzedAt = sentimentAnalyzedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
