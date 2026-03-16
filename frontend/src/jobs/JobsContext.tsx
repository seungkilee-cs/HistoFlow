import React, { createContext, useContext, useEffect, useMemo, useReducer } from 'react';
import { API_BASE_URL } from '../utils/api';

export type ActivityEntry = {
  timestamp: string;
  stage: string;
  message: string;
  detail?: string | null;
};

export type ServerJob = {
  id: string;
  imageId: string;
  datasetName: string | null;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  stage: 'QUEUED' | 'DOWNLOADING' | 'TILING' | 'UPLOADING' | 'COMPLETED' | 'FAILED';
  message: string | null;
  failureReason: string | null;
  metadataPath: string | null;
  stageProgressPercent?: number | null;
  activityEntries?: ActivityEntry[];
  createdAt: string;
  updatedAt: string;
};

export type TrackedJob = {
  entryId: string;
  jobId?: string;
  imageId?: string;
  datasetName: string;
  fileName?: string;
  status: string;
  stage: string;
  message?: string | null;
  failureReason?: string | null;
  metadataPath?: string | null;
  uploadProgress?: number;
  uploadBytes?: number;
  totalBytes?: number;
  stageProgressPercent?: number | null;
  activityEntries: ActivityEntry[];
  createdAt: string;
  updatedAt: string;
  announcedTerminalStatus?: 'COMPLETED' | 'FAILED';
};

export type JobToast = {
  id: string;
  kind: 'success' | 'error';
  title: string;
  description: string;
  ctaLabel?: string;
  ctaTo?: string;
};

type JobsState = {
  jobs: TrackedJob[];
  toasts: JobToast[];
  connectionState: 'connecting' | 'live' | 'polling';
};

type JobsContextValue = {
  jobs: TrackedJob[];
  toasts: JobToast[];
  connectionState: JobsState['connectionState'];
  startLocalUpload: (payload: { datasetName: string; fileName: string; totalBytes: number }) => string;
  updateLocalUploadProgress: (entryId: string, uploadedBytes: number, totalBytes: number) => void;
  attachServerJob: (
    entryId: string,
    payload: { jobId: string; imageId: string; datasetName: string; status: string }
  ) => void;
  failLocalUpload: (entryId: string, message: string) => void;
  dismissToast: (toastId: string) => void;
};

type JobsAction =
  | { type: 'SET_CONNECTION_STATE'; connectionState: JobsState['connectionState'] }
  | { type: 'DISMISS_TOAST'; toastId: string }
  | { type: 'START_LOCAL_UPLOAD'; entryId: string; datasetName: string; fileName: string; totalBytes: number; timestamp: string }
  | { type: 'UPDATE_LOCAL_UPLOAD_PROGRESS'; entryId: string; uploadedBytes: number; totalBytes: number }
  | {
      type: 'ATTACH_SERVER_JOB';
      entryId: string;
      payload: { jobId: string; imageId: string; datasetName: string; status: string };
    }
  | { type: 'FAIL_LOCAL_UPLOAD'; entryId: string; message: string }
  | { type: 'UPSERT_REMOTE_JOBS'; jobs: ServerJob[]; notify: boolean };

const JobsContext = createContext<JobsContextValue | null>(null);

const initialState: JobsState = {
  jobs: [],
  toasts: [],
  connectionState: 'connecting',
};

function createToast(job: TrackedJob): JobToast | null {
  if (job.status === 'COMPLETED' && job.imageId) {
    return {
      id: `toast-${job.jobId ?? job.entryId}-completed`,
      kind: 'success',
      title: `${job.datasetName} is ready`,
      description: job.message ?? 'Open the viewer to inspect the tiled slide.',
      ctaLabel: 'Open viewer',
      ctaTo: `/tile-viewer/${job.imageId}`,
    };
  }

  if (job.status === 'FAILED') {
    return {
      id: `toast-${job.jobId ?? job.entryId}-failed`,
      kind: 'error',
      title: `${job.datasetName} failed`,
      description: job.failureReason ?? job.message ?? 'The slide could not be processed.',
    };
  }

  return null;
}

function sortJobs(jobs: TrackedJob[]) {
  return [...jobs].sort((left, right) => {
    const leftTime = Date.parse(left.updatedAt || left.createdAt);
    const rightTime = Date.parse(right.updatedAt || right.createdAt);
    return rightTime - leftTime;
  });
}

