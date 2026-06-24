/**
 * API-Client für Lyrics Analyzer
 * Definiert globale Variable LyricsAnalyzerAPI
 */

(function() {
    'use strict';

    const API_BASE = 'http://localhost:8080/api';

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

    async function getStatusCounts() {
        const tracks = await getTracks(0, 1000);
        const counts = { FOUND: 0, NOT_FOUND: 0, ERROR: 0, PENDING: 0 };
        (tracks.content || []).forEach(track => {
            const status = track.lyricsStatus || 'PENDING';
            if (counts[status] !== undefined) counts[status]++;
        });
        return counts;
    }

    // ==================== THEME CLASSIFICATION ====================

    async function trainThemeClassifier() {
        return apiRequest('/api/analysis/theme/train', { method: 'POST' });
    }

    async function classifyTheme(artist, title) {
        return apiRequest(`/api/analysis/theme/classify?artist=${encodeURIComponent(artist)}&title=${encodeURIComponent(title)}`);
    }

    async function classifyAllThemes() {
        return apiRequest('/api/analysis/theme/classify-all', { method: 'POST' });
    }

    async function isThemeClassifierTrained() {
        return apiRequest('/api/analysis/theme/trained');
    }

    // ==================== ARTIST STYLE ANALYSIS ====================

    async function getArtistStyle(artistName) {
        return apiRequest(`/api/analysis/artist/style/${encodeURIComponent(artistName)}`);
    }

    async function compareArtists(artist1, artist2) {
        return apiRequest(`/api/analysis/artist/compare?artist1=${encodeURIComponent(artist1)}&artist2=${encodeURIComponent(artist2)}`);
    }

    async function findSimilarArtists(artistName, limit = 5) {
        return apiRequest(`/api/analysis/artist/similar?artistName=${encodeURIComponent(artistName)}&limit=${limit}`);
    }

    // ==================== LYRICS DNA ====================

    async function getAllDNA() {
        return apiRequest('/api/analysis/dna/all');
    }

    async function getDNAVisualization() {
        return apiRequest('/api/analysis/dna/visualization');
    }

    async function getArtistDNA(artistName) {
        return apiRequest(`/api/analysis/dna/${encodeURIComponent(artistName)}`);
    }

    async function getSimilarArtistsDNA(artistName, limit = 5) {
        return apiRequest(`/api/analysis/dna/similar?artistName=${encodeURIComponent(artistName)}&limit=${limit}`);
    }

    async function getAllThemes() {
        return apiRequest('/api/analysis/themes');
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
    };
})();
