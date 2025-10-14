import React, { useEffect, useRef } from 'react';
import OpenSeadragon from 'openseadragon';

interface ImageViewerProps {
  imageId: string;
}

/**
 * ImageViewer Component
 *
 * This component:
 * 1. Initializes OpenSeadragon viewer
 * 2. Configures it to load tiles from our backend API
 * 3. OpenSeadragon automatically requests tiles as user pans/zooms
 *
 * Data Flow:
 * - OSD loads DZI: GET http://localhost:8080/api/v1/tiles/{imageId}/image.dzi
 * - OSD parses XML to understand image pyramid structure
 * - OSD calculates visible tiles based on viewport
 * - OSD requests tiles: GET http://localhost:8080/api/v1/tiles/{imageId}/{level}/{x}_{y}.jpg
 * - Backend fetches from MinIO and streams back
 * - OSD renders tiles on canvas
 */
const ImageViewer: React.FC<ImageViewerProps> = ({ imageId }) => {
  const viewerRef = useRef<HTMLDivElement>(null);
  const osdInstanceRef = useRef<OpenSeadragon.Viewer | null>(null);

  useEffect(() => {
    // Don't initialize if already initialized or ref not ready
    if (!viewerRef.current || osdInstanceRef.current) return;

    console.log(`Initializing OpenSeadragon for imageId: ${imageId}`);

    // Initialize OpenSeadragon viewer
    const viewer = OpenSeadragon({
      element: viewerRef.current,

      // Prefix for OSD UI controls (zoom buttons, etc.)
      prefixUrl: '//openseadragon.github.io/openseadragon/images/',

      // CRITICAL: Point to your backend API DZI endpoint
      // Backend runs on port 8080 regardless of frontend port
      tileSources: `http://localhost:8080/api/v1/tiles/${imageId}/image.dzi`,

      // Viewer controls
      showNavigator: true,              // Mini-map in corner
      navigatorPosition: 'BOTTOM_RIGHT',
      showRotationControl: true,
      showHomeControl: true,
      showFullPageControl: true,
      showZoomControl: true,

      // Performance settings
      maxZoomPixelRatio: 2,            // Max zoom magnification
      minZoomImageRatio: 0.8,          // How far to zoom out
      visibilityRatio: 1.0,            // How much of image must be visible

      // Tile loading settings
      crossOriginPolicy: 'Anonymous',
      ajaxWithCredentials: false,

      // Initial zoom behavior
      immediateRender: false,          // Load tiles before showing
      blendTime: 0.5,                  // Fade-in animation

      // Debugging (set to true to see what OSD is doing)
      debugMode: false
    });

    // Event handlers for debugging
    viewer.addHandler('open', () => {
      console.log('OpenSeadragon: Image opened successfully');
      console.log(`   Image dimensions: ${viewer.world.getItemAt(0).getContentSize()}`);
    });

    viewer.addHandler('tile-loaded', () => {
      // Fires every time a tile loads
      // console.log('Tile loaded');
    });

    viewer.addHandler('tile-load-failed', (event: any) => {
      console.error('Tile load failed:', event);
    });

    viewer.addHandler('open-failed', (event: any) => {
      console.error('Failed to open image:', event);
      console.error('   Check that backend is running on port 8080');
      console.error('   Check that imageId exists in MinIO');
    });

    osdInstanceRef.current = viewer;

    // Cleanup on component unmount
    return () => {
      if (osdInstanceRef.current) {
        console.log('Cleaning up OpenSeadragon viewer');
        osdInstanceRef.current.destroy();
        osdInstanceRef.current = null;
      }
    };
  }, [imageId]); // Reinitialize if imageId changes

  return (
    <div
      ref={viewerRef}
      style={{
        width: '100%',
        height: '600px',
        border: '2px solid #ccc',
        borderRadius: '8px',
        backgroundColor: '#000',
        position: 'relative'
      }}
    />
  );
};

export default ImageViewer;
