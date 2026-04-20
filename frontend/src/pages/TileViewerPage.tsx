import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import ImageViewer from '../components/ImageViewer';
import '../styles/TileViewerPage.scss';

type DatasetSummary = {
  imageId: string;
  datasetName: string;
  totalObjects: number;
  totalSizeBytes: number;
  lastModifiedMillis: number;
};

type DatasetPage = {
  datasets: DatasetSummary[];
  nextContinuationToken?: string | null;
  appliedPrefix: string;
};

type AnalysisSummary = {
  total_tiles: number;
  tissue_tiles: number;
  skipped_tiles: number;
  flagged_tiles: number;
  tumor_area_percentage: number;
  aggregate_score: number;
  max_score: number;
};

type AnalysisJobRecord = {
  jobId: string;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  tilesProcessed: number;
  totalTiles: number;
  tumorAreaPercentage: number | null;
  aggregateScore: number | null;
  maxScore: number | null;
  heatmapKey: string | null;
  errorMessage: string | null;
};

const API_BASE_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

const TileViewerPage: React.FC = () => {
  const { imageId: routeImageId } = useParams<{ imageId?: string }>();
  const [activeImageId, setActiveImageId] = useState<string | null>(null);
  const [activeDatasetName, setActiveDatasetName] = useState<string | null>(null);
  const [viewerKey, setViewerKey] = useState(0);
  const [inputValue, setInputValue] = useState('');
  const [featuredDatasets, setFeaturedDatasets] = useState<DatasetSummary[]>([]);
  const [searchResults, setSearchResults] = useState<DatasetSummary[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const initialisedRef = useRef(false);
  const searchGroupRef = useRef<HTMLDivElement | null>(null);

  const suggestions = useMemo(() => {
    const source = searchTerm ? searchResults : featuredDatasets;
    return source.slice(0, 10);
  }, [featuredDatasets, searchResults, searchTerm]);

  const formatDatasetMeta = (dataset: DatasetSummary) => {
    const date = dataset.lastModifiedMillis ? new Date(dataset.lastModifiedMillis) : null;
    const mb = dataset.totalSizeBytes / (1024 * 1024);
    const sizeLabel = mb >= 1 ? `${mb.toFixed(1)} MB` : `${(dataset.totalSizeBytes / 1024).toFixed(0)} KB`;
    return `${sizeLabel} · ${dataset.totalObjects} files${date ? ` · ${date.toLocaleDateString()}` : ''}`;
  };

  // ── Analysis state ────────────────────────────────────────────────────────
  const [analysisJobId, setAnalysisJobId] = useState<string | null>(null);
  const [analysisStatus, setAnalysisStatus] = useState<string | null>(null);
  const [analysisProgress, setAnalysisProgress] = useState<{ done: number; total: number; message: string } | null>(null);
  const [analysisSummary, setAnalysisSummary] = useState<AnalysisSummary | null>(null);
  const [analysisPredictions, setAnalysisPredictions] = useState<any[]>([]);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [showOverlays, setShowOverlays] = useState(true);
  const [showHeatmap, setShowHeatmap] = useState(true);
  const [heatmapUrl, setHeatmapUrl] = useState<string | null>(null);
  const [heatmapWarning, setHeatmapWarning] = useState<string | null>(null);
  const [threshold, setThreshold] = useState(0.5);
  const [overlayOpacity, setOverlayOpacity] = useState(0.6);
  const [heatmapOpacity, setHeatmapOpacity] = useState(0.45);
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>('');

  // ── Fetch available classifier models once on mount ───────────────────────
  useEffect(() => {
    fetch(`${API_BASE_URL}/api/v1/analysis/models`)
      .then(r => r.ok ? r.json() : { models: [] })
      .then(data => setAvailableModels(Array.isArray(data.models) ? data.models : []))
      .catch(() => setAvailableModels([]));
  }, []);

  // ── Dataset helpers ───────────────────────────────────────────────────────
  const resetAnalysis = () => {
    setAnalysisJobId(null);
    setAnalysisStatus(null);
    setAnalysisProgress(null);
    setAnalysisSummary(null);
    setAnalysisPredictions([]);
    setHeatmapUrl(null);
    setHeatmapWarning(null);
    setIsAnalyzing(false);
  };

  const applyDataset = (dataset: DatasetSummary | null) => {
    if (!dataset) return;
    setActiveImageId(dataset.imageId);
    setActiveDatasetName(dataset.datasetName);
    setInputValue(dataset.datasetName || dataset.imageId);
    setViewerKey(prev => prev + 1);
    setShowSuggestions(false);
    setSearchTerm('');
    setSearchResults([]);
    setErrorMessage(null);
    resetAnalysis();
    fetchHistoryForImage(dataset.imageId);
  };

  const applyDatasetById = (imageId: string, fallbackLabel?: string) => {
    if (!imageId) return;
    setActiveImageId(imageId);
    setActiveDatasetName(fallbackLabel ?? null);
    setInputValue(fallbackLabel ?? imageId);
    setViewerKey(prev => prev + 1);
    setShowSuggestions(false);
    setSearchTerm('');
    setSearchResults([]);
    setErrorMessage(null);
    resetAnalysis();
    fetchHistoryForImage(imageId);
  };

  // ── Route param ───────────────────────────────────────────────────────────
  useEffect(() => {
    if (!routeImageId) return;
    initialisedRef.current = true;
    applyDatasetById(routeImageId);
  }, [routeImageId]);

  // ── Load featured datasets ────────────────────────────────────────────────
  useEffect(() => {
    const controller = new AbortController();
    const load = async () => {
      try {
        setIsLoading(true);
        setErrorMessage(null);
        const params = new URLSearchParams({ limit: '5' });
        const res = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, { signal: controller.signal });
        if (!res.ok) throw new Error('Failed to fetch datasets');
        const data: DatasetPage = await res.json();
        setFeaturedDatasets(data.datasets);
        if (!routeImageId && !initialisedRef.current && data.datasets.length > 0) {
          initialisedRef.current = true;
          applyDataset(data.datasets[0]);
        }
      } catch (err) {
        if (!(err instanceof DOMException && err.name === 'AbortError')) {
          setErrorMessage('Unable to load datasets.');
        }
      } finally {
        setIsLoading(false);
      }
    };
    load();
    return () => controller.abort();
  }, [routeImageId]);

  // ── Search ────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (!searchTerm) {
      setSearchResults([]);
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const params = new URLSearchParams({ limit: '10', prefix: searchTerm });
        const res = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, { signal: controller.signal });
        if (!res.ok) throw new Error('Failed to search');
        const data: DatasetPage = await res.json();
        setSearchResults(data.datasets);
      } catch (err) {
        if (!(err instanceof DOMException && err.name === 'AbortError')) {
          setErrorMessage('Unable to search datasets.');
        }
      }
    }, 250);
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [searchTerm]);

  // ── Click outside suggestions ─────────────────────────────────────────────
  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (searchGroupRef.current && !searchGroupRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
      }
    };
    window.addEventListener('mousedown', handleClick);
    return () => window.removeEventListener('mousedown', handleClick);
  }, []);

  const handleInputChange = (value: string) => {
    setInputValue(value);
    setSearchTerm(value.trim());
    setShowSuggestions(true);
  };

  const handleSubmit = () => {
    const trimmed = inputValue.trim();
    if (!trimmed) return;
    const allKnown = [...featuredDatasets, ...searchResults];
    const matched = allKnown.find(
      d => d.imageId.toLowerCase() === trimmed.toLowerCase() ||
           d.datasetName.toLowerCase() === trimmed.toLowerCase()
    );
    if (matched) { applyDataset(matched); return; }
    applyDatasetById(trimmed);
  };

  // ── Analysis ──────────────────────────────────────────────────────────────
  const triggerAnalysis = async () => {
    if (!activeImageId) return;
    try {
      setIsAnalyzing(true);
      setErrorMessage(null);
      setHeatmapWarning(null);
      setHeatmapUrl(null);
      setAnalysisSummary(null);
      const params = selectedModel ? `?modelName=${encodeURIComponent(selectedModel)}` : '';
      const res = await fetch(`${API_BASE_URL}/api/v1/analysis/trigger/${activeImageId}${params}`, { method: 'POST' });
      if (!res.ok) throw new Error('Failed to trigger analysis');
      const data = await res.json();
      setAnalysisJobId(data.job_id);
      setAnalysisStatus('accepted');
    } catch {
      setErrorMessage('Failed to start cancer analysis.');
      setIsAnalyzing(false);
    }
  };

  const fetchHistoryForImage = async (imageId: string) => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/v1/analysis/history/${imageId}`);
      if (!res.ok) return;
      const data: { jobs: AnalysisJobRecord[] } = await res.json();
      const latest = data.jobs.find(j => j.status === 'COMPLETED');
      if (!latest) return;
      setAnalysisJobId(latest.jobId);
      setAnalysisStatus('completed');
      setAnalysisSummary({
        total_tiles: latest.totalTiles,
        tissue_tiles: 0,
        skipped_tiles: 0,
        flagged_tiles: 0,
        tumor_area_percentage: latest.tumorAreaPercentage ?? 0,
        aggregate_score: latest.aggregateScore ?? 0,
        max_score: latest.maxScore ?? 0,
      });
      if (latest.heatmapKey) {
        setHeatmapUrl(`${API_BASE_URL}/api/v1/analysis/heatmap/${latest.jobId}`);
      }
    } catch {
      // non-fatal — viewer still works without history
    }
  };

  useEffect(() => {
    if (!analysisJobId || analysisStatus === 'completed' || analysisStatus === 'failed') return;
    const interval = setInterval(async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/api/v1/analysis/status/${analysisJobId}`);
        if (!res.ok) throw new Error('Status check failed');
        const data = await res.json();
        setAnalysisStatus(data.status);
        setAnalysisProgress({ done: data.tiles_processed, total: data.total_tiles, message: data.message });
        if (data.status === 'completed') {
          clearInterval(interval);
          fetchAnalysisResults(analysisJobId);
        } else if (data.status === 'failed') {
          clearInterval(interval);
          setIsAnalyzing(false);
          setErrorMessage(`Analysis failed: ${data.message}`);
        }
      } catch {
        // polling error — retry on next tick
      }
    }, 2000);
    return () => clearInterval(interval);
  }, [analysisJobId, analysisStatus]);

  const fetchAnalysisResults = async (jobId: string) => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/v1/analysis/results/${jobId}`);
      if (!res.ok) throw new Error('Results fetch failed');
      const data = await res.json();
      setAnalysisPredictions(Array.isArray(data.tile_predictions) ? data.tile_predictions : []);
      if (data.summary) setAnalysisSummary(data.summary);

      if (data.heatmap_key) {
        setHeatmapUrl(`${API_BASE_URL}/api/v1/analysis/heatmap/${jobId}`);
      } else {
        setHeatmapWarning('Heatmap unavailable. Showing region boxes only.');
      }
      setIsAnalyzing(false);
    } catch {
      setErrorMessage('Failed to load analysis results.');
      setIsAnalyzing(false);
    }
  };

  const analysisProgressPct =
    analysisProgress && analysisProgress.total > 0
      ? Math.round((analysisProgress.done / analysisProgress.total) * 100)
      : 0;

  const hasOverlayData = analysisPredictions.length > 0 || !!heatmapUrl;

  return (
    <div className="tvp">
      {/* ── Sidebar ── */}
      <aside className="tvp__sidebar">

        {/* Dataset search */}
        <section className="tvp__section">
          <span className="tvp__label">Dataset</span>
          <div className="tvp__search-group" ref={searchGroupRef}>
            <div className="tvp__search-row">
              <input
                className="tvp__input"
                type="text"
                value={inputValue}
                onChange={e => handleInputChange(e.target.value)}
                onFocus={() => setShowSuggestions(true)}
                onKeyDown={e => {
                  if (e.key === 'Enter') { e.preventDefault(); handleSubmit(); }
                  if (e.key === 'Escape') setShowSuggestions(false);
                }}
                placeholder="Search name or image ID"
                autoComplete="off"
              />
              <button
                className="tvp__load-btn"
                type="button"
                onClick={handleSubmit}
                disabled={!inputValue.trim()}
              >
                Load
              </button>
            </div>

            {showSuggestions && suggestions.length > 0 && (
              <ul className="tvp__suggestions" role="listbox">
                {suggestions.map(d => (
                  <li key={d.imageId}>
                    <button
                      type="button"
                      className="tvp__suggestion"
                      onMouseDown={e => e.preventDefault()}
                      onClick={() => applyDataset(d)}
                    >
                      <span className="tvp__suggestion-name">{d.datasetName || d.imageId}</span>
                      <span className="tvp__suggestion-meta">{formatDatasetMeta(d)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {isLoading && <span className="tvp__status-text">Loading datasets…</span>}
          {errorMessage && <span className="tvp__status-text tvp__status-text--error">{errorMessage}</span>}
          {heatmapWarning && <span className="tvp__status-text tvp__status-text--warn">{heatmapWarning}</span>}

          {activeImageId && (
            <p className="tvp__current">
              <span className="tvp__current-label">Active: </span>
              <code className="tvp__current-id">{activeDatasetName || activeImageId}</code>
            </p>
          )}
        </section>

        {/* Analysis trigger */}
        <section className="tvp__section">
          {availableModels.length > 0 && (
            <label className="tvp__slider-label">
              <span>Classifier model</span>
              <select
                className="tvp__select"
                value={selectedModel}
                onChange={e => setSelectedModel(e.target.value)}
                disabled={isAnalyzing}
              >
                <option value="">Default</option>
                {availableModels.map(m => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </label>
          )}
          <button
            className={`tvp__analyze-btn${isAnalyzing ? ' tvp__analyze-btn--busy' : ''}`}
            onClick={triggerAnalysis}
            disabled={!activeImageId || isAnalyzing}
            type="button"
          >
            {isAnalyzing ? 'Analyzing…' : 'Run Cancer Analysis'}
          </button>

          {isAnalyzing && analysisProgress && (
            <div className="tvp__analysis-progress">
              <div className="tvp__ap-bar">
                <div className="tvp__ap-fill" style={{ width: `${analysisProgressPct}%` }} />
              </div>
              <div className="tvp__ap-meta">
                <span>{analysisProgress.message}</span>
                <span>{analysisProgress.done} / {analysisProgress.total}</span>
              </div>
            </div>
          )}
        </section>

        {/* Analysis results summary */}
        {analysisSummary && (
          <section className="tvp__section tvp__results">
            <span className="tvp__label">Analysis Results</span>

            <div className="tvp__stat-big">
              <span className="tvp__stat-big-value">
                {analysisSummary.tumor_area_percentage.toFixed(1)}
                <span className="tvp__stat-big-unit">%</span>
              </span>
              <span className="tvp__stat-big-label">Tumor area</span>
            </div>

            <div className="tvp__stat-row">
              <div className="tvp__stat">
                <span className="tvp__stat-label">Max score</span>
                <span className="tvp__stat-value">{analysisSummary.max_score.toFixed(3)}</span>
              </div>
              <div className="tvp__stat">
                <span className="tvp__stat-label">Avg score</span>
                <span className="tvp__stat-value">{analysisSummary.aggregate_score.toFixed(3)}</span>
              </div>
            </div>

            <div className="tvp__stat-row">
              <div className="tvp__stat">
                <span className="tvp__stat-label">Tissue tiles</span>
                <span className="tvp__stat-value">{analysisSummary.tissue_tiles.toLocaleString()}</span>
              </div>
              <div className="tvp__stat">
                <span className="tvp__stat-label">Flagged</span>
                <span className="tvp__stat-value">{analysisSummary.flagged_tiles.toLocaleString()}</span>
              </div>
            </div>
          </section>
        )}

        {/* Overlay controls */}
        {hasOverlayData && (
          <section className="tvp__section">
            <span className="tvp__label">Overlay Controls</span>

            <label className="tvp__slider-label">
              <span>Threshold: {threshold.toFixed(2)}</span>
              <input type="range" min="0" max="1" step="0.01" value={threshold}
                onChange={e => setThreshold(parseFloat(e.target.value))} />
            </label>

            <label className="tvp__slider-label">
              <span>Box opacity: {overlayOpacity.toFixed(2)}</span>
              <input type="range" min="0" max="1" step="0.01" value={overlayOpacity}
                onChange={e => setOverlayOpacity(parseFloat(e.target.value))} />
            </label>

            <label className="tvp__slider-label">
              <span>Heatmap opacity: {heatmapOpacity.toFixed(2)}</span>
              <input type="range" min="0" max="1" step="0.01" value={heatmapOpacity}
                onChange={e => setHeatmapOpacity(parseFloat(e.target.value))} />
            </label>

            <div className="tvp__toggles">
              <label className="tvp__toggle">
                <input type="checkbox" checked={showOverlays} onChange={e => setShowOverlays(e.target.checked)} />
                <span>Region boxes</span>
              </label>
              <label className="tvp__toggle">
                <input type="checkbox" checked={showHeatmap} onChange={e => setShowHeatmap(e.target.checked)} />
                <span>Heatmap</span>
              </label>
            </div>
          </section>
        )}
      </aside>

      {/* ── Viewer ── */}
      <main className="tvp__viewer">
        {activeImageId ? (
          <ImageViewer
            key={`${activeImageId}-${viewerKey}`}
            imageId={activeImageId}
            overlays={showOverlays ? analysisPredictions : []}
            threshold={threshold}
            overlayOpacity={overlayOpacity}
            heatmapUrl={heatmapUrl ?? undefined}
            showHeatmap={showHeatmap}
            heatmapOpacity={heatmapOpacity}
          />
        ) : (
          <div className="tvp__placeholder">
            <p>Select a dataset to initialize the viewer.</p>
          </div>
        )}
      </main>
    </div>
  );
};

export default TileViewerPage;
