-- Consolidated initial schema for lyrics-analyzer
-- Combines V1-V5: create table, add columns, and ensure correct types

CREATE TABLE track (
    id                  BIGSERIAL PRIMARY KEY,
    deezer_id           BIGINT UNIQUE,
    artist_name         VARCHAR(255) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    album_name          VARCHAR(500),
    genre               VARCHAR(255),
    release_year        INTEGER,
    deezer_album_id     BIGINT,
    theme               VARCHAR(20),
    dl_theme            VARCHAR(20),
    dl_confidence       DOUBLE PRECISION,

    lyrics              TEXT,
    lyrics_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    lyrics_fetched_at   TIMESTAMP,

    sentiment_label     VARCHAR(20),
    sentiment_score     DOUBLE PRECISION,
    sentiment_analyzed_at TIMESTAMP,

    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_artist_title UNIQUE (artist_name, title)
);

-- Indices for frequently queried columns
CREATE INDEX idx_track_lyrics_status ON track (lyrics_status);
CREATE INDEX idx_track_genre ON track (genre);
CREATE INDEX idx_track_release_year ON track (release_year);
CREATE INDEX idx_track_deezer_album_id ON track (deezer_album_id);
CREATE INDEX idx_track_theme ON track (theme);
CREATE INDEX idx_track_dl_theme ON track (dl_theme);
