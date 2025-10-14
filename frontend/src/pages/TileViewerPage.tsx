import React, { useState } from 'react';
import ImageViewer from '../components/ImageViewer';
import '../styles/TileViewerPage.scss';

/**
 * Tile Serve 테스트용 페이지
 * 
 * Sprint 1 test page to verify full-stack tile serving works
 *
 * OSD 뷰어를 띄우는 기능이 완성되었다고 가정하고
 * 1. [Frontend] embed OpenSeadragon viewer
 * 2. [Frontend] rrequest DZI from backend -> 
 * 3. [Frontend] request tiles from backend
 * 4. [Backend] fetch tiles from MinIO
 * 5. [Backend] stream tiles to frontend ->
 * 6. [e2e] User sees and zoom smoothly on frontend
 */
const TileViewerPage: React.FC = () => {
  const [imageId, setImageId] = useState('test-image-001');
  const [viewerKey, setViewerKey] = useState(0); // force remount on imageId change

  const handleImageIdChange = (newId: string) => {
    setImageId(newId);
    setViewerKey(prev => prev + 1); // force viewer to reinitialize
  };

  return (
    <div className="tile-viewer-page">
      <div className="tile-viewer-page__header">
        <h1 className="tile-viewer-page__title">HistoFlow Tile Viewer</h1>
      </div>

      <div className="tile-viewer-page__control-bar">
        <label className="tile-viewer-page__label" htmlFor="tile-viewer-image-id">
          Image ID
        </label>
        <input
          id="tile-viewer-image-id"
          className="tile-viewer-page__input"
          type="text"
          value={imageId}
          onChange={(e) => handleImageIdChange(e.target.value.trim())}
          placeholder="e.g. test-image-001"
        />
        <p className="tile-viewer-page__hint">
          Current dataset: <code>{imageId || '—'}</code>
        </p>
      </div>

      <div className="tile-viewer-page__viewer">
        <div className="tile-viewer-page__surface">
          <ImageViewer key={viewerKey} imageId={imageId} />
        </div>
      </div>
    </div>
  );
};

export default TileViewerPage;
