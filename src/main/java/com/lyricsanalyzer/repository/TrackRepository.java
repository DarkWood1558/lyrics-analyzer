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

    List<Track> findByLyricsStatus(LyricsStatus status, Pageable pageable);

    List<Track> findByLyricsStatusAndSentimentLabelIsNull(LyricsStatus status, Pageable pageable);

    long countByLyricsStatus(LyricsStatus status);

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
           AND (t.genre IS NULL OR t.release_year IS NULL)
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
           WHERE (COALESCE(:status, '') = '' OR lyrics_status = CAST(:status AS VARCHAR(20)))
           AND (COALESCE(:search, '') = '' OR
                artist_name ILIKE ('%' || :search || '%') OR
                title ILIKE ('%' || :search || '%'))
           ORDER BY id DESC
           """)
    Page<Track> customSearchTracks(@Param("status") LyricsStatus status,
                                  @Param("search") String search,
                                  Pageable pageable);

    @Query("""
           SELECT t.genre AS genre, AVG(t.sentimentScore) AS avgScore, COUNT(t) AS trackCount
           FROM Track t
           WHERE t.sentimentScore IS NOT NULL AND t.genre IS NOT NULL
           GROUP BY t.genre
           ORDER BY avgScore DESC
           """)
    List<GenreSentimentRow> findAverageSentimentByGenre();

    @Query("""
           SELECT t.releaseYear AS year, AVG(t.sentimentScore) AS avgScore, COUNT(t) AS trackCount
           FROM Track t
           WHERE t.sentimentScore IS NOT NULL AND t.releaseYear IS NOT NULL
           GROUP BY t.releaseYear
           ORDER BY t.releaseYear ASC
           """)
    List<YearSentimentRow> findAverageSentimentByYear();

    interface GenreSentimentRow {
        String getGenre();
        Double getAvgScore();
        Long getTrackCount();
    }

    interface YearSentimentRow {
        Integer getYear();
        Double getAvgScore();
        Long getTrackCount();
    }
}
