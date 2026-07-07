/**
 * API-Client für Lyrics Analyzer
 * Definiert globale Variable LyricsAnalyzerAPI
 *
 * Nutzt relative Pfade (kein hardcoded "http://localhost:8080"), da das Frontend von der
 * Spring-Boot-Anwendung selbst unter /static ausgeliefert wird. Relative Pfade funktionieren
 * dadurch unabhängig von Host/Port (z.B. auch hinter einem Reverse-Proxy).
 */

(function() {
    'use strict';

    const API_BASE = '/api';

    async function apiRequest(url, options = {}) {
        try {
            const response = await fetch(url, options);
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
            }
            return await response.json();
        } catch (error) {
            console.error('API Request failed:', error);
            throw error;
        }
    }

    async function searchAndIngestTracks(searchQuery, limit = 20) {
        return apiRequest(`${API_BASE}/ingestion`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ searchQuery, limit }),
        });
    }

    async function analyzePendingSentiment(limit = 50) {
        return apiRequest(`${API_BASE}/sentiment/analyze-pending?limit=${limit}`, {
            method: 'POST',
        });
    }

    async function retryErrorTracks(limit = 20) {
        return apiRequest(`${API_BASE}/ingestion/retry-errors?limit=${limit}`, {
            method: 'POST',
        });
    }

    async function backfillMetadata(limit = 50) {
        return apiRequest(`${API_BASE}/ingestion/backfill-metadata?limit=${limit}`, {
            method: 'POST',
        });
    }

    async function getTracks(page = 0, size = 20, search = '', status = '') {
        let url = `${API_BASE}/tracks?page=${page}&size=${size}`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (status) url += `&status=${encodeURIComponent(status)}`;
        return apiRequest(url);
    }

    async function getTrackById(trackId) {
        return apiRequest(`${API_BASE}/tracks/${trackId}`);
    }

    async function getStatsByGenre() {
        return apiRequest(`${API_BASE}/tracks/stats/by-genre`);
    }

    async function getStatsByYear() {
        return apiRequest(`${API_BASE}/tracks/stats/by-year`);
    }

    /**
     * Nutzt den dedizierten Backend-Endpoint /api/tracks/status-counts, statt (wie zuvor)
     * bis zu 1000 Tracks komplett zu laden und client-seitig zu zählen. Das Backend
     * berechnet die Zahlen direkt per COUNT-Query (TrackController.statusCounts /
     * TrackRepository.countByLyricsStatus) - schneller und korrekt auch bei >1000 Tracks.
     */
    async function getStatusCounts() {
        return apiRequest(`${API_BASE}/tracks/status-counts`);
    }

    // ==================== THEME CLASSIFICATION ====================

    async function trainThemeClassifier() {
        return apiRequest(`${API_BASE}/analysis/theme/train`, { method: 'POST' });
    }

    async function classifyTheme(artist, title) {
        return apiRequest(`${API_BASE}/analysis/theme/classify?artist=${encodeURIComponent(artist)}&title=${encodeURIComponent(title)}`);
    }

    async function classifyAllThemes() {
        return apiRequest(`${API_BASE}/analysis/theme/classify-all`, { method: 'POST' });
    }

    async function isThemeClassifierTrained() {
        return apiRequest(`${API_BASE}/analysis/theme/trained`);
    }

    // ==================== ARTIST STYLE ANALYSIS ====================

    async function getArtistStyle(artistName) {
        return apiRequest(`${API_BASE}/analysis/artist/style/${encodeURIComponent(artistName)}`);
    }

    async function compareArtists(artist1, artist2) {
        return apiRequest(`${API_BASE}/analysis/artist/compare?artist1=${encodeURIComponent(artist1)}&artist2=${encodeURIComponent(artist2)}`);
    }

    async function findSimilarArtists(artistName, limit = 5) {
        return apiRequest(`${API_BASE}/analysis/artist/similar?artistName=${encodeURIComponent(artistName)}&limit=${limit}`);
    }

    // ==================== LYRICS DNA ====================

    async function getAllDNA() {
        return apiRequest(`${API_BASE}/analysis/dna/all`);
    }

    async function getDNAVisualization() {
        return apiRequest(`${API_BASE}/analysis/dna/visualization`);
    }

    async function getArtistDNA(artistName) {
        return apiRequest(`${API_BASE}/analysis/dna/${encodeURIComponent(artistName)}`);
    }

    async function getSimilarArtistsDNA(artistName, limit = 5) {
        return apiRequest(`${API_BASE}/analysis/dna/similar?artistName=${encodeURIComponent(artistName)}&limit=${limit}`);
    }

    async function getAllThemes() {
        return apiRequest(`${API_BASE}/analysis/themes`);
    }

    async function saveTrackTheme(trackId, theme) {
        return apiRequest(`${API_BASE}/tracks/${trackId}/theme`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ theme }),
        });
    }

    async function deleteTrack(trackId) {
        return apiRequest(`${API_BASE}/tracks/${trackId}`, {
            method: 'DELETE',
        });
    }

    async function deleteTracks(trackIds) {
        const results = [];
        for (const id of trackIds) {
            try {
                await deleteTrack(id);
                results.push({ id, success: true });
            } catch (error) {
                results.push({ id, success: false, error: error.message });
            }
        }
        return results;
    }

    window.LyricsAnalyzerAPI = {
        searchAndIngestTracks,
        analyzePendingSentiment,
        retryErrorTracks,
        backfillMetadata,
        getTracks,
        getTrackById,
        getStatsByGenre,
        getStatsByYear,
        getStatusCounts,
        // Theme Classification
        trainThemeClassifier,
        classifyTheme,
        classifyAllThemes,
        isThemeClassifierTrained,
        // Artist Style Analysis
        getArtistStyle,
        compareArtists,
        findSimilarArtists,
        // Lyrics DNA
        getAllDNA,
        getDNAVisualization,
        getArtistDNA,
        getSimilarArtistsDNA,
        getAllThemes,
        saveTrackTheme,
        deleteTrack,
        deleteTracks,
    };
})();