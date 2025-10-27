import React, { useEffect, useRef } from 'react';
import OpenSeadragon from 'openseadragon';
import '../styles/ImageViewer.scss';

interface ImageViewerProps {
  imageId: string;
}

/**
 * Thin OpenSeadragon wrapper so I can point at a MinIO dataset by imageId.
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
      prefixUrl: '//openseadragon.github.io/openseadragon/images/',
      tileSources: `http://localhost:8080/api/v1/tiles/${imageId}/image.dzi`,
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
      console.error('   Backend might be down or the dataset is missing in MinIO.');
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

  return <div ref={viewerRef} className="image-viewer" />;
};

export default ImageViewer;
