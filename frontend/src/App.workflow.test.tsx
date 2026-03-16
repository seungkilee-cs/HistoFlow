import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('./components/ImageViewer', () => ({
  default: ({ imageId }: { imageId: string }) => <div data-testid="image-viewer-mock">{imageId}</div>,
}));

class MockEventSource {
  static instances: MockEventSource[] = [];

  onopen: ((this: EventSource, ev: Event) => any) | null = null;
  onerror: ((this: EventSource, ev: Event) => any) | null = null;
  private listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>();

  constructor() {
    MockEventSource.instances.push(this);
    queueMicrotask(() => {
      this.onopen?.call(this as unknown as EventSource, new Event('open'));
    });
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    const current = this.listeners.get(type) ?? [];
    current.push(listener);
    this.listeners.set(type, current);
  }

  emit(type: string, payload: unknown) {
    const event = { data: JSON.stringify(payload) } as MessageEvent<string>;
    (this.listeners.get(type) ?? []).forEach((listener) => listener(event));
  }

  close() {
    return;
  }
}

const defaultFetch = async (input: RequestInfo | URL) => {
  const url = String(input);
  if (url.includes('/api/v1/tiling/jobs')) {
    return new Response(JSON.stringify({ jobs: [] }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  if (url.includes('/api/v1/tiles/datasets')) {
    return new Response(JSON.stringify({ datasets: [] }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  return new Response(null, { status: 404 });
};

beforeEach(() => {
  window.history.pushState({}, '', '/');
  MockEventSource.instances = [];
  vi.stubGlobal('EventSource', MockEventSource);
  vi.stubGlobal('fetch', vi.fn(defaultFetch));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

it('shows a completion toast and opens the viewer route from the CTA', async () => {
  render(<App />);

  await waitFor(() => {
    expect(MockEventSource.instances.length).toBe(1);
  });

  await act(async () => {
    MockEventSource.instances[0].emit('tiling-job', {
      id: 'job-1',
      imageId: 'img-ready',
      datasetName: 'Case-ready',
      status: 'COMPLETED',
        stage: 'COMPLETED',
        message: 'Tiles are ready.',
        failureReason: null,
        metadataPath: 'img-ready/metadata.json',
        stageProgressPercent: 100,
        activityEntries: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });
  });

  await waitFor(() => {
    expect(screen.getByText(/Case-ready is ready/i)).toBeTruthy();
  });

  const notifications = screen.getByLabelText(/Notifications/i);
  fireEvent.click(within(notifications).getByRole('link', { name: /^Open viewer$/i }));

  await waitFor(() => {
    expect(screen.getByTestId('image-viewer-mock').textContent).toContain('img-ready');
  });
});

it('shows backend upload progress and structured activity on the home page', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/api/v1/tiling/jobs')) {
      return new Response(JSON.stringify({
        jobs: [
          {
            id: 'job-2',
            imageId: 'img-progress',
            datasetName: 'Tumor Sample',
            status: 'IN_PROGRESS',
            stage: 'UPLOADING',
            message: 'Uploading generated tiles to object storage.',
            failureReason: null,
            metadataPath: null,
            stageProgressPercent: 42,
            activityEntries: [
              {
                timestamp: '2026-03-16T12:00:00Z',
                stage: 'UPLOADING',
                message: 'Preparing generated tiles for upload.',
                detail: 'Found 4,812 files to upload.',
              },
              {
                timestamp: '2026-03-16T12:00:05Z',
                stage: 'UPLOADING',
                message: 'Uploading generated tiles to object storage.',
                detail: 'Uploaded 2,000 / 4,812 files.',
              },
            ],
            createdAt: '2026-03-16T11:59:00Z',
            updatedAt: '2026-03-16T12:00:05Z',
          },
        ],
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    return defaultFetch(input);
  }));

  render(<App />);

  await waitFor(() => {
    expect(screen.getByText(/Uploading generated tiles to object storage\. 42%/i)).toBeTruthy();
  });

  const activityPanel = screen.getByLabelText(/Backend activity/i);
  expect(within(activityPanel).getByText(/Uploaded 2,000 \/ 4,812 files\./i)).toBeTruthy();
  expect(within(activityPanel).getByText(/Preparing generated tiles for upload\./i)).toBeTruthy();
});
