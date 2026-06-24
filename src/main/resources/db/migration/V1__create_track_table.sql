CREATE TABLE track (
    id                  BIGSERIAL PRIMARY KEY,
    deezer_id           BIGINT UNIQUE,
    artist_name         VARCHAR(255) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    album_name          VARCHAR(500),
    genre               VARCHAR(255),
    release_year        INTEGER,

    lyrics              TEXT,
    lyrics_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, FOUND, NOT_FOUND, ERROR
    lyrics_fetched_at   TIMESTAMP,

    sentiment_label     VARCHAR(20),   -- z.B. VERY_NEGATIVE..VERY_POSITIVE
    sentiment_score     DOUBLE PRECISION, -- numerischer Score, z.B. 0.0 (negativ) bis 4.0 (positiv)
    sentiment_analyzed_at TIMESTAMP,

    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_artist_title UNIQUE (artist_name, title)
);

CREATE INDEX idx_track_lyrics_status ON track (lyrics_status);
CREATE INDEX idx_track_genre ON track (genre);
CREATE INDEX idx_track_release_year ON track (release_year);
