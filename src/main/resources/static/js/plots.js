/**
 * Lyrics Analyzer – Plot-Visualisierungen mit Plotly.js
 *
 * Enthält 5 Plot-Typen:
 *   1. SPLOM (Scatter Matrix)      – Style-Features pro Track, Farbe = Genre
 *   2. Parallel Coordinates        – alle Features gleichzeitig pro Künstler
 *   3. Violin / Box                – Feature-Verteilung nach Sentiment-Label
 *   4. Heatmap                     – Korrelationsmatrix der Style-Features
 *   5. Bubble Chart (Theme)        – uniqueWordRatio vs. rhymeDensity, Farbe = Theme
 *
 * Datenquellen (neue Backend-Endpoints):
 *   GET /api/plot-data/tracks          – Track-Level-Features
 *   GET /api/plot-data/artists         – Künstler-Level-Features (aggregiert)
 *   GET /api/plot-data/sentiment-by-genre
 *   GET /api/plot-data/theme-features
 */

(function () {
    'use strict';

    // ==================== DARK THEME CONFIG ====================

    const DARK_LAYOUT = {
        paper_bgcolor: 'rgba(0,0,0,0)',
        plot_bgcolor:  'rgba(30,41,59,0.6)',   // --surface-color
        font:          { color: '#94a3b8', family: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif', size: 11 },
        colorway:      ['#6366f1','#22c55e','#f59e0b','#ef4444','#3b82f6','#ec4899','#14b8a6','#f97316','#a855f7','#84cc16'],
        xaxis:         { gridcolor: '#334155', zerolinecolor: '#475569', linecolor: '#475569' },
        yaxis:         { gridcolor: '#334155', zerolinecolor: '#475569', linecolor: '#475569' },
        legend:        { bgcolor: 'rgba(30,41,59,0.8)', bordercolor: '#334155', borderwidth: 1 },
        margin:        { l: 60, r: 20, t: 50, b: 60 },
    };

    const PLOTLY_CONFIG = {
        responsive:        true,
        displayModeBar:    true,
        modeBarButtonsToRemove: ['toImage', 'sendDataToCloud'],
        displaylogo:       false,
    };

    // Genre → Farbe (konsistent mit DNA-Tab)
    const GENRE_COLORS = {
        'Rock':      '#e74c3c',
        'Pop':       '#3498db',
        'Schlager':  '#f39c12',
        'Rap':       '#9b59b6',
        'Unbekannt': '#95a5a6',
    };

    // Sentiment-Label → Farbe
    const SENTIMENT_COLORS = {
        'VERY_NEGATIVE': '#ef4444',
        'NEGATIVE':      '#f97316',
        'NEUTRAL':       '#94a3b8',
        'POSITIVE':      '#22c55e',
        'VERY_POSITIVE': '#14b8a6',
    };

    // Feature-Labels (Anzeigenamen)
    const FEATURE_LABELS = {
        avgWordLength:       'Ø Wortlänge',
        rhymeDensity:        'Reimdichte',
        uniqueWordRatio:     'Unique-Wort-Anteil',
        avgLineLength:       'Ø Zeilenlänge',
        exclamationDensity:  'Ausrufezeichen-Dichte',
        questionMarkDensity: 'Fragezeichen-Dichte',
        capitalWordRatio:    'Großbuchstaben-Anteil',
        lineCount:           'Zeilenanzahl',
        sentimentScore:      'Sentiment (0-100)',
    };

    // Die 6 wichtigsten Features für die SPLOM (zu viele machen sie unleserlich)
    const SPLOM_FEATURES = [
        'avgWordLength', 'rhymeDensity', 'uniqueWordRatio',
        'avgLineLength', 'capitalWordRatio', 'sentimentScore'
    ];

    // Alle 8 Style-Features für Parallel Coordinates
    const PARALLEL_FEATURES = [
        'avgWordLength', 'rhymeDensity', 'uniqueWordRatio', 'avgLineLength',
        'exclamationDensity', 'questionMarkDensity', 'capitalWordRatio', 'sentimentScore'
    ];

    // ==================== UTILITIES ====================

    function showPlotLoading(containerId) {
        const el = document.getElementById(containerId);
        if (el) {
            el.innerHTML = `
                <div style="display:flex;align-items:center;justify-content:center;height:300px;gap:1rem;color:var(--text-secondary);">
                    <div class="spinner"></div>
                    <span>Lade Daten und berechne Features …</span>
                </div>`;
        }
    }

    function showPlotError(containerId, msg) {
        const el = document.getElementById(containerId);
        if (el) {
            el.innerHTML = `<div style="padding:1.5rem;color:var(--error-color);">⚠ ${msg}</div>`;
        }
    }

    function groupBy(arr, key) {
        return arr.reduce((acc, item) => {
            const k = item[key] ?? 'Unbekannt';
            (acc[k] = acc[k] || []).push(item);
            return acc;
        }, {});
    }

    function categorizeGenre(genre) {
        if (!genre) return 'Unbekannt';
        const g = genre.toLowerCase();
        if (g.includes('rock') || g.includes('metal') || g.includes('punk') || g.includes('alternative')) return 'Rock';
        if (g.includes('rap') || g.includes('hip')) return 'Rap';
        if (g.includes('schlager') || g.includes('volksmusik')) return 'Schlager';
        if (g.includes('pop') || g.includes('dance') || g.includes('disco') || g.includes('electro')) return 'Pop';
        return 'Sonstige';
    }

    function colorForGenre(genre) {
        const cat = categorizeGenre(genre);
        return GENRE_COLORS[cat] || GENRE_COLORS['Unbekannt'];
    }

    async function fetchPlotData(endpoint) {
        const resp = await fetch(`/api/plot-data/${endpoint}`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        return resp.json();
    }

    // Pearson-Korrelation zweier Arrays
    function pearsonCorrelation(x, y) {
        const n = x.length;
        if (n === 0) return 0;
        const meanX = x.reduce((a, b) => a + b, 0) / n;
        const meanY = y.reduce((a, b) => a + b, 0) / n;
        let num = 0, denX = 0, denY = 0;
        for (let i = 0; i < n; i++) {
            const dx = x[i] - meanX, dy = y[i] - meanY;
            num  += dx * dy;
            denX += dx * dx;
            denY += dy * dy;
        }
        return (denX === 0 || denY === 0) ? 0 : num / Math.sqrt(denX * denY);
    }

    // ==================== PLOT 1: SPLOM ====================

    async function renderSPLOM() {
        showPlotLoading('plot-splom');
        try {
            const data = await fetchPlotData('tracks');
            if (data.length === 0) {
                showPlotError('plot-splom', 'Keine Track-Daten mit Lyrics verfügbar.');
                return;
            }

            // Filtere auf Tracks mit Sentiment (sonst fehlen Punkte in sentiment-Spalten)
            const valid = data.filter(d =>
                SPLOM_FEATURES.every(f => d[f] !== null && d[f] !== undefined)
            );

            if (valid.length === 0) {
                showPlotError('plot-splom', 'Zu wenige Tracks mit vollständigen Feature-Daten. Bitte zuerst Sentiment-Analyse starten.');
                return;
            }

            // Gruppen nach Genre-Kategorie
            const byGenre = groupBy(valid, d => categorizeGenre(d.genre));
            const traces = Object.entries(byGenre).map(([genre, points]) => ({
                type: 'splom',
                name: genre,
                marker: {
                    color: GENRE_COLORS[genre] || '#95a5a6',
                    size: 5,
                    opacity: 0.75,
                    line: { width: 0.5, color: 'rgba(0,0,0,0.3)' },
                },
                dimensions: SPLOM_FEATURES.map(f => ({
                    label: FEATURE_LABELS[f],
                    values: points.map(p => p[f]),
                })),
                text: points.map(p => `${p.artist}<br>${p.title}`),
                hovertemplate: '%{text}<extra></extra>',
                showupperhalf: false,
                diagonal: { visible: true },
            }));

            const layout = {
                ...DARK_LAYOUT,
                title:  { text: 'Scatter Matrix – Style-Features nach Genre', font: { color: '#f8fafc', size: 14 } },
                height: Math.max(500, SPLOM_FEATURES.length * 130),
                margin: { l: 120, r: 20, t: 60, b: 120 },
                dragmode: 'select',
            };

            Plotly.newPlot('plot-splom', traces, layout, PLOTLY_CONFIG);
        } catch (e) {
            showPlotError('plot-splom', `Fehler: ${e.message}`);
        }
    }

    // ==================== PLOT 2: PARALLEL COORDINATES ====================

    async function renderParallelCoordinates() {
        showPlotLoading('plot-parallel');
        try {
            const data = await fetchPlotData('artists');
            if (data.length === 0) {
                showPlotError('plot-parallel', 'Keine Künstler-Daten verfügbar.');
                return;
            }

            const valid = data.filter(d =>
                PARALLEL_FEATURES.every(f => d[f] !== null && d[f] !== undefined)
            );

            if (valid.length < 2) {
                showPlotError('plot-parallel', 'Zu wenige Künstler mit vollständigen Daten. Sentiment-Analyse starten!');
                return;
            }

            // Genre-Kategorien → numerisch für colorscale
            const genreCategories = ['Rock', 'Pop', 'Schlager', 'Rap', 'Sonstige'];
            const genreToNum = Object.fromEntries(genreCategories.map((g, i) => [g, i]));
            const colorValues = valid.map(d => genreToNum[categorizeGenre(d.genre)] ?? 4);

            const dimensions = PARALLEL_FEATURES.map(f => {
                const values = valid.map(d => d[f] ?? 0);
                return {
                    label:  FEATURE_LABELS[f],
                    values: values,
                    range:  [Math.min(...values) * 0.95, Math.max(...values) * 1.05],
                };
            });

            const trace = {
                type: 'parcoords',
                line: {
                    color:      colorValues,
                    colorscale: [
                        [0.0,  '#e74c3c'],   // Rock
                        [0.25, '#3498db'],   // Pop
                        [0.5,  '#f39c12'],   // Schlager
                        [0.75, '#9b59b6'],   // Rap
                        [1.0,  '#95a5a6'],   // Sonstige
                    ],
                    showscale:      true,
                    colorbar: {
                        tickvals:  [0, 1, 2, 3, 4],
                        ticktext:  genreCategories,
                        title:     { text: 'Genre', font: { color: '#94a3b8' } },
                        tickfont:  { color: '#94a3b8' },
                        outlinecolor: '#334155',
                        bgcolor:  'rgba(30,41,59,0.8)',
                        len:       0.6,
                    },
                    opacity: 0.8,
                    width:   2,
                },
                dimensions,
                customdata: valid.map(d => d.artist),
                hoveron:    'color',
            };

            const layout = {
                ...DARK_LAYOUT,
                title:  { text: 'Parallel Coordinates – Style-Profil je Künstler', font: { color: '#f8fafc', size: 14 } },
                height: 500,
                margin: { l: 80, r: 80, t: 60, b: 40 },
            };

            Plotly.newPlot('plot-parallel', [trace], layout, PLOTLY_CONFIG);
        } catch (e) {
            showPlotError('plot-parallel', `Fehler: ${e.message}`);
        }
    }

    // ==================== PLOT 3: VIOLIN PLOTS (Sentiment-Label) ====================

    async function renderViolinPlots() {
        showPlotLoading('plot-violin');
        try {
            const data = await fetchPlotData('tracks');
            const valid = data.filter(d => d.sentimentLabel && d.sentimentScore !== null);

            if (valid.length === 0) {
                showPlotError('plot-violin', 'Keine Tracks mit Sentiment-Daten. Bitte Sentiment-Analyse starten.');
                return;
            }

            // Feature das der User per Dropdown wählt (Default: uniqueWordRatio)
            const selectedFeature = document.getElementById('violin-feature-select')?.value || 'uniqueWordRatio';

            const sentimentOrder = ['VERY_NEGATIVE','NEGATIVE','NEUTRAL','POSITIVE','VERY_POSITIVE'];
            const sentimentLabels = {
                VERY_NEGATIVE: 'Sehr Negativ',
                NEGATIVE:      'Negativ',
                NEUTRAL:       'Neutral',
                POSITIVE:      'Positiv',
                VERY_POSITIVE: 'Sehr Positiv',
            };

            const byLabel = groupBy(valid, 'sentimentLabel');

            const traces = sentimentOrder
                .filter(label => byLabel[label] && byLabel[label].length >= 3)
                .map(label => ({
                    type:    'violin',
                    name:    sentimentLabels[label],
                    y:       byLabel[label].map(d => d[selectedFeature]),
                    box:     { visible: true },
                    meanline:{ visible: true },
                    points:  'outliers',
                    marker:  { color: SENTIMENT_COLORS[label], size: 4, opacity: 0.6 },
                    line:    { color: SENTIMENT_COLORS[label] },
                    fillcolor: SENTIMENT_COLORS[label] + '44',
                    text:    byLabel[label].map(d => `${d.artist} – ${d.title}`),
                    hovertemplate: '%{text}<br>' + FEATURE_LABELS[selectedFeature] + ': %{y:.3f}<extra></extra>',
                }));

            if (traces.length === 0) {
                showPlotError('plot-violin', 'Zu wenige Daten pro Sentiment-Klasse (mind. 3 nötig).');
                return;
            }

            const layout = {
                ...DARK_LAYOUT,
                title: {
                    text: `Violin-Plot: ${FEATURE_LABELS[selectedFeature]} nach Sentiment-Klasse`,
                    font: { color: '#f8fafc', size: 14 }
                },
                yaxis: { ...DARK_LAYOUT.yaxis, title: { text: FEATURE_LABELS[selectedFeature], font: { color: '#94a3b8' } } },
                height: 450,
                showlegend: false,
            };

            Plotly.newPlot('plot-violin', traces, layout, PLOTLY_CONFIG);
        } catch (e) {
            showPlotError('plot-violin', `Fehler: ${e.message}`);
        }
    }

    // ==================== PLOT 4: KORRELATIONS-HEATMAP ====================

    async function renderCorrelationHeatmap() {
        showPlotLoading('plot-heatmap');
        try {
            const data = await fetchPlotData('tracks');
            const featureKeys = Object.keys(FEATURE_LABELS);
            const valid = data.filter(d =>
                featureKeys.filter(f => f !== 'lineCount').every(f => d[f] !== null && d[f] !== undefined)
            );

            if (valid.length < 5) {
                showPlotError('plot-heatmap', 'Zu wenige Tracks für Korrelationsmatrix.');
                return;
            }

            const features = featureKeys.filter(f => f !== 'lineCount' &&
                valid.some(d => d[f] !== null && d[f] !== undefined));

            const n = features.length;
            const matrix = Array.from({length: n}, () => new Array(n).fill(0));
            const vectors = features.map(f => valid.map(d => d[f] ?? 0));

            for (let i = 0; i < n; i++) {
                for (let j = 0; j < n; j++) {
                    matrix[i][j] = i === j ? 1.0 : pearsonCorrelation(vectors[i], vectors[j]);
                }
            }

            const labels = features.map(f => FEATURE_LABELS[f]);

            // Annotationstext für jede Zelle
            const annotations = [];
            for (let i = 0; i < n; i++) {
                for (let j = 0; j < n; j++) {
                    annotations.push({
                        x: j, y: i,
                        text: matrix[i][j].toFixed(2),
                        font: { size: 10, color: Math.abs(matrix[i][j]) > 0.5 ? '#fff' : '#94a3b8' },
                        showarrow: false,
                    });
                }
            }

            const trace = {
                type:        'heatmap',
                z:           matrix,
                x:           labels,
                y:           labels,
                colorscale:  [
                    [0.0,  '#ef4444'],
                    [0.25, '#f97316'],
                    [0.5,  '#1e293b'],
                    [0.75, '#3b82f6'],
                    [1.0,  '#6366f1'],
                ],
                zmid:        0,
                zmin:        -1,
                zmax:        1,
                colorbar: {
                    title:    { text: 'Pearson r', font: { color: '#94a3b8' } },
                    tickfont: { color: '#94a3b8' },
                    outlinecolor: '#334155',
                    bgcolor:  'rgba(30,41,59,0.8)',
                },
                hovertemplate: '%{y}<br>%{x}<br>r = %{z:.3f}<extra></extra>',
            };

            const layout = {
                ...DARK_LAYOUT,
                title:       { text: 'Korrelationsmatrix – Style-Features', font: { color: '#f8fafc', size: 14 } },
                annotations: annotations,
                height:      520,
                xaxis:       { ...DARK_LAYOUT.xaxis, tickangle: -35, automargin: true },
                yaxis:       { ...DARK_LAYOUT.yaxis, automargin: true },
                margin:      { l: 160, r: 80, t: 60, b: 160 },
            };

            Plotly.newPlot('plot-heatmap', [trace], layout, PLOTLY_CONFIG);
        } catch (e) {
            showPlotError('plot-heatmap', `Fehler: ${e.message}`);
        }
    }

    // ==================== PLOT 5: BUBBLE CHART (Theme) ====================

    async function renderThemeBubble() {
        showPlotLoading('plot-theme-bubble');
        try {
            const data = await fetchPlotData('theme-features');
            if (data.length === 0) {
                showPlotError('plot-theme-bubble', 'Keine gelabelten Tracks. Bitte im Tracks-Tab Themes vergeben.');
                return;
            }

            const byTheme = groupBy(data, 'theme');
            const traces = Object.entries(byTheme).map(([theme, points]) => ({
                type: 'scatter',
                mode: 'markers',
                name: theme,
                x:    points.map(p => p.uniqueWordRatio),
                y:    points.map(p => p.rhymeDensity),
                marker: {
                    size:    points.map(p => Math.max(6, (p.sentimentScore ?? 50) / 5)),
                    opacity: 0.75,
                    line:    { width: 0.5, color: 'rgba(0,0,0,0.3)' },
                    sizemode:  'diameter',
                    sizeref:   1,
                },
                text: points.map(p =>
                    `${p.artist}<br>${p.title}<br>Sentiment: ${p.sentimentScore ?? '–'}`
                ),
                hovertemplate: '%{text}<extra>' + theme + '</extra>',
            }));

            const layout = {
                ...DARK_LAYOUT,
                title: {
                    text: 'Bubble Chart – Themes (X: Unique-Wort-Anteil, Y: Reimdichte, Größe: Sentiment)',
                    font: { color: '#f8fafc', size: 13 }
                },
                xaxis: { ...DARK_LAYOUT.xaxis, title: { text: FEATURE_LABELS.uniqueWordRatio, font: { color: '#94a3b8' } } },
                yaxis: { ...DARK_LAYOUT.yaxis, title: { text: FEATURE_LABELS.rhymeDensity,   font: { color: '#94a3b8' } } },
                height: 500,
                legend: {
                    ...DARK_LAYOUT.legend,
                    title: { text: 'Theme', font: { color: '#94a3b8' } },
                },
            };

            Plotly.newPlot('plot-theme-bubble', traces, layout, PLOTLY_CONFIG);
        } catch (e) {
            showPlotError('plot-theme-bubble', `Fehler: ${e.message}`);
        }
    }

    // ==================== PLOT 6: SENTIMENT-TREND (Jahr) ====================

    async function renderSentimentTrend() {
        showPlotLoading('plot-sentiment-trend');
        try {
            const data = await fetchPlotData('sentiment-by-genre');
            const tracksWithYear = data.filter(d => d.releaseYear && d.sentimentScore !== null);

            if (tracksWithYear.length === 0) {
                showPlotError('plot-sentiment-trend', 'Keine Tracks mit Erscheinungsjahr. Metadaten über Dashboard → "Metadaten nachladen" ergänzen.');
                return;
            }

            const byGenre = groupBy(tracksWithYear, d => categorizeGenre(d.genre));

            const traces = Object.entries(byGenre).map(([genre, points]) => {
                // Aggregiere nach Jahr
                const byYear = groupBy(points, 'releaseYear');
                const years = Object.keys(byYear).map(Number).sort((a, b) => a - b);
                const avgScores = years.map(y => {
                    const vals = byYear[y].map(d => d.sentimentScore);
                    return vals.reduce((a, b) => a + b, 0) / vals.length;
                });
                const counts = years.map(y => byYear[y].length);

                return {
                    type: 'scatter',
                    mode: 'lines+markers',
                    name: genre,
                    x:    years,
                    y:    avgScores,
                    marker: {
                        color: GENRE_COLORS[genre] || '#95a5a6',
                        size:  counts.map(c => Math.min(20, Math.max(6, Math.sqrt(c) * 3))),
                        sizemode: 'diameter',
                    },
                    line: { color: GENRE_COLORS[genre] || '#95a5a6', width: 2 },
                    text: years.map((y, i) => `${genre} ${y}<br>n=${counts[i]}<br>Sentiment: ${avgScores[i].toFixed(1)}`),
                    hovertemplate: '%{text}<extra></extra>',
                };
            });

            const layout = {
                ...DARK_LAYOUT,
                title: {
                    text: 'Sentiment-Trend nach Erscheinungsjahr & Genre',
                    font: { color: '#f8fafc', size: 14 }
                },
                xaxis: { ...DARK_LAYOUT.xaxis, title: { text: 'Erscheinungsjahr', font: { color: '#94a3b8' } }, dtick: 2 },
                yaxis: { ...DARK_LAYOUT.yaxis, title: { text: 'Ø Sentiment (0–100)', font: { color: '#94a3b8' } }, range: [0, 100] },
                height: 420,
            };

            Plotly.newPlot('plot-sentiment-trend', traces, layout, PLOTLY_CONFIG);
        } catch (e) {
            showPlotError('plot-sentiment-trend', `Fehler: ${e.message}`);
        }
    }

    // ==================== PLOT 7: GENRE-FEATURE-RADAR ====================

    async function renderGenreRadar() {
        showPlotLoading('plot-genre-radar');
        try {
            const data = await fetchPlotData('artists');
            if (data.length === 0) {
                showPlotError('plot-genre-radar', 'Keine Künstler-Daten verfügbar.');
                return;
            }

            const radarFeatures = ['avgWordLength','rhymeDensity','uniqueWordRatio','avgLineLength','capitalWordRatio'];
            const radarLabels   = radarFeatures.map(f => FEATURE_LABELS[f]);

            // Normalize each feature to 0–1 across all artists for comparable radar
            const mins = Object.fromEntries(radarFeatures.map(f => [f, Math.min(...data.map(d => d[f] ?? 0))]));
            const maxs = Object.fromEntries(radarFeatures.map(f => [f, Math.max(...data.map(d => d[f] ?? 0))]));
            const norm = (d, f) => {
                const range = maxs[f] - mins[f];
                return range === 0 ? 0 : ((d[f] ?? 0) - mins[f]) / range;
            };

            // Aggregiere nach Genre-Kategorie
            const byGenre = groupBy(data, d => categorizeGenre(d.genre));

            const traces = Object.entries(byGenre).map(([genre, artists]) => {
                const avgValues = radarFeatures.map(f => {
                    const vals = artists.map(d => norm(d, f));
                    return vals.reduce((a, b) => a + b, 0) / vals.length;
                });
                // Radar braucht geschlossene Linie → ersten Wert wiederholen
                return {
                    type: 'scatterpolar',
                    name: genre,
                    r:    [...avgValues, avgValues[0]],
                    theta:[...radarLabels, radarLabels[0]],
                    fill:  'toself',
                    fillcolor: (GENRE_COLORS[genre] || '#95a5a6') + '33',
                    line:  { color: GENRE_COLORS[genre] || '#95a5a6', width: 2 },
                    marker:{ size: 5 },
                    hovertemplate: '%{theta}: %{r:.2f}<extra>' + genre + '</extra>',
                };
            });

            const layout = {
                ...DARK_LAYOUT,
                polar: {
                    bgcolor: 'rgba(30,41,59,0.6)',
                    radialaxis: {
                        visible:   true,
                        range:     [0, 1],
                        gridcolor: '#334155',
                        linecolor: '#475569',
                        tickfont:  { color: '#94a3b8', size: 9 },
                        tickformat: '.1f',
                    },
                    angularaxis: {
                        gridcolor: '#334155',
                        linecolor: '#475569',
                        tickfont:  { color: '#94a3b8', size: 10 },
                    },
                },
                title: {
                    text: 'Radar-Chart – Ø Style-Profil nach Genre (normalisiert)',
                    font: { color: '#f8fafc', size: 14 }
                },
                height: 480,
                margin: { l: 60, r: 60, t: 80, b: 60 },
            };

            Plotly.newPlot('plot-genre-radar', traces, layout, PLOTLY_CONFIG);
        } catch (e) {
            showPlotError('plot-genre-radar', `Fehler: ${e.message}`);
        }
    }

    // ==================== ALLE PLOTS RENDERN ====================

    async function renderAllPlots() {
        const btn = document.getElementById('btn-render-plots');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Berechne …';
        }

        // Parallel rendern für bessere UX
        await Promise.all([
            renderSPLOM(),
            renderParallelCoordinates(),
            renderViolinPlots(),
            renderCorrelationHeatmap(),
            renderThemeBubble(),
            renderSentimentTrend(),
            renderGenreRadar(),
        ]);

        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Plots neu laden';
        }
    }

    // ==================== INIT ====================

    function init() {
        const btn = document.getElementById('btn-render-plots');
        if (btn) btn.addEventListener('click', renderAllPlots);

        // Violin Feature-Selector
        const violinSelect = document.getElementById('violin-feature-select');
        if (violinSelect) violinSelect.addEventListener('change', renderViolinPlots);
    }

    // Export für app.js
    window.LyricsPlots = { init, renderAllPlots };

})();
