import React, { useState } from 'react';
import ImageViewer from '../components/ImageViewer';

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
const TestViewerPage: React.FC = () => {
  const [imageId, setImageId] = useState('test-image-001');
  const [viewerKey, setViewerKey] = useState(0); // force remount on imageId change

  const handleImageIdChange = (newId: string) => {
    setImageId(newId);
    setViewerKey(prev => prev + 1); // force viewer to reinitialize
  };

  return (
    <div style={{
      padding: '40px',
      maxWidth: '1400px',
      margin: '0 auto',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    }}>
      {/* Header */}
      <div style={{ marginBottom: '30px' }}>
        <h1 style={{ marginBottom: '10px', color: '#333' }}>
          HistoFlow Tile Viewer - Sprint 1 Test
        </h1>
        <p style={{ color: '#666', fontSize: '16px' }}>
          Testing full-stack tile serving: React → Kotlin Backend → MinIO
        </p>
      </div>

      {/* controls */}
      <div style={{
        marginBottom: '20px',
        padding: '20px',
        backgroundColor: '#f5f5f5',
        borderRadius: '8px'
      }}>
        <label style={{ display: 'block', marginBottom: '10px', fontWeight: 'bold' }}>
          Image ID:
        </label>
        <input
          type="text"
          value={imageId}
          onChange={(e) => handleImageIdChange(e.target.value)}
          style={{
            padding: '8px 12px',
            fontSize: '14px',
            width: '300px',
            borderRadius: '4px',
            border: '1px solid #ccc'
          }}
          placeholder="Enter image ID (e.g., test-image-001)"
        />
        <p style={{ fontSize: '13px', color: '#666', marginTop: '8px' }}>
          Current: <code>{imageId}</code>
        </p>
      </div>

      {/* viewer */}
      <div style={{ marginBottom: '20px' }}>
        <ImageViewer key={viewerKey} imageId={imageId} />
      </div>

      {/* instructions */}
      <div style={{
        padding: '20px',
        backgroundColor: '#e8f5e9',
        borderRadius: '8px',
        fontSize: '14px'
      }}>
        <h3 style={{ marginTop: 0 }}>Controls & Testing</h3>
        <ul style={{ marginBottom: '10px' }}>
          <li><strong>Pan:</strong> Left-click and drag</li>
          <li><strong>Zoom In:</strong> Scroll up or click zoom buttons</li>
          <li><strong>Zoom Out:</strong> Scroll down or click zoom buttons</li>
          <li><strong>Reset View:</strong> Click home button (house icon)</li>
          <li><strong>Mini-map:</strong> Use navigator in bottom-right corner</li>
        </ul>

        <h4 style={{ marginTop: '20px' }}>What to Verify:</h4>
        <ul>
          <li>Image loads within 2-3 seconds</li>
          <li>Can zoom from thumbnail to full resolution smoothly</li>
          <li>Can pan to any part of the image</li>
          <li>No "404 Not Found" errors in browser console (F12)</li>
          <li>Tiles load progressively as you zoom/pan</li>
          <li>Backend logs show tile requests being served</li>
        </ul>

        <h4 style={{ marginTop: '20px' }}>Debugging:</h4>
        <ul>
          <li>Open browser console (F12) to see logs</li>
          <li>Check Network tab to see tile HTTP requests</li>
          <li>Verify backend is running on port 8080</li>
          <li>Verify MinIO has tiles for this imageId</li>
        </ul>
      </div>
    </div>
  );
};

export default TestViewerPage;