function mergeRemoteJob(state: JobsState, incoming: ServerJob, notify: boolean): JobsState {
  const existingIndex = state.jobs.findIndex(
    (job) => job.jobId === incoming.id || (!!incoming.imageId && job.imageId === incoming.imageId)
  );
  const existing = existingIndex >= 0 ? state.jobs[existingIndex] : null;

  const merged: TrackedJob = {
    entryId: existing?.entryId ?? incoming.id,
    jobId: incoming.id,
    imageId: incoming.imageId,
    datasetName: incoming.datasetName ?? existing?.datasetName ?? incoming.imageId,
    fileName: existing?.fileName,
    status: incoming.status,
    stage: incoming.stage,
    message: incoming.message,
    failureReason: incoming.failureReason,
    metadataPath: incoming.metadataPath,
    uploadProgress: existing?.uploadProgress ?? (incoming.status === 'IN_PROGRESS' ? 100 : undefined),
    uploadBytes: existing?.uploadBytes,
    totalBytes: existing?.totalBytes,
    stageProgressPercent: typeof incoming.stageProgressPercent === 'number'
      ? incoming.stageProgressPercent
      : incoming.status === 'COMPLETED'
        ? 100
        : incoming.stage === 'UPLOADING'
          ? existing?.stageProgressPercent ?? null
          : null,
    activityEntries: Array.isArray(incoming.activityEntries) && incoming.activityEntries.length > 0
      ? incoming.activityEntries
      : existing?.activityEntries ?? [],
    createdAt: incoming.createdAt ?? existing?.createdAt ?? new Date().toISOString(),
    updatedAt: incoming.updatedAt ?? new Date().toISOString(),
    announcedTerminalStatus: existing?.announcedTerminalStatus,
  };

  const jobs = existingIndex >= 0
    ? state.jobs.map((job, index) => index === existingIndex ? merged : job)
    : [merged, ...state.jobs];

  if (
    notify &&
    (merged.status === 'COMPLETED' || merged.status === 'FAILED') &&
    merged.announcedTerminalStatus !== merged.status
  ) {
    const toast = createToast(merged);
    if (toast) {
      merged.announcedTerminalStatus = merged.status as 'COMPLETED' | 'FAILED';
      return {
        ...state,
        jobs: sortJobs(existingIndex >= 0 ? jobs.map((job, index) => index === existingIndex ? merged : job) : [merged, ...state.jobs]),
        toasts: [toast, ...state.toasts].slice(0, 4),
      };
    }
  }

  return { ...state, jobs: sortJobs(jobs) };
}

