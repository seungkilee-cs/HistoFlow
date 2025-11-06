import React, { useEffect, useMemo, useRef, useState } from 'react';
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

const API_BASE_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

const TileViewerPage: React.FC = () => {
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
    const size = dataset.totalSizeBytes;
    const megabytes = size / (1024 * 1024);
    const sizeLabel = megabytes >= 1 ? `${megabytes.toFixed(1)} MB` : `${(size / 1024).toFixed(0)} KB`;
    return `${sizeLabel} · ${dataset.totalObjects} files${date ? ` · ${date.toLocaleString()}` : ''}`;
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
  };

  useEffect(() => {
    const controller = new AbortController();
    const loadFeatured = async () => {
      try {
        setIsLoading(true);
        setErrorMessage(null);
        const params = new URLSearchParams({ limit: '5' });
        const response = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, {
          signal: controller.signal
        });
        if (!response.ok) {
          throw new Error('Failed to fetch datasets');
        }
        const data: DatasetPage = await response.json();
        setFeaturedDatasets(data.datasets);
        if (!initialisedRef.current && data.datasets.length > 0) {
          initialisedRef.current = true;
          applyDataset(data.datasets[0]);
        }
      } catch (error) {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setErrorMessage('Unable to load datasets.');
        }
      } finally {
        setIsLoading(false);
      }
    };

    loadFeatured();
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!searchTerm) {
      setSearchResults([]);
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        setErrorMessage(null);
        const params = new URLSearchParams({ limit: '10', prefix: searchTerm });
        const response = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, {
          signal: controller.signal
        });
        if (!response.ok) {
          throw new Error('Failed to fetch datasets');
        }
        const data: DatasetPage = await response.json();
        setSearchResults(data.datasets);
      } catch (error) {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setErrorMessage('Unable to search datasets.');
        }
      }
    }, 250);

    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [searchTerm]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (!searchGroupRef.current) {
        return;
      }
      if (!searchGroupRef.current.contains(event.target as Node)) {
        setShowSuggestions(false);
      }
    };

    window.addEventListener('mousedown', handleClickOutside);
    return () => window.removeEventListener('mousedown', handleClickOutside);
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
    const matched = allKnown.find(dataset =>
      dataset.imageId.toLowerCase() === trimmed.toLowerCase() ||
      dataset.datasetName.toLowerCase() === trimmed.toLowerCase()
    );

    if (matched) {
      applyDataset(matched);
      return;
    }

    applyDatasetById(trimmed);
  };

  const visibleImageId = activeImageId ?? '';
  const visibleDatasetName = activeDatasetName ?? inputValue ?? '';

  return (
    <div className="tile-viewer-page">
      <div className="tile-viewer-page__header">
        <h1 className="tile-viewer-page__title">HistoFlow Tile Viewer</h1>
      </div>

      <div className="tile-viewer-page__control-bar">
        <div className="tile-viewer-page__search-group" ref={searchGroupRef}>
          <label className="tile-viewer-page__label" htmlFor="tile-viewer-image-id">
            Dataset
          </label>
          <div className="tile-viewer-page__search">
            <input
              id="tile-viewer-image-id"
              className="tile-viewer-page__input"
              type="text"
              value={inputValue}
              onChange={(e) => handleInputChange(e.target.value)}
              onFocus={() => setShowSuggestions(true)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  handleSubmit();
                }
                if (event.key === 'Escape') {
                  setShowSuggestions(false);
                }
              }}
              placeholder="Search by dataset name or image ID"
              autoComplete="off"
            />
            <button
              className="tile-viewer-page__submit"
              type="button"
              onClick={handleSubmit}
              disabled={!inputValue.trim()}
            >
              Load
            </button>
          </div>
          {showSuggestions && suggestions.length > 0 && (
            <ul className="tile-viewer-page__suggestions" role="listbox">
              {suggestions.map(dataset => (
                <li key={dataset.imageId}>
                  <button
                    type="button"
                    className="tile-viewer-page__suggestion"
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => applyDataset(dataset)}
                  >
                    <span className="tile-viewer-page__suggestion-title">
                      {dataset.datasetName || dataset.imageId}
                    </span>
                    <span className="tile-viewer-page__suggestion-meta">
                      {dataset.datasetName && dataset.datasetName !== dataset.imageId ? `${dataset.imageId} · ` : ''}
                      {formatDatasetMeta(dataset)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="tile-viewer-page__status-row">
            {isLoading && <span className="tile-viewer-page__status">Loading datasets…</span>}
            {!isLoading && featuredDatasets.length > 0 && !searchTerm && (
              <span className="tile-viewer-page__status">
                Showing latest {featuredDatasets.length} dataset{featuredDatasets.length > 1 ? 's' : ''}
              </span>
            )}
            {errorMessage && <span className="tile-viewer-page__status tile-viewer-page__status--error">{errorMessage}</span>}
          </div>
        </div>
        <p className="tile-viewer-page__hint">
          Current dataset:{' '}
          {visibleImageId ? (
            <span>
              <code>{visibleDatasetName || visibleImageId}</code>
              {visibleDatasetName && visibleDatasetName !== visibleImageId ? (
                <span> (<code>{visibleImageId}</code>)</span>
              ) : null}
            </span>
          ) : (
            <code>—</code>
          )}
        </p>
      </div>

      <div className="tile-viewer-page__viewer">
        <div className="tile-viewer-page__surface">
          {activeImageId ? (
            <ImageViewer key={`${activeImageId}-${viewerKey}`} imageId={activeImageId} />
          ) : (
            <div className="tile-viewer-page__placeholder">
              Select a dataset to initialize the viewer.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default TileViewerPage;
