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

    Optional<Track> findByArtistNameIgnoreCaseAndTitleIgnoreCase(String artistName, String title);

    Optional<Track> findByDeezerId(Long deezerId);

    List<Track> findByLyricsStatus(LyricsStatus status, Pageable pageable);

    List<Track> findByLyricsStatusAndSentimentLabelIsNull(LyricsStatus status, Pageable pageable);

    long countByLyricsStatus(LyricsStatus status);

    /**
     * Flexible Such-/Filter-Query für die GUI-Track-Liste.
     * {@code status} und {@code search} sind optional (NULL = Filter nicht angewendet) -
     * dadurch deckt eine einzige Query alle Kombinationen ab (kein Filter / nur Status /
     * nur Suche / beides), ohne mehrere Repository-Methoden pflegen zu müssen.
     */
    @Query("""
           SELECT t FROM Track t
           WHERE (:status IS NULL OR t.lyricsStatus = :status)
           AND (:search IS NULL OR
                LOWER(t.artistName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))
           ORDER BY t.id DESC
           """)
    Page<Track> search(@Param("status") LyricsStatus status,
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