function jobsReducer(state: JobsState, action: JobsAction): JobsState {
  switch (action.type) {
    case 'SET_CONNECTION_STATE':
      return { ...state, connectionState: action.connectionState };
    case 'DISMISS_TOAST':
      return { ...state, toasts: state.toasts.filter((toast) => toast.id !== action.toastId) };
    case 'START_LOCAL_UPLOAD':
      return {
        ...state,
        jobs: sortJobs([
          {
            entryId: action.entryId,
            datasetName: action.datasetName,
            fileName: action.fileName,
            status: 'LOCAL_UPLOADING',
            stage: 'LOCAL_UPLOAD',
            message: 'Uploading original slide to object storage.',
            uploadProgress: 0,
            uploadBytes: 0,
            totalBytes: action.totalBytes,
            stageProgressPercent: null,
            activityEntries: [
              {
                timestamp: action.timestamp,
                stage: 'LOCAL_UPLOAD',
                message: 'Uploading original slide to object storage.',
              },
            ],
            createdAt: action.timestamp,
            updatedAt: action.timestamp,
          },
          ...state.jobs,
        ]),
      };
    case 'UPDATE_LOCAL_UPLOAD_PROGRESS':
      return {
        ...state,
        jobs: sortJobs(
          state.jobs.map((job) => {
            if (job.entryId !== action.entryId) {
              return job;
            }

            return {
              ...job,
              uploadBytes: action.uploadedBytes,
              totalBytes: action.totalBytes,
              uploadProgress: action.totalBytes > 0 ? Math.round((action.uploadedBytes / action.totalBytes) * 100) : 0,
              updatedAt: new Date().toISOString(),
            };
          })
        ),
      };
    case 'ATTACH_SERVER_JOB':
      return {
        ...state,
        jobs: sortJobs(
          state.jobs.map((job) => {
            if (job.entryId !== action.entryId) {
              return job;
            }

            return {
              ...job,
              jobId: action.payload.jobId,
              imageId: action.payload.imageId,
              datasetName: action.payload.datasetName,
              status: !action.payload.status || action.payload.status === 'accepted'
                ? 'IN_PROGRESS'
                : action.payload.status.toUpperCase(),
              stage: 'QUEUED',
              message: 'Upload complete. Waiting for tiling worker.',
              uploadProgress: 100,
              uploadBytes: job.totalBytes,
              stageProgressPercent: null,
              activityEntries: [
                ...job.activityEntries,
                {
                  timestamp: new Date().toISOString(),
                  stage: 'QUEUED',
                  message: 'Upload complete. Waiting for tiling worker.',
                },
              ].slice(-50),
              updatedAt: new Date().toISOString(),
            };
          })
        ),
      };
    case 'FAIL_LOCAL_UPLOAD': {
      const updatedJobs = state.jobs.map((job) => {
        if (job.entryId !== action.entryId) {
          return job;
        }

        return {
          ...job,
          status: 'FAILED',
          stage: 'FAILED',
          message: 'Upload failed.',
          failureReason: action.message,
          updatedAt: new Date().toISOString(),
          announcedTerminalStatus: 'FAILED' as const,
          stageProgressPercent: null,
          activityEntries: [
            ...job.activityEntries,
            {
              timestamp: new Date().toISOString(),
              stage: 'FAILED',
              message: 'Upload failed.',
              detail: action.message,
            },
          ].slice(-50),
        };
      });
      const failedJob = updatedJobs.find((job) => job.entryId === action.entryId);
      const toast = failedJob ? createToast(failedJob) : null;
      return {
        ...state,
        jobs: sortJobs(updatedJobs),
        toasts: toast ? [toast, ...state.toasts].slice(0, 4) : state.toasts,
      };
    }
    case 'UPSERT_REMOTE_JOBS':
      return action.jobs.reduce((nextState, job) => mergeRemoteJob(nextState, job, action.notify), state);
    default:
      return state;
  }
}

async function fetchRecentJobs(notify: boolean, dispatch: React.Dispatch<JobsAction>) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/tiling/jobs?limit=20`);
    if (!response.ok) {
      return;
    }
    const data = await response.json() as { jobs?: ServerJob[] };
    dispatch({ type: 'UPSERT_REMOTE_JOBS', jobs: Array.isArray(data.jobs) ? data.jobs : [], notify });
  } catch {
    // Leave the current UI state untouched; polling/SSE will retry.
  }
}

async function reconcileTileAvailability(
  jobs: TrackedJob[],
  dispatch: React.Dispatch<JobsAction>
) {
  const candidates = jobs.filter(
    (job) => job.imageId && (job.status === 'IN_PROGRESS' || job.status === 'LOCAL_UPLOADING')
  );

  await Promise.all(candidates.map(async (job) => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/tiles/${job.imageId}/status`);
      if (!response.ok) {
        return;
      }

      const data = await response.json() as { status?: string; message?: string };
      const normalized = data.status?.toLowerCase();
      if (!normalized) {
        return;
      }

      let synthesized: ServerJob | null = null;

      if (normalized === 'completed') {
        synthesized = {
          id: job.jobId ?? job.imageId ?? job.entryId,
          imageId: job.imageId ?? '',
          datasetName: job.datasetName,
          status: 'COMPLETED',
          stage: 'COMPLETED',
          message: data.message ?? 'Tiles are ready.',
          failureReason: null,
          metadataPath: job.metadataPath ?? null,
          stageProgressPercent: 100,
          activityEntries: job.activityEntries,
          createdAt: job.createdAt,
          updatedAt: new Date().toISOString(),
        };
      } else if (normalized === 'processing' && job.stage === 'QUEUED') {
        synthesized = {
          id: job.jobId ?? job.imageId ?? job.entryId,
          imageId: job.imageId ?? '',
          datasetName: job.datasetName,
          status: 'IN_PROGRESS',
          stage: 'TILING',
          message: data.message ?? 'Generating Deep Zoom tiles.',
          failureReason: null,
          metadataPath: job.metadataPath ?? null,
          stageProgressPercent: null,
          activityEntries: job.activityEntries,
          createdAt: job.createdAt,
          updatedAt: new Date().toISOString(),
        };
      }

      if (synthesized) {
        dispatch({ type: 'UPSERT_REMOTE_JOBS', jobs: [synthesized], notify: true });
      }
    } catch {
      // Keep current state; the next poll will retry.
    }
  }));
}

