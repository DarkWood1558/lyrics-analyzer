package com.lyricsanalyzer.repository;

import com.lyricsanalyzer.model.LyricsStatus;
import com.lyricsanalyzer.model.Track;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    @Query(value = "SELECT t.* FROM track t WHERE LOWER(CAST(t.artist_name AS TEXT)) = LOWER(CAST(:artistName AS TEXT)) AND LOWER(CAST(t.title AS TEXT)) = LOWER(CAST(:title AS TEXT)) LIMIT 1", nativeQuery = true)
    Optional<Track> findByArtistNameIgnoreCaseAndTitleIgnoreCase(@Param("artistName") String artistName, @Param("title") String title);

    Optional<Track> findByDeezerId(Long deezerId);

    @Query(nativeQuery = true, value = """
           SELECT * FROM track 
           WHERE lyrics_status = :status
           """)
    List<Track> findByLyricsStatus(@Param("status") String status, Pageable pageable);

    @Query(nativeQuery = true, value = """
           SELECT * FROM track 
           WHERE lyrics_status = :status 
           AND sentiment_label IS NULL
           """)
    List<Track> findByLyricsStatusAndSentimentLabelIsNull(@Param("status") String status, Pageable pageable);

    @Query(nativeQuery = true, value = """
           SELECT COUNT(*) FROM track 
           WHERE lyrics_status = :status
           """)
    long countByLyricsStatus(@Param("status") String status);

    @Query(value = "SELECT t.* FROM track t WHERE LOWER(CAST(t.artist_name AS TEXT)) = LOWER(CAST(:artistName AS TEXT))", nativeQuery = true)
    List<Track> findByArtistNameIgnoreCase(@Param("artistName") String artistName);

    /**
     * Tracks, für die ein nachträglicher Metadaten-Abruf (Genre/Erscheinungsjahr) über die
     * Deezer Album-API möglich und noch nicht erfolgt ist: eine Album-ID ist bekannt
     * (deezer_album_id IS NOT NULL), aber Genre oder Jahr fehlen noch.
     * Wird vom Backfill-Endpunkt genutzt, um bereits vor der Metadaten-Anreicherung
     * angelegte Tracks nachträglich zu vervollständigen.
     */
    @Query(value = """
           SELECT t.* FROM track t
           WHERE t.deezer_album_id IS NOT NULL
           AND (CAST(t.genre AS TEXT) IS NULL OR t.release_year IS NULL)
           ORDER BY t.id ASC
           """, nativeQuery = true)
    List<Track> findPendingMetadataBackfill(Pageable pageable);

    /**
     * Flexible Such-/Filter-Query für die GUI-Track-Liste.
     * {@code status} und {@code search} sind optional (NULL = Filter nicht angewendet) -
     * Dadurch deckt eine einzige Query alle Kombinationen ab (kein Filter / nur Status /
     * nur Suche / beides), ohne mehrere Repository-Methoden pflegen zu müssen.
     */
    @Query(nativeQuery = true, value = """
           SELECT * FROM track
           WHERE (:status IS NULL OR lyrics_status = :status)
           AND (:search IS NULL OR
                artist_name ILIKE ('%' || :search || '%') OR
                title ILIKE ('%' || :search || '%'))
           ORDER BY id DESC
           """)
    Page<Track> customSearchTracks(@Param("status") String status,
                                  @Param("search") String search,
                                  Pageable pageable);

    @Query(nativeQuery = true, value = """
           SELECT genre AS genre, AVG(sentiment_score) AS avg_score, COUNT(*) AS track_count
           FROM track
           WHERE sentiment_score IS NOT NULL AND CAST(genre AS TEXT) IS NOT NULL
           GROUP BY genre
           ORDER BY avg_score DESC
           """)
    List<GenreSentimentRow> findAverageSentimentByGenre();

    @Query(nativeQuery = true, value = """
           SELECT release_year AS year, AVG(sentiment_score) AS avg_score, COUNT(*) AS track_count
           FROM track
           WHERE sentiment_score IS NOT NULL AND release_year IS NOT NULL
           GROUP BY release_year
           ORDER BY release_year ASC
           """)
    List<YearSentimentRow> findAverageSentimentByYear();

    interface GenreSentimentRow {
        String getGenre();
        Double getAvg_score();
        Long getTrack_count();
    }

    interface YearSentimentRow {
        Integer getYear();
        Double getAvg_score();
        Long getTrack_count();
    }

    // Methoden für Deep Learning / Themenklassifikation
    List<Track> findAllByThemeNotNull();
    List<Track> findAllByThemeIsNull();
}
