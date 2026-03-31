import { useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import '../styles/HomePage.scss';
import { uploadFileWithPresignedMultipart } from '../utils/upload';
import { useJobs, TrackedJob } from '../jobs/JobsContext';

// ── Helpers ───────────────────────────────────────────────────────────────────

function badgeClass(status: string) {
  switch (status) {
    case 'COMPLETED': return 'badge--success';
    case 'FAILED': return 'badge--error';
    case 'LOCAL_UPLOADING':
    case 'IN_PROGRESS': return 'badge--blue';
    default: return 'badge--dim';
  }
}

function badgeLabel(status: string, stage: string) {
  if (status === 'LOCAL_UPLOADING') return 'Uploading';
  if (status === 'IN_PROGRESS') {
    const s = stage.toLowerCase().replace('_', ' ');
    return s.charAt(0).toUpperCase() + s.slice(1);
  }
  if (status === 'COMPLETED') return 'Ready';
  if (status === 'FAILED') return 'Failed';
  return status;
}

// ── Job card ──────────────────────────────────────────────────────────────────

function JobCard({ job }: { job: TrackedJob }) {
  const isActive = job.status === 'LOCAL_UPLOADING' || job.status === 'IN_PROGRESS';
  const isCompleted = job.status === 'COMPLETED';
  const isFailed = job.status === 'FAILED';

  let progress: number | null = null;
  if (job.status === 'LOCAL_UPLOADING' && typeof job.uploadProgress === 'number') {
    progress = job.uploadProgress;
  } else if (job.stage === 'UPLOADING' && typeof job.stageProgressPercent === 'number') {
    progress = job.stageProgressPercent;
  }

  const lastEntry = job.activityEntries[job.activityEntries.length - 1];

  return (
    <div className={`job-card${isActive ? ' job-card--active' : ''}${isCompleted ? ' job-card--done' : ''}${isFailed ? ' job-card--failed' : ''}`}>
      <div className="job-card__top">
        <span className="job-card__name" title={job.datasetName || job.imageId || ''}>
          {job.datasetName || job.fileName || job.imageId || '—'}
        </span>
        <span className={`badge ${badgeClass(job.status)}`}>
          {badgeLabel(job.status, job.stage)}
        </span>
      </div>

      {isActive && progress !== null && (
        <div className="job-progress">
          <div className="job-progress__track">
            <div className="job-progress__fill" style={{ width: `${progress}%` }} />
          </div>
          <span className="job-progress__label">{progress}%</span>
        </div>
      )}

      {isActive && lastEntry && (
        <p className="job-card__detail">
          {lastEntry.detail || lastEntry.message}
        </p>
      )}

      {isFailed && job.failureReason && (
        <p className="job-card__error">{job.failureReason}</p>
      )}

      {isCompleted && job.imageId && (
        <Link to={`/tile-viewer/${job.imageId}`} className="job-card__cta">
          Open Viewer →
        </Link>
      )}
    </div>
  );
}

// ── HomePage ──────────────────────────────────────────────────────────────────

function HomePage() {
  const { jobs, startLocalUpload, updateLocalUploadProgress, attachServerJob, failLocalUpload } = useJobs();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) startUpload(file);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) startUpload(file);
    // Reset so the same file can be re-selected
    e.target.value = '';
  };

  const startUpload = async (file: File) => {
    setUploadError(null);
    const datasetName = file.name;
    const entryId = startLocalUpload({ datasetName, fileName: file.name, totalBytes: file.size });

    try {
      const result = await uploadFileWithPresignedMultipart(file, {
        concurrency: 4,
        partSizeHint: 16 * 1024 * 1024,
        datasetName,
        onProgress: (uploaded, total) => {
          updateLocalUploadProgress(entryId, uploaded, total);
        },
      });

      if (result.imageId) {
        attachServerJob(entryId, {
          jobId: result.jobId ?? `pending-${entryId}`,
          imageId: result.imageId,
          datasetName: result.datasetName,
          status: result.status,
        });
      }
    } catch (err: any) {
      const message = err?.message || String(err);
      setUploadError(message);
      failLocalUpload(entryId, message);
    }
  };

  return (
    <div className="home-page">
      {/* ── Upload panel ── */}
      <div className="home-page__upload">
        <div>
          <h1 className="home-page__title">Upload Slide</h1>
          <p className="home-page__subtitle">
            Whole-slide images are tiled for deep-zoom viewing and AI cancer analysis.
          </p>
        </div>

        <div
          className={`drop-zone${isDragging ? ' drop-zone--dragging' : ''}`}
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onDragLeave={() => setIsDragging(false)}
          onClick={() => fileInputRef.current?.click()}
          role="button"
          tabIndex={0}
          aria-label="Upload zone – click or drag a slide file"
          onKeyDown={(e) => e.key === 'Enter' && fileInputRef.current?.click()}
        >
          <input
            type="file"
            accept=".svs,.tif,.tiff,.ndpi,.mrxs,.scn,image/*"
            ref={fileInputRef}
            style={{ display: 'none' }}
            onChange={handleFileChange}
          />
          <div className="drop-zone__icon" aria-hidden="true">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1M16 12l-4-4m0 0L8 12m4-4v8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <p className="drop-zone__primary">
            {isDragging ? 'Drop to upload' : 'Drag & drop or click to select'}
          </p>
          <p className="drop-zone__formats">SVS · TIFF · NDPI · MRXS · SCN</p>
        </div>

        {uploadError && (
          <div className="home-page__error" role="alert">
            {uploadError}
          </div>
        )}
      </div>

      {/* ── Jobs panel ── */}
      <div className="home-page__jobs">
        <h2 className="home-page__jobs-title">Recent Jobs</h2>
        {jobs.length === 0 ? (
          <p className="home-page__jobs-empty">No jobs yet. Upload a slide to get started.</p>
        ) : (
          <div className="home-page__jobs-list">
            {jobs.map(job => (
              <JobCard key={job.entryId} job={job} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default HomePage;
