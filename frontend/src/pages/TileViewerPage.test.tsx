import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TileViewerPage from './TileViewerPage';

vi.mock('../components/ImageViewer', () => ({
  default: (props: any) => (
    <div
      data-testid="image-viewer-mock"
      data-heatmap-url={props.heatmapUrl ?? ''}
      data-show-heatmap={String(props.showHeatmap)}
    >
      {props.imageId}
    </div>
  ),
}));

const jsonResponse = (payload: unknown, status = 200) =>
  new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

describe('TileViewerPage analysis flow', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';

      if (url.includes('/api/v1/tiles/datasets')) {
        return jsonResponse({
          datasets: [
            {
              imageId: 'img-1',
              datasetName: 'Case-1',
              totalObjects: 120,
              totalSizeBytes: 1000,
              lastModifiedMillis: Date.now(),
            },
          ],
          nextContinuationToken: null,
          appliedPrefix: '',
        });
      }

      if (url.includes('/api/v1/analysis/history/')) {
        return jsonResponse({ jobs: [] });
      }

      if (url.includes('/api/v1/analysis/trigger/img-1') && method === 'POST') {
        return jsonResponse({ job_id: 'job-1', status: 'accepted', message: 'ok' });
      }

      if (url.includes('/api/v1/analysis/status/job-1')) {
        return jsonResponse({
          status: 'completed',
          tiles_processed: 20,
          total_tiles: 20,
          message: 'Complete',
        });
      }

      if (url.includes('/api/v1/analysis/results/job-1')) {
        return jsonResponse({
          tile_predictions: [
            {
              tile_x: 0,
              tile_y: 0,
              pixel_x: 0,
              pixel_y: 0,
              width: 100,
              height: 100,
              tumor_probability: 0.9,
            },
          ],
        });
      }

      if (url.includes('/api/v1/analysis/heatmap/job-1') && method === 'HEAD') {
        return new Response(null, { status: 200 });
      }

      return new Response(null, { status: 404 });
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows heatmap controls after completed analysis', async () => {
    render(<TileViewerPage />);

    await waitFor(() => {
      expect(screen.getByText(/Current dataset:/i)).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /Run Cancer Analysis/i }));

    await waitFor(() => {
      expect(screen.getByLabelText(/Show Heatmap/i)).toBeTruthy();
    }, { timeout: 7000 });

    const viewer = screen.getByTestId('image-viewer-mock');
    expect(viewer.getAttribute('data-show-heatmap')).toBe('true');
    expect(viewer.getAttribute('data-heatmap-url')).toContain('/api/v1/analysis/heatmap/job-1');
  });

  it('auto-populates summary and heatmap from completed history', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);

      if (url.includes('/api/v1/tiles/datasets')) {
        return jsonResponse({
          datasets: [
            {
              imageId: 'img-1',
              datasetName: 'Case-1',
              totalObjects: 120,
              totalSizeBytes: 1000,
              lastModifiedMillis: Date.now(),
            },
          ],
          nextContinuationToken: null,
          appliedPrefix: '',
        });
      }

      if (url.includes('/api/v1/analysis/history/img-1')) {
        return jsonResponse({
          jobs: [{
            jobId: 'job-old',
            status: 'COMPLETED',
            tilesProcessed: 300,
            totalTiles: 300,
            tumorAreaPercentage: 22.1,
            aggregateScore: 0.8,
            maxScore: 0.95,
            heatmapKey: 'img-1/heatmap_level_12.png',
            errorMessage: null,
          }],
        });
      }

      return new Response(null, { status: 404 });
    }));

    render(<TileViewerPage />);

    await waitFor(() => {
      expect(screen.getByTestId('image-viewer-mock').getAttribute('data-heatmap-url'))
        .toContain('/api/v1/analysis/heatmap/job-old');
    }, { timeout: 3000 });

    expect(screen.getByText(/22\.1/)).toBeTruthy();
  });
});
