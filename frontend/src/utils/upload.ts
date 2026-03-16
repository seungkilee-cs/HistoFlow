const API_BASE_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

export type UploadProgressCb = (uploadedBytes: number, totalBytes: number) => void;

type InitiateResponse = {
  uploadId: string;
  key: string;
  partSize: number;
  imageId: string;
  datasetName: string;
};

type PresignResponse = { urls?: Array<{ partNumber: number; url: string }>; url?: string };
type CompleteResponse = {
  jobId?: string;
  imageId?: string;
  datasetName?: string;
  status?: string;
  message?: string;
};

// --- Persistence helpers --------------------------------------------------
function storageKeyForFile(file: File) {
  return `upload-state:${file.name}-${file.size}-${file.lastModified}`;
}

// Get persisted upload state for a file
function loadPersisted(file: File): any | null {
  try {
    const raw = localStorage.getItem(storageKeyForFile(file));
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

// save persisted upload state for current file
function savePersisted(file: File, state: any) {
  try {
    localStorage.setItem(storageKeyForFile(file), JSON.stringify(state));
  } catch (e) {
    // ignore
  }
}

function removePersisted(file: File) {
  try {
    localStorage.removeItem(storageKeyForFile(file));
  } catch (e) {
    // ignore
  }
}

// Start upload by initiating multipart upload on server
async function initiateUploadOnServer(
  file: File,
  partSizeHint: number,
  datasetName?: string,
  signal?: AbortSignal
): Promise<InitiateResponse> {
  const resp = await fetch(`${API_BASE_URL}/api/v1/uploads/multipart/initiate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      filename: file.name,
      size: file.size,
      contentType: file.type,
      partSizeHint,
      datasetName,
    }),
    signal,
  });
  if (!resp.ok) {
    const txt = await resp.text().catch(() => '');
    throw new Error(`Failed to initiate upload: ${resp.status} ${resp.statusText} ${txt}`);
  }
  return (await resp.json()) as InitiateResponse;
}

/**
 * Request presigned URLs for a batch of partNumbers. Backend should accept { uploadId, key, partNumbers }.
 * Returns a map partNumber -> url.
 */
async function getPresignedUrls(
  uploadId: string,
  key: string,
  partNumbers: number[],
  signal?: AbortSignal
): Promise<Record<number, string>> {
  const resp = await fetch(`${API_BASE_URL}/api/v1/uploads/multipart/presign`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ uploadId, key, partNumbers }),
    signal,
  });
  if (!resp.ok) {
    const txt = await resp.text().catch(() => '');
    throw new Error(`Failed to get presigned urls: ${resp.status} ${resp.statusText} ${txt}`);
  }
  const response = (await resp.json()) as PresignResponse;
  const out: Record<number, string> = {};
  if (Array.isArray(response.urls)) {
    for (const u of response.urls) out[u.partNumber] = u.url;
    return out;
  }
  // single url case (shouldn't happen for multipart) but handle defensively
  if (response.url && partNumbers.length === 1) {
    out[partNumbers[0]] = response.url;
    return out;
  }
  throw new Error('Invalid presign response');
}

async function uploadPartWithRetries(
  url: string,
  blob: Blob,
  maxRetries = 3,
  signal?: AbortSignal
): Promise<string> {
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const putResp = await fetch(url, { method: 'PUT', body: blob, signal });
      if (!putResp.ok) {
        const txt = await putResp.text().catch(() => '');
        throw new Error(`Part upload failed ${putResp.status} ${putResp.statusText} ${txt}`);
      }
      const etag = (putResp.headers.get('ETag') || putResp.headers.get('etag') || '').replace(/"/g, '');
      return etag;
    } catch (err) {
      if (attempt === maxRetries) throw err;
      // backoff
      await new Promise((r) => setTimeout(r, Math.pow(2, attempt) * 300));
    }
  }
  throw new Error('Upload retries exhausted');
}

async function completeUploadOnServer(
  uploadId: string,
  key: string,
  parts: Array<{ partNumber: number; etag: string }>,
  datasetName?: string,
  signal?: AbortSignal
) {
  const resp = await fetch(`${API_BASE_URL}/api/v1/uploads/multipart/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ uploadId, key, parts, datasetName }),
    signal,
  });
  if (!resp.ok) {
    const txt = await resp.text().catch(() => '');
    throw new Error(`Complete failed: ${resp.status} ${resp.statusText} ${txt}`);
  }
  return resp.json().catch(() => ({})) as Promise<CompleteResponse>;
}

export function createPartNumberAllocator(totalParts: number) {
  let nextPart = 1;
  return () => {
    if (nextPart > totalParts) {
      return null;
    }

    const partNumber = nextPart;
    nextPart += 1;
    return partNumber;
  };
}

// Create workers to upload parts concurrently and run them
async function runWorkers(totalParts: number, concurrency: number, workerFn: (partNumber: number) => Promise<void>) {
  const claimPart = createPartNumberAllocator(totalParts);
  const workers: Promise<void>[] = [];
  for (let i = 0; i < concurrency; i++) {
    const worker = (async () => {
      while (true) {
        const partNumber = claimPart();
        if (partNumber === null) break;
        await workerFn(partNumber);
      }
    })();
    workers.push(worker);
  }
  await Promise.all(workers); // wait for all workers to finish and exit
}

