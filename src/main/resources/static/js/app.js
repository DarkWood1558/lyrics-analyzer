/**
 * Lyrics Analyzer - Frontend Application
 * Haupt-UI-Logik - funktioniert ohne ES6-Module
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
        
        countFOUND: document.getElementById('count-FOUND'),
        countNOT_FOUND: document.getElementById('count-NOT_FOUND'),
        countERROR: document.getElementById('count-ERROR'),
        countPENDING: document.getElementById('count-PENDING'),
        
        ingestionForm: document.getElementById('ingestion-form'),
        searchQuery: document.getElementById('search-query'),
        ingestionLimit: document.getElementById('ingestion-limit'),
        ingestionLoading: document.getElementById('ingestion-loading'),
        ingestionResult: document.getElementById('ingestion-result'),
        ingestionHistory: document.getElementById('ingestion-history'),
        
        sentimentLimit: document.getElementById('sentiment-limit'),
        btnRunSentiment: document.getElementById('btn-run-sentiment'),
        sentimentResult: document.getElementById('sentiment-result'),
        
        retryLimit: document.getElementById('retry-limit'),
        btnRetryErrors: document.getElementById('btn-retry-errors'),
        retryResult: document.getElementById('retry-result'),

        backfillLimit: document.getElementById('backfill-limit'),
        btnBackfillMetadata: document.getElementById('btn-backfill-metadata'),
        backfillResult: document.getElementById('backfill-result'),
        
        trackSearch: document.getElementById('track-search'),
        trackStatusFilter: document.getElementById('track-status-filter'),
        btnApplyFilter: document.getElementById('btn-apply-filter'),
        tracksTbody: document.getElementById('tracks-tbody'),
        pagination: document.getElementById('pagination'),
        paginationInfo: document.getElementById('pagination-info'),
        btnPrevPage: document.getElementById('btn-prev-page'),
        btnNextPage: document.getElementById('btn-next-page'),
        
        statsGenre: document.getElementById('stats-genre'),
        statsYear: document.getElementById('stats-year'),
        
        modalOverlay: document.getElementById('track-modal-overlay'),
        modalContent: document.getElementById('modal-content'),
        modalCloseBtn: document.getElementById('modal-close-btn'),
        
        toast: document.getElementById('toast'),
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

    function formatSentiment(score) {
        if (score === null || score === undefined) return '–';
        return score.toFixed(3);
    }

    function formatSentimentLabel(score) {
        if (score === null || score === undefined) return 'Kein Sentiment';
        if (score > 0.2) return 'Positiv';
        if (score < -0.2) return 'Negativ';
        return 'Neutral';
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
        elements.ingestionForm.addEventListener('submit', handleIngestionSubmit);
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
        if (state.ingestionHistory.length === 0) {
            elements.ingestionHistory.innerHTML = '<li class="muted">Noch keine Suche durchgeführt.</li>';
            return;
        }
        elements.ingestionHistory.innerHTML = state.ingestionHistory
            .map(q => `<li><button class="history-item" data-query="${encodeURIComponent(q)}">${q}</button></li>`)
            .join('');
        elements.ingestionHistory.querySelectorAll('.history-item').forEach(btn => {
            btn.addEventListener('click', () => {
                elements.searchQuery.value = decodeURIComponent(btn.dataset.query);
            });
        });
    }

    // ==================== SENTIMENT ====================

    function initSentiment() {
        elements.btnRunSentiment.addEventListener('click', handleRunSentiment);
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
        elements.btnRetryErrors.addEventListener('click', handleRetryErrors);
    }

    async function handleRetryErrors() {
        const limit = parseInt(elements.retryLimit.value) || 20;
        showLoading(elements.retryResult, true);
        try {
            const result = await api.retryErrorTracks(limit);
            const msg = `Versucht: ${result.attempted}, Neu gefunden: ${result.newlyFetched}, Weiterhin nicht gefunden: ${result.notFound}, Weiterhin Fehler: ${result.stillError}`;
            showResult(elements.retryResult, msg, false);
            showToast('Erneuter Ladeversuch abgeschlossen');
            loadDashboard();
            loadTracks();
        } catch (error) {
            showResult(elements.retryResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.retryResult, false);
        }
    }

    // ==================== METADATA BACKFILL (Genre/Jahr) ====================

    function initBackfillMetadata() {
        if (!elements.btnBackfillMetadata) return;
        elements.btnBackfillMetadata.addEventListener('click', handleBackfillMetadata);
    }

    async function handleBackfillMetadata() {
        const limit = parseInt(elements.backfillLimit.value) || 50;
        showLoading(elements.backfillResult, true);
        try {
            const result = await api.backfillMetadata(limit);
            const msg = `Geprüft: ${result.attempted}, Aktualisiert: ${result.updated}, Übersprungen: ${result.skipped}`;
            showResult(elements.backfillResult, msg, false);
            showToast('Genre-/Jahr-Nachladen abgeschlossen');
            loadTracks();
            loadStats();
        } catch (error) {
            showResult(elements.backfillResult, `Fehler: ${error.message}`, true);
        } finally {
            showLoading(elements.backfillResult, false);
        }
    }

    // ==================== TRACKS ====================

    function initTracks() {
        elements.btnApplyFilter.addEventListener('click', () => {
            state.tracks.page = 0;
            loadTracks();
        });
        elements.trackSearch.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') {
                state.tracks.page = 0;
                loadTracks();
            }
        });
        elements.btnPrevPage.addEventListener('click', () => {
            if (state.tracks.page > 0) {
                state.tracks.page--;
                loadTracks();
            }
        });
        elements.btnNextPage.addEventListener('click', () => {
            if (state.tracks.page < state.tracks.totalPages - 1) {
                state.tracks.page++;
                loadTracks();
            }
        });
    }

    async function loadTracks() {
        state.tracks.search = elements.trackSearch.value;
        state.tracks.statusFilter = elements.trackStatusFilter.value;
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

    function renderTracksTable() {
        if (state.tracks.data.length === 0) {
            elements.tracksTbody.innerHTML = '<tr><td colspan="8" class="muted">Keine Tracks gefunden</td></tr>';
            return;
        }
        elements.tracksTbody.innerHTML = state.tracks.data
            .map(track => `
                <tr data-track-id="${track.id}">
                    <td>${track.id}</td>
                    <td>${escapeHtml(track.artistName || '–')}</td>
                    <td>${escapeHtml(track.title || '–')}</td>
                    <td>${escapeHtml(track.albumName || '–')}</td>
                    <td><span class="status-badge ${track.lyricsStatus || 'PENDING'}">${track.lyricsStatus || 'PENDING'}</span></td>
                    <td>${escapeHtml(track.sentimentLabel || 'Kein Sentiment')}</td>
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
        elements.paginationInfo.textContent = `Seite ${state.tracks.page + 1} von ${state.tracks.totalPages || 1}`;
        elements.btnPrevPage.disabled = state.tracks.page === 0;
        elements.btnNextPage.disabled = state.tracks.page >= (state.tracks.totalPages - 1);
    }

    async function showTrackDetails(trackId) {
        try {
            const track = await api.getTrackById(trackId);
            const html = `
                <div class="modal-header">
                    <h2>${escapeHtml(track.artistName || 'Unbekannt')} – ${escapeHtml(track.title || 'Unbekannt')}</h2>
                </div>
                <div class="modal-body">
                    <div class="detail-grid">
                        <div class="detail-item"><strong>Album:</strong> ${escapeHtml(track.albumName || '–')}</div>
                        <div class="detail-item"><strong>Lyrics-Status:</strong> <span class="status-badge ${track.lyricsStatus || 'PENDING'}">${track.lyricsStatus || 'PENDING'}</span></div>
                        <div class="detail-item"><strong>Sentiment:</strong> ${escapeHtml(track.sentimentLabel || 'Kein Sentiment')} (${formatSentiment(track.sentimentScore)})</div>
                        <div class="detail-item"><strong>Genre:</strong> ${escapeHtml(track.genre || '–')}</div>
                        <div class="detail-item"><strong>Jahr:</strong> ${escapeHtml(track.releaseYear || '–')}</div>
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

    function renderGenreStats(stats) {
        if (!stats || stats.length === 0) {
            elements.statsGenre.innerHTML = '<p class="muted">Noch keine Genre-Daten verfügbar. Nutze "Songs laden" (neue Songs) oder "Genre/Jahr nachladen" (vorhandene Songs), um Genre-Daten von Deezer zu ergänzen.</p>';
            return;
        }
        const maxScore = Math.max(...stats.map(s => s.averageSentimentScore || 0), 0.0001);
        elements.statsGenre.innerHTML = stats
            .map(s => `
                <div class="stats-row">
                    <span class="stats-row-label">${escapeHtml(s.genre || 'Unbekannt')}</span>
                    <div class="stats-bar-track">
                        <div class="stats-bar-fill" style="width: ${Math.max(0, (s.averageSentimentScore || 0) / maxScore * 100)}%"></div>
                    </div>
                    <span class="stats-row-value">${formatSentiment(s.averageSentimentScore)} (${s.trackCount} Songs)</span>
                </div>
            `)
            .join('');
    }

    function renderYearStats(stats) {
        if (!stats || stats.length === 0) {
            elements.statsYear.innerHTML = '<p class="muted">Noch keine Jahr-Daten verfügbar. Nutze "Songs laden" (neue Songs) oder "Genre/Jahr nachladen" (vorhandene Songs), um Erscheinungsjahre von Deezer zu ergänzen.</p>';
            return;
        }
        const sorted = [...stats].sort((a, b) => (b.year || 0) - (a.year || 0));
        const maxScore = Math.max(...sorted.map(s => s.averageSentimentScore || 0), 0.0001);
        elements.statsYear.innerHTML = sorted
            .map(s => `
                <div class="stats-row">
                    <span class="stats-row-label">${s.year || 'Unbekannt'}</span>
                    <div class="stats-bar-track">
                        <div class="stats-bar-fill" style="width: ${Math.max(0, (s.averageSentimentScore || 0) / maxScore * 100)}%"></div>
                    </div>
                    <span class="stats-row-value">${formatSentiment(s.averageSentimentScore)} (${s.trackCount} Songs)</span>
                </div>
            `)
            .join('');
    }

    // ==================== MODAL ====================

    function initModal() {
        elements.modalCloseBtn.addEventListener('click', () => {
            elements.modalOverlay.classList.add('hidden');
        });
        elements.modalOverlay.addEventListener('click', (e) => {
            if (e.target === elements.modalOverlay) {
                elements.modalOverlay.classList.add('hidden');
            }
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !elements.modalOverlay.classList.contains('hidden')) {
                elements.modalOverlay.classList.add('hidden');
            }
        });
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
