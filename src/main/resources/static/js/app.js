/**
 * Lyrics Analyzer - Frontend Application
 * Haupt-UI-Logik - funktioniert ohne ES6-Module
 * Enthält alle Funktionen für Dashboard, Tracks, Stats, Themen, Stil-Analyse und Lyrics DNA
 */

(function() {
    'use strict';

    // ==================== STATE ====================
    const state = {
        currentTab: 'dashboard',
        tracks: {
            data: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
            search: '',
            statusFilter: '',
        },
        ingestionHistory: [],
        statusCounts: {
            FOUND: 0,
            NOT_FOUND: 0,
            ERROR: 0,
            PENDING: 0,
        },
    };

    // ==================== DOM ELEMENTS ====================
    const elements = {
        tabs: document.getElementById('tabs'),
        tabPanels: document.querySelectorAll('.tab-panel'),
        tabButtons: document.querySelectorAll('.tab-button'),

        // Dashboard
        countFOUND: document.getElementById('count-FOUND'),
        countNOT_FOUND: document.getElementById('count-NOT_FOUND'),
        countERROR: document.getElementById('count-ERROR'),
        countPENDING: document.getElementById('count-PENDING'),

        // Ingestion
        ingestionForm: document.getElementById('ingestion-form'),
        searchQuery: document.getElementById('search-query'),
        ingestionLimit: document.getElementById('ingestion-limit'),
        ingestionLoading: document.getElementById('ingestion-loading'),
        ingestionResult: document.getElementById('ingestion-result'),
        ingestionHistory: document.getElementById('ingestion-history'),

        // Sentiment
        sentimentLimit: document.getElementById('sentiment-limit'),
        btnRunSentiment: document.getElementById('btn-run-sentiment'),
        sentimentResult: document.getElementById('sentiment-result'),

        // Retry Errors
        retryLimit: document.getElementById('retry-limit'),
        btnRetryErrors: document.getElementById('btn-retry-errors'),
        retryResult: document.getElementById('retry-result'),

        // Backfill Metadata
        btnBackfillMetadata: document.getElementById('btn-backfill-metadata'),
        backfillResult: document.getElementById('backfill-result'),

        // Tracks
        trackSearch: document.getElementById('track-search'),
        trackStatusFilter: document.getElementById('track-status-filter'),
        btnApplyFilter: document.getElementById('btn-apply-filter'),
        tracksTbody: document.getElementById('tracks-tbody'),
        pagination: document.getElementById('pagination'),
        paginationInfo: document.getElementById('pagination-info'),
        btnPrevPage: document.getElementById('btn-prev-page'),
        btnNextPage: document.getElementById('btn-next-page'),

        // Stats
        statsGenre: document.getElementById('stats-genre'),
        statsYear: document.getElementById('stats-year'),

        // Modal
        modalOverlay: document.getElementById('track-modal-overlay'),
        modalContent: document.getElementById('modal-content'),
        modalCloseBtn: document.getElementById('modal-close-btn'),

        // Toast
        toast: document.getElementById('toast'),

        // Theme Classification
        themeArtist: document.getElementById('theme-artist'),
        themeTitle: document.getElementById('theme-title'),
        btnClassifyTheme: document.getElementById('btn-classify-theme'),
        themeResult: document.getElementById('theme-result'),
        btnTrainTheme: document.getElementById('btn-train-theme'),
        btnClassifyAll: document.getElementById('btn-classify-all'),
        themeTrainResult: document.getElementById('theme-train-result'),

        // Style Analysis
        artist1: document.getElementById('artist1'),
        artist2: document.getElementById('artist2'),
        btnCompareArtists: document.getElementById('btn-compare-artists'),
        styleCompareResult: document.getElementById('style-compare-result'),
        similarArtist: document.getElementById('similar-artist'),
        similarLimit: document.getElementById('similar-limit'),
        btnFindSimilar: document.getElementById('btn-find-similar'),
        similarResult: document.getElementById('similar-result'),

        // DNA
        btnLoadDNA: document.getElementById('btn-load-dna'),
        dnaVisualization: document.getElementById('dna-visualization'),
        dnaResult: document.getElementById('dna-result'),
        dnaArtist: document.getElementById('dna-artist'),
        btnGetDNA: document.getElementById('btn-get-dna'),
        dnaDetailsResult: document.getElementById('dna-details-result'),
    };

    // API Reference
    const api = window.LyricsAnalyzerAPI;

    // ==================== UTILITY ====================

    function showLoading(element, show = true) {
        if (show) element.classList.remove('hidden');
        else element.classList.add('hidden');
    }

    function showResult(element, message, isError = false) {
        element.textContent = message;
        element.className = `result-box ${isError ? 'result-box-error' : 'result-box-success'}`;
        element.classList.remove('hidden');
    }

    function showToast(message, duration = 3000) {
        elements.toast.textContent = message;
        elements.toast.classList.remove('hidden');
        setTimeout(() => elements.toast.classList.add('hidden'), duration);
    }

    /**
     * sentimentScore liegt auf der CoreNLP-Skala 0.0 (very negative) - 4.0 (very positive),
     * NICHT auf einer -1..1-Skala. Reine Zahlenausgabe mit 3 Nachkommastellen.
     */
    function formatSentiment(score) {
        if (score === null || score === undefined) return '–';
        return score.toFixed(3);
    }

    /**
     * Nutzt das vom Backend bereits berechnete sentimentLabel-Enum
     * (VERY_NEGATIVE..VERY_POSITIVE), statt den 0..4-Score selbst (falsch) als -1..1
     * zu interpretieren. Vorher wurde score > 0.2 / < -0.2 geprüft, was auf der
     * tatsächlichen 0..4-Skala praktisch immer "Positiv" ergeben hätte.
     */
    const SENTIMENT_LABEL_TEXT = {
        VERY_NEGATIVE: 'Sehr negativ',
        NEGATIVE: 'Negativ',
        NEUTRAL: 'Neutral',
        POSITIVE: 'Positiv',
        VERY_POSITIVE: 'Sehr positiv',
    };

    function formatSentimentLabel(label) {
        if (!label) return 'Kein Sentiment';
        return SENTIMENT_LABEL_TEXT[label] || label;
    }

    function escapeHtml(text) {
        if (text === null || text === undefined) return '–';
        const div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }

    // ==================== TAB NAVIGATION ====================

    function initTabs() {
        elements.tabButtons.forEach(button => {
            button.addEventListener('click', () => switchTab(button.dataset.tab));
        });
    }

    function switchTab(tabName) {
        state.currentTab = tabName;
        elements.tabButtons.forEach(b => b.classList.toggle('active', b.dataset.tab === tabName));
        elements.tabPanels.forEach(p => p.classList.toggle('active', p.id === `panel-${tabName}`));

        if (tabName === 'dashboard') loadDashboard();
        else if (tabName === 'tracks') loadTracks();
        else if (tabName === 'stats') loadStats();
    }

    // ==================== DASHBOARD ====================

    async function loadDashboard() {
        try {
            const counts = await api.getStatusCounts();
            state.statusCounts = counts;
            updateStatusCards();
        } catch (error) {
            showToast(`Dashboard: ${error.message}`);
        }
    }

    function updateStatusCards() {
        elements.countFOUND.textContent = state.statusCounts.FOUND || 0;
        elements.countNOT_FOUND.textContent = state.statusCounts.NOT_FOUND || 0;
        elements.countERROR.textContent = state.statusCounts.ERROR || 0;
        elements.countPENDING.textContent = state.statusCounts.PENDING || 0;
    }

    // ==================== INGESTION ====================

    function initIngestion() {
        if (elements.ingestionForm) {
            elements.ingestionForm.addEventListener('submit', handleIngestionSubmit);
        }
    }

    async function handleIngestionSubmit(e) {
        e.preventDefault();
        const searchQuery = elements.searchQuery.value.trim();
        const limit = parseInt(elements.ingestionLimit.value) || 20;

        if (!searchQuery) {
            showResult(elements.ingestionResult, 'Bitte Suchbegriff eingeben', true);
            return;
        }

        showLoading(elements.ingestionLoading, true);
        elements.ingestionResult.classList.add('hidden');

        try {
            const result = await api.searchAndIngestTracks(searchQuery, limit);
            saveToHistory(searchQuery);
            const msg = `Gefunden: ${result.searched}, Cache: ${result.alreadyCached}, Neu: ${result.newlyFetched}, Nicht gefunden: ${result.notFound}, Fehler: ${result.errors}`;
            showResult(elements.ingestionResult, msg, false);
            loadDashboard();
            if (result.newlyFetched > 0) switchTab('tracks');
        } catch (error) {
            showResult(elements.ingestionResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.ingestionLoading, false);
        }
    }

    function saveToHistory(query) {
        if (!state.ingestionHistory.includes(query)) {
            state.ingestionHistory.unshift(query);
            if (state.ingestionHistory.length > 10) state.ingestionHistory.pop();
        }
        updateHistory();
    }

    function updateHistory() {
        if (!elements.ingestionHistory) return;
        if (state.ingestionHistory.length === 0) {
            elements.ingestionHistory.innerHTML = '<li class="muted">Noch keine Suche durchgeführt.</li>';
            return;
        }
        elements.ingestionHistory.innerHTML = state.ingestionHistory
            .map(q => `<li><button class="history-item" data-query="${encodeURIComponent(q)}">${escapeHtml(q)}</button></li>`)
            .join('');
        elements.ingestionHistory.querySelectorAll('.history-item').forEach(btn => {
            btn.addEventListener('click', () => {
                elements.searchQuery.value = decodeURIComponent(btn.dataset.query);
            });
        });
    }

    // ==================== SENTIMENT ====================

    function initSentiment() {
        if (elements.btnRunSentiment) {
            elements.btnRunSentiment.addEventListener('click', handleRunSentiment);
        }
    }

    async function handleRunSentiment() {
        const limit = parseInt(elements.sentimentLimit.value) || 50;
        showLoading(elements.sentimentResult, true);
        try {
            const result = await api.analyzePendingSentiment(limit);
            showResult(elements.sentimentResult, `Analyse für ${result.analyzedCount ?? limit} Tracks abgeschlossen`, false);
            showToast('Sentiment-Analyse abgeschlossen');
            loadDashboard();
            loadTracks();
            loadStats();
        } catch (error) {
            showResult(elements.sentimentResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.sentimentResult, false);
        }
    }

    // ==================== RETRY ERRORS ====================

    function initRetryErrors() {
        if (elements.btnRetryErrors) {
            elements.btnRetryErrors.addEventListener('click', handleRetryErrors);
        }
    }

    async function handleRetryErrors() {
        const limit = parseInt(elements.retryLimit.value) || 20;
        showLoading(elements.retryResult, true);
        try {
            const result = await api.retryErrorTracks(limit);
            showResult(elements.retryResult, `Erneuter Versuch abgeschlossen: ${result.attempted} versucht, ${result.newlyFetched} neu gefunden, ${result.notFound} nicht gefunden, ${result.stillError} weiterhin Fehler`, false);
            showToast('Erneuter Ladeversuch abgeschlossen');
            loadDashboard();
            loadTracks();
        } catch (error) {
            showResult(elements.retryResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.retryResult, false);
        }
    }

    // ==================== BACKFILL METADATA ====================

    function initBackfillMetadata() {
        if (elements.btnBackfillMetadata) {
            elements.btnBackfillMetadata.addEventListener('click', handleBackfillMetadata);
        }
    }

    async function handleBackfillMetadata() {
        const limitInput = document.getElementById('metadata-limit');
        const limit = parseInt(limitInput?.value) || 50;
        showLoading(elements.backfillResult, true);
        try {
            const result = await api.backfillMetadata(limit);
            showResult(elements.backfillResult, `Metadaten-Backfill abgeschlossen: ${result.attempted} versucht, ${result.updated} aktualisiert, ${result.skipped} übersprungen`, false);
            showToast('Metadaten-Backfill abgeschlossen');
            loadStats();
        } catch (error) {
            showResult(elements.backfillResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.backfillResult, false);
        }
    }

    // ==================== TRACKS ====================

    function initTracks() {
        if (elements.btnApplyFilter) {
            elements.btnApplyFilter.addEventListener('click', () => {
                state.tracks.page = 0;
                loadTracks();
            });
        }
        if (elements.trackSearch) {
            elements.trackSearch.addEventListener('keyup', (e) => {
                if (e.key === 'Enter') {
                    state.tracks.page = 0;
                    loadTracks();
                }
            });
        }
        if (elements.btnPrevPage) {
            elements.btnPrevPage.addEventListener('click', () => {
                if (state.tracks.page > 0) {
                    state.tracks.page--;
                    loadTracks();
                }
            });
        }
        if (elements.btnNextPage) {
            elements.btnNextPage.addEventListener('click', () => {
                if (state.tracks.page < state.tracks.totalPages - 1) {
                    state.tracks.page++;
                    loadTracks();
                }
            });
        }
    }

    async function loadTracks() {
        state.tracks.search = elements.trackSearch ? elements.trackSearch.value : '';
        state.tracks.statusFilter = elements.trackStatusFilter ? elements.trackStatusFilter.value : '';
        try {
            const result = await api.getTracks(
                state.tracks.page,
                state.tracks.size,
                state.tracks.search,
                state.tracks.statusFilter
            );
            state.tracks.data = result.content || [];
            state.tracks.totalElements = result.totalElements || 0;
            state.tracks.totalPages = result.totalPages || 0;
            renderTracksTable();
            updatePagination();
        } catch (error) {
            showToast(`Tracks: ${error.message}`);
        }
    }

    /**
     * WICHTIG: Das Backend (TrackResponse) liefert die Felder artistName/albumName,
     * NICHT artist/album. Vorher griff dieser Code auf track.artist/track.album zu,
     * was immer undefined war - die Tabelle zeigte daher in Künstler- und Album-Spalte
     * nur "–" an, unabhängig von den tatsächlichen Daten.
     */
    function renderTracksTable() {
        if (!elements.tracksTbody) return;
        if (state.tracks.data.length === 0) {
            elements.tracksTbody.innerHTML = '<tr><td colspan="8" class="muted">Keine Tracks gefunden</td></tr>';
            return;
        }
        elements.tracksTbody.innerHTML = state.tracks.data
            .map(track => `
                <tr data-track-id="${track.id}">
                    <td>${track.id}</td>
                    <td>${escapeHtml(track.artistName)}</td>
                    <td>${escapeHtml(track.title)}</td>
                    <td>${escapeHtml(track.albumName)}</td>
                    <td><span class="status-badge status-${track.lyricsStatus || 'PENDING'}">${track.lyricsStatus || 'PENDING'}</span></td>
                    <td>${formatSentimentLabel(track.sentimentLabel)}</td>
                    <td>${formatSentiment(track.sentimentScore)}</td>
                    <td><button class="btn btn-small btn-details" data-track-id="${track.id}">Details</button></td>
                </tr>
            `)
            .join('');
        elements.tracksTbody.querySelectorAll('.btn-details').forEach(btn => {
            btn.addEventListener('click', () => showTrackDetails(parseInt(btn.dataset.trackId)));
        });
    }

    function updatePagination() {
        if (elements.paginationInfo) {
            elements.paginationInfo.textContent = `Seite ${state.tracks.page + 1} von ${state.tracks.totalPages || 1}`;
        }
        if (elements.btnPrevPage) {
            elements.btnPrevPage.disabled = state.tracks.page === 0;
        }
        if (elements.btnNextPage) {
            elements.btnNextPage.disabled = state.tracks.page >= (state.tracks.totalPages - 1);
        }
    }

    /**
     * Nutzt ebenfalls artistName/albumName statt artist/album (siehe renderTracksTable).
     * track.theme existiert jetzt im DTO (TrackDetailResponse wurde entsprechend erweitert).
     */
    async function showTrackDetails(trackId) {
        try {
            const track = await api.getTrackById(trackId);
            const html = `
                <div class="modal-header">
                    <h2>${escapeHtml(track.artistName)} – ${escapeHtml(track.title)}</h2>
                </div>
                <div class="modal-body">
                    <div class="detail-grid">
                        <div class="detail-item"><strong>Album:</strong> ${escapeHtml(track.albumName)}</div>
                        <div class="detail-item"><strong>Lyrics-Status:</strong> <span class="status-badge status-${track.lyricsStatus || 'PENDING'}">${track.lyricsStatus || 'PENDING'}</span></div>
                        <div class="detail-item"><strong>Sentiment:</strong> ${formatSentimentLabel(track.sentimentLabel)} (${formatSentiment(track.sentimentScore)})</div>
                        <div class="detail-item"><strong>Genre:</strong> ${escapeHtml(track.genre)}</div>
                        <div class="detail-item"><strong>Jahr:</strong> ${escapeHtml(track.releaseYear)}</div>
                        ${track.theme ? `<div class="detail-item"><strong>Thema:</strong> ${escapeHtml(track.theme)}</div>` : ''}
                    </div>
                    ${track.lyrics ? `
                        <div class="lyrics-section">
                            <h3>Songtext</h3>
                            <pre class="lyrics-text">${escapeHtml(track.lyrics)}</pre>
                        </div>
                    ` : ''}
                </div>
            `;
            elements.modalContent.innerHTML = html;
            elements.modalOverlay.classList.remove('hidden');
        } catch (error) {
            showToast(`Details: ${error.message}`);
        }
    }

    // ==================== STATS ====================

    async function loadStats() {
        try {
            const [genreStats, yearStats] = await Promise.all([
                api.getStatsByGenre(),
                api.getStatsByYear(),
            ]);
            renderGenreStats(genreStats);
            renderYearStats(yearStats);
        } catch (error) {
            showToast(`Stats: ${error.message}`);
        }
    }

    /**
     * Backend liefert averageSentimentScore (siehe TrackController.sentimentByGenre),
     * nicht averageSentiment.
     */
    function renderGenreStats(stats) {
        if (!elements.statsGenre) return;
        if (!stats || stats.length === 0) {
            elements.statsGenre.innerHTML = '<p class="muted">Keine Genre-Daten verfügbar. Hinweis: Genre muss von Deezer geladen werden (siehe README).</p>';
            return;
        }
        elements.statsGenre.innerHTML = stats
            .map(s => `
                <div class="stat-item">
                    <span class="stat-genre">${escapeHtml(s.genre || 'Unbekannt')}</span>
                    <span class="stat-value">${formatSentiment(s.averageSentimentScore)} (n=${s.trackCount})</span>
                </div>
            `)
            .join('');
    }

    function renderYearStats(stats) {
        if (!elements.statsYear) return;
        if (!stats || stats.length === 0) {
            elements.statsYear.innerHTML = '<p class="muted">Keine Jahr-Daten verfügbar. Hinweis: Jahr muss von Deezer geladen werden (siehe README).</p>';
            return;
        }
        const sorted = [...stats].sort((a, b) => (b.year || 0) - (a.year || 0));
        elements.statsYear.innerHTML = sorted
            .map(s => `
                <div class="stat-item">
                    <span class="stat-year">${s.year || 'Unbekannt'}</span>
                    <span class="stat-value">${formatSentiment(s.averageSentimentScore)} (n=${s.trackCount})</span>
                </div>
            `)
            .join('');
    }

    // ==================== MODAL ====================

    function initModal() {
        if (elements.modalCloseBtn) {
            elements.modalCloseBtn.addEventListener('click', () => {
                elements.modalOverlay.classList.add('hidden');
            });
        }
        if (elements.modalOverlay) {
            elements.modalOverlay.addEventListener('click', (e) => {
                if (e.target === elements.modalOverlay) {
                    elements.modalOverlay.classList.add('hidden');
                }
            });
        }
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && elements.modalOverlay && !elements.modalOverlay.classList.contains('hidden')) {
                elements.modalOverlay.classList.add('hidden');
            }
        });
    }

    // ==================== THEME CLASSIFICATION ====================

    function initThemes() {
        if (elements.btnClassifyTheme) {
            elements.btnClassifyTheme.addEventListener('click', handleClassifyTheme);
        }
        if (elements.btnTrainTheme) {
            elements.btnTrainTheme.addEventListener('click', handleTrainTheme);
        }
        if (elements.btnClassifyAll) {
            elements.btnClassifyAll.addEventListener('click', handleClassifyAll);
        }
    }

    async function handleClassifyTheme() {
        const artist = elements.themeArtist?.value?.trim();
        const title = elements.themeTitle?.value?.trim();

        if (!artist || !title) {
            showResult(elements.themeResult, 'Bitte Künstler und Titel eingeben', true);
            return;
        }

        showLoading(elements.themeResult, true);
        try {
            const response = await api.classifyTheme(artist, title);

            if (response.error) {
                showResult(elements.themeResult, `Fehler: ${response.error}`, true);
                return;
            }

            const themeDistHtml = Object.entries(response.themeDistribution || {})
                .map(([theme, percent]) => `<div><strong>${escapeHtml(theme)}:</strong> ${percent}%</div>`)
                .join('');

            if (elements.themeResult) {
                elements.themeResult.innerHTML = `
                    <div><strong>Vorhergesagtes Thema:</strong> ${escapeHtml(response.predictedTheme || '–')}</div>
                    <div style="margin-top: 1rem;"><strong>Themenverteilung:</strong></div>
                    ${themeDistHtml}
                `;
                elements.themeResult.classList.remove('hidden', 'result-box-error');
                elements.themeResult.classList.add('result-box-success');
            }
        } catch (error) {
            showResult(elements.themeResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.themeResult, false);
        }
    }

    async function handleTrainTheme() {
        showLoading(elements.themeTrainResult, true);
        try {
            const response = await api.trainThemeClassifier();

            if (response.error) {
                showResult(elements.themeTrainResult, `Fehler: ${response.error || response.message}`, true);
                return;
            }

            showResult(elements.themeTrainResult, `Klassifikator trainiert mit ${response.trainingSamples} Tracks!`, false);
            showToast('Klassifikator trainiert! Jetzt können Themen vorhergesagt werden.');
        } catch (error) {
            showResult(elements.themeTrainResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.themeTrainResult, false);
        }
    }

    async function handleClassifyAll() {
        showLoading(elements.themeTrainResult, true);
        try {
            const response = await api.classifyAllThemes();

            if (response.error) {
                showResult(elements.themeTrainResult, `Fehler: ${response.error || response.message}`, true);
                return;
            }

            showResult(elements.themeTrainResult, `Klassifiziert: ${response.classifiedTracks} Tracks, übersprungen: ${response.skippedTracks}`, false);
            showToast(`Alle Tracks klassifiziert!`);
            loadTracks();
        } catch (error) {
            showResult(elements.themeTrainResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.themeTrainResult, false);
        }
    }

    // ==================== STYLE ANALYSIS ====================

    function initStyle() {
        if (elements.btnCompareArtists) {
            elements.btnCompareArtists.addEventListener('click', handleCompareArtists);
        }
        if (elements.btnFindSimilar) {
            elements.btnFindSimilar.addEventListener('click', handleFindSimilar);
        }
    }

    async function handleCompareArtists() {
        const artist1 = elements.artist1?.value?.trim();
        const artist2 = elements.artist2?.value?.trim();

        if (!artist1 || !artist2) {
            showResult(elements.styleCompareResult, 'Bitte zwei Künstler eingeben', true);
            return;
        }

        showLoading(elements.styleCompareResult, true);
        try {
            const response = await api.compareArtists(artist1, artist2);

            if (response.error) {
                showResult(elements.styleCompareResult, `Fehler: ${response.error}`, true);
                return;
            }

            if (elements.styleCompareResult) {
                elements.styleCompareResult.innerHTML = `
                    <div><strong>Ähnlichkeit:</strong> ${escapeHtml(response.similarity)}</div>
                    <div style="margin-top: 1rem;">
                        <strong>${escapeHtml(response.artist1)} Features:</strong>
                        <pre style="margin-top: 0.5rem;">${escapeHtml(JSON.stringify(response.featuresArtist1, null, 2))}</pre>
                    </div>
                    <div style="margin-top: 1rem;">
                        <strong>${escapeHtml(response.artist2)} Features:</strong>
                        <pre style="margin-top: 0.5rem;">${escapeHtml(JSON.stringify(response.featuresArtist2, null, 2))}</pre>
                    </div>
                `;
                elements.styleCompareResult.classList.remove('hidden', 'result-box-error');
                elements.styleCompareResult.classList.add('result-box-success');
            }
        } catch (error) {
            showResult(elements.styleCompareResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.styleCompareResult, false);
        }
    }

    async function handleFindSimilar() {
        const artist = elements.similarArtist?.value?.trim();
        const limit = parseInt(elements.similarLimit?.value) || 5;

        if (!artist) {
            showResult(elements.similarResult, 'Bitte Künstler eingeben', true);
            return;
        }

        showLoading(elements.similarResult, true);
        try {
            const response = await api.findSimilarArtists(artist, limit);

            if (Object.keys(response).length === 0) {
                showResult(elements.similarResult, 'Keine ähnlichen Künstler gefunden', true);
                return;
            }

            const similarHtml = Object.entries(response)
                .map(([name, similarity]) => `<div>${escapeHtml(name)}: ${(similarity * 100).toFixed(1)}%</div>`)
                .join('');

            if (elements.similarResult) {
                elements.similarResult.innerHTML = `
                    <div><strong>Ähnlichste Künstler zu ${escapeHtml(artist)}:</strong></div>
                    ${similarHtml}
                `;
                elements.similarResult.classList.remove('hidden', 'result-box-error');
                elements.similarResult.classList.add('result-box-success');
            }
        } catch (error) {
            showResult(elements.similarResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.similarResult, false);
        }
    }

    // ==================== LYRICS DNA ====================

    function initDNA() {
        if (elements.btnLoadDNA) {
            elements.btnLoadDNA.addEventListener('click', handleLoadDNA);
        }
        if (elements.btnGetDNA) {
            elements.btnGetDNA.addEventListener('click', handleGetDNA);
        }
    }

    async function handleLoadDNA() {
        showLoading(elements.dnaResult, true);
        try {
            const data = await api.getDNAVisualization();

            if (!data || data.length === 0) {
                showResult(elements.dnaResult, 'Keine DNA-Daten verfügbar. Zuerst Tracks laden!', true);
                return;
            }

            renderDNAVisualization(data);
            if (elements.dnaResult) {
                elements.dnaResult.classList.add('hidden');
            }
        } catch (error) {
            showResult(elements.dnaResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.dnaResult, false);
        }
    }

    /**
     * Nutzt jetzt data-Attribute + addEventListener statt Inline-onmouseenter mit
     * interpoliertem JSON.stringify(...).replace(/"/g, '&quot;'). Die alte Variante war
     * fragil (bricht bei Künstlernamen mit Anführungszeichen/Sonderzeichen) und potenziell
     * XSS-anfällig, da nicht-escapte Werte direkt ins HTML-Attribut interpoliert wurden.
     * Die Punkt-Daten werden stattdessen in einer Map gehalten und per Index referenziert.
     */
    const dnaPointsById = new Map();

    function renderDNAVisualization(data) {
        if (!elements.dnaVisualization) return;
        dnaPointsById.clear();

        elements.dnaVisualization.innerHTML = `
            <style>
                .dna-scatterplot {
                    position: relative;
                    width: 100%;
                    height: 400px;
                    border: 1px solid var(--border-color);
                    border-radius: var(--border-radius);
                    background: var(--surface-hover);
                }
                .dna-point {
                    position: absolute;
                    border-radius: 50%;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-size: 10px;
                    font-weight: bold;
                    text-shadow: 1px 1px 2px rgba(0,0,0,0.5);
                    transition: transform 0.2s ease;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.3);
                }
                .dna-point:hover {
                    transform: scale(1.5);
                    z-index: 10;
                }
                .dna-tooltip {
                    position: absolute;
                    background: var(--surface-color);
                    border: 1px solid var(--border-color);
                    border-radius: var(--border-radius);
                    padding: 0.5rem;
                    font-size: 0.8rem;
                    z-index: 100;
                    pointer-events: none;
                    display: none;
                    max-width: 300px;
                }
            </style>
            <div class="dna-scatterplot" id="dna-scatterplot">
                ${data.map((point, idx) => {
            dnaPointsById.set(String(idx), point);
            return `
                    <div class="dna-point"
                         data-point-id="${idx}"
                         style="left: ${(point.x * 10 + 50)}%; top: ${(10 - point.y * 10 + 10)}%;
                               background: ${point.color === 'positive' ? 'var(--success-color)' : 'var(--error-color)'};
                               width: ${point.size}px; height: ${point.size}px;"
                         title="${escapeHtml(point.artist)} (${escapeHtml(point.topTheme)})">
                    </div>
                `;
        }).join('')}
                <div class="dna-tooltip" id="dna-tooltip"></div>
            </div>
        `;

        elements.dnaVisualization.querySelectorAll('.dna-point').forEach(el => {
            el.addEventListener('mouseenter', (e) => showDNATooltip(e, dnaPointsById.get(el.dataset.pointId)));
            el.addEventListener('mouseleave', hideDNATooltip);
        });
    }

    function showDNATooltip(event, point) {
        const tooltip = document.getElementById('dna-tooltip');
        if (!tooltip || !point) return;

        const themeHtml = Object.entries(point.themes || {})
            .map(([theme, percent]) => `<div>${escapeHtml(theme)}: ${percent}%</div>`)
            .join('');

        tooltip.innerHTML = `
            <div><strong>${escapeHtml(point.artist)}</strong></div>
            <div>Top Thema: ${escapeHtml(point.topTheme)}</div>
            <div>Themenverteilung:</div>
            ${themeHtml}
            <div>Sentiment: ${point.averageSentiment !== undefined && point.averageSentiment !== null ? point.averageSentiment.toFixed(2) : 'N/A'}</div>
        `;
        tooltip.style.display = 'block';
        tooltip.style.left = (event.pageX + 10) + 'px';
        tooltip.style.top = (event.pageY + 10) + 'px';
    }

    function hideDNATooltip() {
        const tooltip = document.getElementById('dna-tooltip');
        if (tooltip) tooltip.style.display = 'none';
    }

    async function handleGetDNA() {
        const artist = elements.dnaArtist?.value?.trim();

        if (!artist) {
            showResult(elements.dnaDetailsResult, 'Bitte Künstler eingeben', true);
            return;
        }

        showLoading(elements.dnaDetailsResult, true);
        try {
            const response = await api.getArtistDNA(artist);

            if (response.error) {
                showResult(elements.dnaDetailsResult, `Fehler: ${response.error}`, true);
                return;
            }

            const themeHtml = Object.entries(response.themeDistribution || {})
                .map(([theme, percent]) => `<div>${escapeHtml(theme)}: ${percent}%</div>`)
                .join('');

            if (elements.dnaDetailsResult) {
                elements.dnaDetailsResult.innerHTML = `
                    <div><strong>Künstler:</strong> ${escapeHtml(response.artist)}</div>
                    <div><strong>Durchschnittliches Sentiment:</strong> ${response.averageSentiment.toFixed(3)}</div>
                    <div><strong>Feature-Vektor:</strong> [${response.featureVector.map(v => v.toFixed(3)).join(', ')}]</div>
                    <div style="margin-top: 1rem;"><strong>Themenverteilung:</strong></div>
                    ${themeHtml}
                `;
                elements.dnaDetailsResult.classList.remove('hidden', 'result-box-error');
                elements.dnaDetailsResult.classList.add('result-box-success');
            }
        } catch (error) {
            showResult(elements.dnaDetailsResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.dnaDetailsResult, false);
        }
    }

    // ==================== INIT ====================

    function init() {
        initTabs();
        initIngestion();
        initSentiment();
        initRetryErrors();
        initBackfillMetadata();
        initTracks();
        initModal();
        initThemes();
        initStyle();
        initDNA();

        loadDashboard();
        loadTracks();
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();