// --- Public API ----------------------------------------------------------
/**
 * Upload a large file using presigned multipart uploads.
 * - Initiates multipart upload on the backend
 * - Requests presigned URLs for parts (in batches)
 * - Uploads parts in parallel with retries
 * - Calls complete on the backend
 */
export async function uploadFileWithPresignedMultipart(
  file: File,
  {
    concurrency = 4,
    partSizeHint = 16 * 1024 * 1024,
    onProgress,
    signal,
    batchPresign = true,
    datasetName,
  }: {
    concurrency?: number;
    partSizeHint?: number;
    onProgress?: UploadProgressCb;
    signal?: AbortSignal;
    batchPresign?: boolean; // if backend supports batch presign
    datasetName?: string;
  } = {}
): Promise<{ success: boolean; key?: string; jobId?: string; imageId: string; datasetName: string; status: string; message: string }> {
  if (!file) throw new Error('No file provided');

  // try to load persisted state (for resume)
  let persisted = loadPersisted(file);

  let uploadId: string;
  let key: string;
  let partSize: number;
  let imageId: string | undefined;
  let resolvedDatasetName = datasetName || file.name;

  if (persisted && persisted.uploadId && persisted.key && persisted.partSize) {
    uploadId = persisted.uploadId;
    key = persisted.key;
    partSize = persisted.partSize;
    imageId = persisted.imageId;
    resolvedDatasetName = persisted.datasetName || resolvedDatasetName;
  } else {
    const init = await initiateUploadOnServer(file, partSizeHint, datasetName, signal);
    uploadId = init.uploadId;
    key = init.key;
    partSize = init.partSize || partSizeHint;
    imageId = init.imageId;
    resolvedDatasetName = init.datasetName || resolvedDatasetName;
    persisted = { uploadId, key, partSize, imageId, datasetName: resolvedDatasetName, uploadedParts: {} };
    savePersisted(file, persisted);
  }

  const totalSize = file.size;
  const totalParts = Math.ceil(totalSize / partSize);

  const uploadedParts: Record<number, string> = persisted.uploadedParts || {};
  let uploadedBytes = Object.keys(uploadedParts).reduce((acc, p) => {
    const partNumber = Number(p);
    const start = (partNumber - 1) * partSize;
    const end = Math.min(totalSize, start + partSize);
    return acc + (end - start);
  }, 0);

  onProgress?.(uploadedBytes, totalSize);

  // Upload a single part number
  const uploadSingle = async (partNumber: number) => {
    // If already uploaded
    if (uploadedParts[partNumber]) return;
    const start = (partNumber - 1) * partSize;
    const end = Math.min(totalSize, start + partSize);
    const blob = file.slice(start, end);

    // get URL(s) in batch if supported
    let urlMap: Record<number, string> = {};
    if (batchPresign) {
      // request a small batch around this part (e.g., 8 parts) to reduce roundtrips
      const batchSize = 8;
      const batchStart = partNumber;
      const batchEnd = Math.min(totalParts, batchStart + batchSize - 1);
      const parts = [] as number[];
      for (let i = batchStart; i <= batchEnd; i++) if (!uploadedParts[i]) parts.push(i);
      if (parts.length > 0) urlMap = await getPresignedUrls(uploadId, key, parts, signal);
    } else {
      urlMap = await getPresignedUrls(uploadId, key, [partNumber], signal);
    }

    const url = urlMap[partNumber];
    if (!url) throw new Error('No presigned url for part ' + partNumber);

    const etag = await uploadPartWithRetries(url, blob, 3, signal);
    uploadedParts[partNumber] = etag || '';
    persisted.uploadedParts = persisted.uploadedParts || {};
    persisted.uploadedParts[partNumber] = etag;
    savePersisted(file, persisted);

    // update progress
    uploadedBytes += blob.size;
    onProgress?.(uploadedBytes, totalSize);
  };

  // run workers
  await runWorkers(totalParts, concurrency, async (partNumber) => {
    // skip already uploaded parts
    if (uploadedParts[partNumber]) return;
    if (signal?.aborted) throw new Error('Upload aborted');
    await uploadSingle(partNumber);
  });

  // Prepare parts array
  const partsArray = Object.entries(uploadedParts)
    .map(([k, v]) => ({ partNumber: Number(k), etag: v }))
    .sort((a, b) => a.partNumber - b.partNumber);

  const completion = await completeUploadOnServer(uploadId, key, partsArray, datasetName, signal);

  // cleanup
  removePersisted(file);

  return {
    success: true,
    key,
    jobId: completion.jobId,
    imageId: completion.imageId || imageId || key,
    datasetName: completion.datasetName || resolvedDatasetName,
    status: completion.status || 'accepted',
    message: completion.message || 'Upload complete. Waiting for tiling worker.',
  };
}