export function JobsProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(jobsReducer, initialState);

  useEffect(() => {
    let isMounted = true;
    let eventSource: EventSource | null = null;
    let reconnectTimer: number | null = null;
    let pollTimer: number | null = null;

    const stopPolling = () => {
      if (pollTimer !== null) {
        window.clearInterval(pollTimer);
        pollTimer = null;
      }
    };

    const startPolling = () => {
      if (pollTimer !== null) {
        return;
      }

      pollTimer = window.setInterval(() => {
        fetchRecentJobs(true, dispatch);
      }, 5000);
    };

    const connect = () => {
      if (!isMounted) {
        return;
      }

      if (typeof EventSource === 'undefined') {
        dispatch({ type: 'SET_CONNECTION_STATE', connectionState: 'polling' });
        startPolling();
        return;
      }

      dispatch({ type: 'SET_CONNECTION_STATE', connectionState: 'connecting' });
      eventSource = new EventSource(`${API_BASE_URL}/api/v1/tiling/events/stream`);
      eventSource.onopen = () => {
        dispatch({ type: 'SET_CONNECTION_STATE', connectionState: 'live' });
        stopPolling();
      };
      eventSource.addEventListener('tiling-job', (event) => {
        const messageEvent = event as MessageEvent<string>;
        const job = JSON.parse(messageEvent.data) as ServerJob;
        dispatch({ type: 'UPSERT_REMOTE_JOBS', jobs: [job], notify: true });
      });
      eventSource.onerror = () => {
        dispatch({ type: 'SET_CONNECTION_STATE', connectionState: 'polling' });
        eventSource?.close();
        eventSource = null;
        startPolling();

        if (reconnectTimer === null) {
          reconnectTimer = window.setTimeout(() => {
            reconnectTimer = null;
            connect();
          }, 5000);
        }
      };
    };

    void fetchRecentJobs(false, dispatch);
    connect();

    return () => {
      isMounted = false;
      stopPolling();
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
      }
      eventSource?.close();
    };
  }, []);

  useEffect(() => {
    const interval = window.setInterval(() => {
      void reconcileTileAvailability(state.jobs, dispatch);
    }, 5000);

    return () => window.clearInterval(interval);
  }, [state.jobs]);

  const value = useMemo<JobsContextValue>(() => ({
    jobs: state.jobs,
    toasts: state.toasts,
    connectionState: state.connectionState,
    startLocalUpload: ({ datasetName, fileName, totalBytes }) => {
      const entryId = `local-${Math.random().toString(36).slice(2, 10)}`;
      dispatch({
        type: 'START_LOCAL_UPLOAD',
        entryId,
        datasetName,
        fileName,
        totalBytes,
        timestamp: new Date().toISOString(),
      });
      return entryId;
    },
    updateLocalUploadProgress: (entryId, uploadedBytes, totalBytes) => {
      dispatch({ type: 'UPDATE_LOCAL_UPLOAD_PROGRESS', entryId, uploadedBytes, totalBytes });
    },
    attachServerJob: (entryId, payload) => {
      dispatch({ type: 'ATTACH_SERVER_JOB', entryId, payload });
    },
    failLocalUpload: (entryId, message) => {
      dispatch({ type: 'FAIL_LOCAL_UPLOAD', entryId, message });
    },
    dismissToast: (toastId) => {
      dispatch({ type: 'DISMISS_TOAST', toastId });
    },
  }), [state.connectionState, state.jobs, state.toasts]);

  return (
    <JobsContext.Provider value={value}>
      {children}
    </JobsContext.Provider>
  );
}

export function useJobs() {
  const context = useContext(JobsContext);
  if (!context) {
    throw new Error('useJobs must be used within a JobsProvider');
  }
  return context;
}
