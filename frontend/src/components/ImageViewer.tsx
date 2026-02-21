import React, { useEffect, useRef } from 'react';
import OpenSeadragon from 'openseadragon';
import '../styles/ImageViewer.scss';

const API_BASE_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

interface TilePrediction {
  tile_x: number;
  tile_y: number;
  pixel_x: number;
  pixel_y: number;
  width: number;
  height: number;
  tumor_probability: number;
}

interface ImageViewerProps {
  imageId: string;
  overlays?: TilePrediction[];
  threshold?: number;
  overlayOpacity?: number;
  heatmapUrl?: string;
  showHeatmap?: boolean;
  heatmapOpacity?: number;
}

/**
 * Thin OpenSeadragon wrapper so I can point at a MinIO dataset by imageId.
 */
const ImageViewer: React.FC<ImageViewerProps> = ({
  imageId,
  overlays = [],
  threshold = 0.5,
  overlayOpacity = 0.5,
  heatmapUrl,
  showHeatmap = false,
  heatmapOpacity = 0.45
}) => {
  const viewerRef = useRef<HTMLDivElement>(null);
  const osdInstanceRef = useRef<OpenSeadragon.Viewer | null>(null);

  useEffect(() => {
    // Don't initialize if already initialized or ref not ready
    if (!viewerRef.current || osdInstanceRef.current) return;

    console.log(`Initializing OpenSeadragon for imageId: ${imageId}`);

    // Initialize OpenSeadragon viewer
    const viewer = OpenSeadragon({
      element: viewerRef.current,
      prefixUrl: '//openseadragon.github.io/openseadragon/images/',
      tileSources: `${API_BASE_URL}/api/v1/tiles/${imageId}/image.dzi`,
      showNavigator: true,
      navigatorPosition: 'BOTTOM_RIGHT',
      showRotationControl: true,
      showHomeControl: true,
      showFullPageControl: true,
      showZoomControl: true,
      maxZoomPixelRatio: 2,
      minZoomImageRatio: 0.8,
      visibilityRatio: 1.0,
      crossOriginPolicy: 'Anonymous',
      ajaxWithCredentials: false,
      immediateRender: false,
      blendTime: 0.5,
      debugMode: false
    });

    // Console breadcrumbs while I'm debugging tile fetches.
    viewer.addHandler('open', () => {
      console.log('OpenSeadragon: Image opened successfully');
    });

    viewer.addHandler('tile-load-failed', (event: any) => {
      console.error('Tile load failed:', event);
    });

    viewer.addHandler('open-failed', (event: any) => {
      console.error('Failed to open image:', event);
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

  // Update overlays when they change or when the threshold changes
  useEffect(() => {
    const viewer = osdInstanceRef.current;
    if (!viewer || !viewer.isOpen()) return;

    // Clear existing overlays
    viewer.clearOverlays();

    if (showHeatmap && heatmapUrl) {
      const heatmapImage = document.createElement('img');
      heatmapImage.className = 'heatmap-overlay';
      heatmapImage.src = heatmapUrl;
      heatmapImage.alt = 'Analysis heatmap overlay';
      heatmapImage.style.opacity = heatmapOpacity.toString();
      heatmapImage.style.pointerEvents = 'none';

      viewer.addOverlay({
        element: heatmapImage,
        location: new OpenSeadragon.Rect(0, 0, 1, 1)
      });
    }

    console.log(`Updating ${overlays.length} overlays with threshold ${threshold}`);

    // Filter and add new overlays
    overlays
      .filter(tile => tile.tumor_probability >= threshold)
      .forEach((tile, index) => {
        const rect = viewer.viewport.imageToViewportRectangle(
          tile.pixel_x, tile.pixel_y, tile.width, tile.height
        );

        const overlayElement = document.createElement('div');
        overlayElement.id = `overlay-${index}`;
        overlayElement.className = 'tumor-overlay';
        overlayElement.style.border = `2px solid rgba(255, 0, 0, ${overlayOpacity})`;
        overlayElement.style.backgroundColor = `rgba(255, 0, 0, ${tile.tumor_probability * 0.2 * overlayOpacity})`;
        overlayElement.title = `Tumor Probability: ${(tile.tumor_probability * 100).toFixed(1)}%`;

        viewer.addOverlay({
          element: overlayElement,
          location: rect
        });
      });
  }, [overlays, threshold, overlayOpacity, imageId, showHeatmap, heatmapUrl, heatmapOpacity]);

  return <div ref={viewerRef} className="image-viewer" />;
};

export default ImageViewer;
