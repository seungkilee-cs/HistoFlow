import { useState, useRef } from 'react';
import '../styles/HomePage.scss';
import { uploadFileWithPresignedMultipart } from '../utils/upload';
import { useJobs } from '../jobs/JobsContext';

function HomePage() {
  const { jobs, startLocalUpload, updateLocalUploadProgress, attachServerJob, failLocalUpload } = useJobs();
  const [_image, setImage] = useState<File | null>(null);
  // Likely won't be needed as real images are to big. Just here for testing
  const [preview, setPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<number>(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [activeEntryId, setActiveEntryId] = useState<string | null>(null);

  const activeJob = activeEntryId
    ? jobs.find((job) => job.entryId === activeEntryId) ?? jobs[0] ?? null
    : jobs[0] ?? null;
  const localUploadProgress = activeJob?.status === 'LOCAL_UPLOADING'
    ? activeJob.uploadProgress ?? progress
    : null;
  const backendProgress = activeJob?.stage === 'UPLOADING' && typeof activeJob.stageProgressPercent === 'number'
    ? activeJob.stageProgressPercent
    : null;
  const visibleActivityEntries = [...(activeJob?.activityEntries ?? [])].reverse();

  const statusText = (() => {
    if (uploadError) {
      return null;
    }

    if (!activeJob) {
      return null;
    }

    if (activeJob.status === 'LOCAL_UPLOADING') {
      return `Uploading original slide to object storage... ${localUploadProgress ?? 0}%`;
    }

    if (backendProgress !== null) {
      return `${activeJob.message ?? 'Uploading generated tiles to object storage.'} ${backendProgress}%`;
    }

    return activeJob.message ?? null;
  })();

  const formatActivityTime = (timestamp: string) => {
    const parsed = new Date(timestamp);
    if (Number.isNaN(parsed.getTime())) {
      return '';
    }

    return parsed.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      setImage(file);
      setPreview(URL.createObjectURL(file));
      // kick off upload
      startUpload(file);
    }
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setImage(file);
      setPreview(URL.createObjectURL(file));
      // kick off upload
      startUpload(file);
    }
  };

  // Upload function with progress updates
  const startUpload = async (file: File) => {
    const datasetName = file.name;
    const entryId = startLocalUpload({
      datasetName,
      fileName: file.name,
      totalBytes: file.size,
    });
    setActiveEntryId(entryId);

    setUploadError(null);
    setProgress(0);
    try {
      const result = await uploadFileWithPresignedMultipart(file, {
        concurrency: 4, // Number of parallel upload parts... 4 HTTP calls run at the same time
        partSizeHint: 16 * 1024 * 1024, // How big each part should be (16MB).. S3 minimum is 5MB
        datasetName,
        onProgress: (uploaded, total) => {
          updateLocalUploadProgress(entryId, uploaded, total);
          setProgress(Math.min(100, Math.round((uploaded / total) * 100)));
        },
      });
      setProgress(100);
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

  const handleButtonClick = () => {
    fileInputRef.current?.click();
  };

  return (
    <div className="HomePage">
      <h1>HistoFlow</h1>
      <div
        className="upload-container"
        onDrop={handleDrop}
        onDragOver={handleDragOver}
      >
        <p>Drag & drop an image here</p>
        <button
          type="button"
          onClick={handleButtonClick}
          className="upload-button"
        >
          Select Image
        </button>
        <input
          type="file"
          accept=".svs,.tif,.tiff,.ndpi,.mrxs,.scn,image/*"
          ref={fileInputRef}
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
        {preview && (
          <div className="preview-container">
            <img
              src={preview}
              alt="Preview"
              className="preview-image"
            />
          </div>
        )}

        {statusText && (
          <div className="upload-status" aria-live="polite">
            <div className="upload-status__message">{statusText}</div>
            {localUploadProgress !== null && (
              <div className="upload-progress">
                <div className="upload-progress__track">
                  <div className="upload-progress__fill" style={{ width: `${localUploadProgress}%` }} />
                </div>
                <span className="upload-progress__label">{localUploadProgress}%</span>
              </div>
            )}
            {backendProgress !== null && (
              <div className="upload-progress upload-progress--backend">
                <div className="upload-progress__track">
                  <div className="upload-progress__fill" style={{ width: `${backendProgress}%` }} />
                </div>
                <span className="upload-progress__label">{backendProgress}%</span>
              </div>
            )}
          </div>
        )}
        {activeJob && visibleActivityEntries.length > 0 && (
          <section className="upload-activity" aria-label="Backend activity">
            <div className="upload-activity__header">
              <h2>Activity</h2>
              <span>{activeJob.datasetName}</span>
            </div>
            <ol className="upload-activity__list">
              {visibleActivityEntries.map((entry, index) => (
                <li
                  key={`${entry.timestamp}-${entry.stage}-${index}`}
                  className="upload-activity__item"
                >
                  <div className="upload-activity__meta">
                    <span className="upload-activity__time">{formatActivityTime(entry.timestamp)}</span>
                    <span className="upload-activity__stage">{entry.stage}</span>
                  </div>
                  <p className="upload-activity__message">{entry.message}</p>
                  {entry.detail && <p className="upload-activity__detail">{entry.detail}</p>}
                </li>
              ))}
            </ol>
          </section>
        )}
        {/* Error message */}
        {uploadError && (
          <div style={{ marginTop: '1rem', color: '#ff6b6b' }}>
            Error: {uploadError}
          </div>
        )}
      </div>
    </div>
  );
}

export default HomePage;
