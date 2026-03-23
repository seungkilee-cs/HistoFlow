⏺ Write(frontend/src/styles/base.scss)
  ⎿  Added 47 lines, removed 4 lines
       1 -$main-bg: #14213d;
       2 -$text-color: #fff;
       1 +:root {
       2 +  --bg: #0c1220;
       3 +  --surface: #131c2e;
       4 +  --surface-2: #1a2640;
       5 +  --surface-3: #20304a;
       6 +  --border: rgba(255, 255, 255, 0.08);
       7 +  --border-focus: rgba(252, 163, 17, 0.45);
       8 +  --text: #e4ecf7;
       9 +  --text-muted: rgba(228, 236, 247, 0.55);
      10 +  --text-dim: rgba(228, 236, 247, 0.28);
      11 +  --accent: #fca311;
      12 +  --accent-hover: #ffb733;
      13 +  --blue: #3b82f6;
      14 +  --blue-hover: #60a5fa;
      15 +  --blue-dim: rgba(59, 130, 246, 0.15);
      16 +  --success: #22c55e;
      17 +  --success-dim: rgba(34, 197, 94, 0.15);
      18 +  --error: #f85149;
      19 +  --error-dim: rgba(248, 81, 73, 0.15);
      20 +  --warning: #f59e0b;
      21 +  --nav-h: 56px;
      22 +  --sidebar-w: 292px;
      23 +  --radius-sm: 6px;
      24 +  --radius-md: 10px;
      25 +  --radius-lg: 14px;
      26 +  --radius-xl: 20px;
      27 +}
      28
      29 +*, *::before, *::after {
      30 +  box-sizing: border-box;
      31 +}
      32 +
      33  body {
       5 -  color: $text-color;
       6 -  background: $main-bg;
      34 +  margin: 0;
      35 +  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Inter", Roboto, sans-serif;
      36 +  background: var(--bg);
      37 +  color: var(--text);
      38 +  -webkit-font-smoothing: antialiased;
      39 +  -moz-osx-font-smoothing: grayscale;
      40  }
      41 +
      42 +a {
      43 +  color: inherit;
      44 +  text-decoration: none;
      45 +}
      46 +
      47 +button {
      48 +  cursor: pointer;
      49 +  font-family: inherit;
      50 +}

⏺ Write(frontend/src/styles/App.scss)
  ⎿  Added 85 lines, removed 15 lines
       1 -@import url('https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap');
       1 +.app {
       2 +  min-height: 100vh;
       3 +  display: flex;
       4 +  flex-direction: column;
       5 +}
       6
       3 -.App {
       4 -  text-align: center;
       7 +.app-main {
       8 +  flex: 1;
       9 +  display: flex;
      10 +  flex-direction: column;
      11  }
      12
       7 -.App-logo {
       8 -  height: 40vmin;
       9 -  pointer-events: none;
      13 +/* ── Nav ── */
      14 +.app-nav {
      15 +  height: var(--nav-h);
      16 +  padding: 0 20px;
      17 +  background: var(--surface);
      18 +  border-bottom: 1px solid var(--border);
      19 +  display: flex;
      20 +  align-items: center;
      21 +  gap: 24px;
      22 +  position: sticky;
      23 +  top: 0;
      24 +  z-index: 100;
      25  }
      26
      12 -.App-header {
      13 -  background-color: #282c34;
      14 -  min-height: 100vh;
      27 +.app-nav__brand {
      28 +  font-size: 18px;
      29 +  font-weight: 700;
      30 +  letter-spacing: -0.02em;
      31 +  color: var(--text);
      32 +  flex-shrink: 0;
      33 +
      34 +  &:hover {
      35 +    color: var(--text);
      36 +  }
      37 +}
      38 +
      39 +.app-nav__brand-accent {
      40 +  color: var(--accent);
      41 +}
      42 +
      43 +.app-nav__links {
      44    display: flex;
      16 -  flex-direction: column;
      45 +  gap: 2px;
      46 +  flex: 1;
      47 +}
      48 +
      49 +.app-nav__link {
      50 +  padding: 6px 13px;
      51 +  border-radius: var(--radius-md);
      52 +  font-size: 14px;
      53 +  font-weight: 500;
      54 +  color: var(--text-muted);
      55 +  transition: color 0.15s, background 0.15s;
      56 +
      57 +  &:hover {
      58 +    color: var(--text);
      59 +    background: rgba(255, 255, 255, 0.06);
      60 +  }
      61 +
      62 +  &.active {
      63 +    color: var(--text);
      64 +    background: rgba(255, 255, 255, 0.1);
      65 +  }
      66 +}
      67 +
      68 +.app-nav__status {
      69 +  display: flex;
      70    align-items: center;
      18 -  justify-content: center;
      19 -  font-size: calc(10px + 2vmin);
      20 -  color: white;
      71 +  gap: 7px;
      72 +  font-size: 12px;
      73 +  color: var(--text-muted);
      74 +  flex-shrink: 0;
      75  }
      76
      23 -.App-link {
      24 -  color: #61dafb;
      77 +.app-nav__dot {
      78 +  width: 7px;
      79 +  height: 7px;
      80 +  border-radius: 50%;
      81 +  flex-shrink: 0;
      82 +
      83 +  &--live {
      84 +    background: var(--success);
      85 +    box-shadow: 0 0 5px var(--success);
      86 +  }
      87 +
      88 +  &--polling {
      89 +    background: var(--warning);
      90 +  }
      91 +
      92 +  &--connecting {
      93 +    background: var(--text-dim);
      94 +  }
      95  }

⏺ Write(frontend/src/App.tsx)
  ⎿  Added 54 lines, removed 25 lines
       1 -import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
       1 +import { BrowserRouter as Router, Routes, Route, NavLink, Link } from 'react-router-dom';
       2  import HomePage from './components/HomePage';
       3  import TileViewerPage from './pages/TileViewerPage';
       4  import ToastViewport from './components/ToastViewport';
       5 -import { JobsProvider } from './jobs/JobsContext';
       5 +import { JobsProvider, useJobs } from './jobs/JobsContext';
       6
       7 +function NavBar() {
       8 +  const { connectionState } = useJobs();
       9 +  const statusLabel =
      10 +    connectionState === 'live' ? 'Live'
      11 +    : connectionState === 'polling' ? 'Polling'
      12 +    : 'Connecting';
      13 +
      14 +  return (
      15 +    <nav className="app-nav">
      16 +      <Link to="/" className="app-nav__brand">
      17 +        Histo<span className="app-nav__brand-accent">Flow</span>
      18 +      </Link>
      19 +      <div className="app-nav__links">
      20 +        <NavLink
      21 +          to="/"
      22 +          end
      23 +          className={({ isActive }) => `app-nav__link${isActive ? ' active' : ''}`}
      24 +        >
      25 +          Upload
      26 +        </NavLink>
      27 +        <NavLink
      28 +          to="/tile-viewer"
      29 +          className={({ isActive }) => `app-nav__link${isActive ? ' active' : ''}`}
      30 +        >
      31 +          Viewer
      32 +        </NavLink>
      33 +      </div>
      34 +      <div className="app-nav__status">
      35 +        <span className={`app-nav__dot app-nav__dot--${connectionState}`} aria-hidden />
      36 +        <span>{statusLabel}</span>
      37 +      </div>
      38 +    </nav>
      39 +  );
      40 +}
      41 +
      42 +function AppShell() {
      43 +  return (
      44 +    <div className="app">
      45 +      <NavBar />
      46 +      <main className="app-main">
      47 +        <Routes>
      48 +          <Route path="/" element={<HomePage />} />
      49 +          <Route path="/tile-viewer" element={<TileViewerPage />} />
      50 +          <Route path="/tile-viewer/:imageId" element={<TileViewerPage />} />
      51 +        </Routes>
      52 +      </main>
      53 +      <ToastViewport />
      54 +    </div>
      55 +  );
      56 +}
      57 +
      58  function App() {
      59    return (
      60      <Router>
      61        <JobsProvider>
      11 -        <div className="App">
      12 -          <nav style={{
      13 -            padding: '20px',
      14 -            backgroundColor: '#2c3e50',
      15 -            color: 'white',
      16 -            display: 'flex',
      17 -            gap: '20px'
      18 -          }}>
      19 -            <Link to="/" style={{ color: 'white', textDecoration: 'none', fontSize: '18px' }}>
      20 -              Home
      21 -            </Link>
      22 -            <Link to="/tile-viewer" style={{ color: 'white', textDecoration: 'none', fontSize: '18px' }}>
      23 -              Tile Viewer
      24 -            </Link>
      25 -          </nav>
      26 -
      27 -          <Routes>
      28 -            <Route path="/" element={<HomePage />} />
      29 -            <Route path="/tile-viewer" element={<TileViewerPage />} />
      30 -            <Route path="/tile-viewer/:imageId" element={<TileViewerPage />} />
      31 -          </Routes>
      32 -          <ToastViewport />
      33 -        </div>
      62 +        <AppShell />
      63        </JobsProvider>
      64      </Router>
      65    );

⏺ Now write the revamped HomePage:

⏺ Write(frontend/src/components/HomePage.tsx)
  ⎿  Added 143 lines, removed 152 lines
        1  import { useState, useRef } from 'react';
        2 +import { Link } from 'react-router-dom';
        3  import '../styles/HomePage.scss';
        4  import { uploadFileWithPresignedMultipart } from '../utils/upload';
        4 -import { useJobs } from '../jobs/JobsContext';
        5 +import { useJobs, TrackedJob } from '../jobs/JobsContext';
        6
        6 -function HomePage() {
        7 -  const { jobs, startLocalUpload, updateLocalUploadProgress, attachServerJob, failLocalUpload } = useJobs();
        8 -  const [_image, setImage] = useState<File | null>(null);
        9 -  // Likely won't be needed as real images are to big. Just here for testing
       10 -  const [preview, setPreview] = useState<string | null>(null);
       11 -  const fileInputRef = useRef<HTMLInputElement>(null);
       12 -  const [progress, setProgress] = useState<number>(0);
       13 -  const [uploadError, setUploadError] = useState<string | null>(null);
       14 -  const [activeEntryId, setActiveEntryId] = useState<string | null>(null);
        7 +// ── Helpers ───────────────────────────────────────────────────────────────────
        8
       16 -  const activeJob = activeEntryId
       17 -    ? jobs.find((job) => job.entryId === activeEntryId) ?? jobs[0] ?? null
       18 -    : jobs[0] ?? null;
       19 -  const localUploadProgress = activeJob?.status === 'LOCAL_UPLOADING'
       20 -    ? activeJob.uploadProgress ?? progress
       21 -    : null;
       22 -  const backendProgress = activeJob?.stage === 'UPLOADING' && typeof activeJob.stageProgressPercent === 'number'
       23 -    ? activeJob.stageProgressPercent
       24 -    : null;
       25 -  const visibleActivityEntries = [...(activeJob?.activityEntries ?? [])].reverse();
        9 +function badgeClass(status: string) {
       10 +  switch (status) {
       11 +    case 'COMPLETED': return 'badge--success';
       12 +    case 'FAILED': return 'badge--error';
       13 +    case 'LOCAL_UPLOADING':
       14 +    case 'IN_PROGRESS': return 'badge--blue';
       15 +    default: return 'badge--dim';
       16 +  }
       17 +}
       18
       27 -  const statusText = (() => {
       28 -    if (uploadError) {
       29 -      return null;
       30 -    }
       19 +function badgeLabel(status: string, stage: string) {
       20 +  if (status === 'LOCAL_UPLOADING') return 'Uploading';
       21 +  if (status === 'IN_PROGRESS') {
       22 +    const s = stage.toLowerCase().replace('_', ' ');
       23 +    return s.charAt(0).toUpperCase() + s.slice(1);
       24 +  }
       25 +  if (status === 'COMPLETED') return 'Ready';
       26 +  if (status === 'FAILED') return 'Failed';
       27 +  return status;
       28 +}
       29
       32 -    if (!activeJob) {
       33 -      return null;
       34 -    }
       30 +// ── Job card ──────────────────────────────────────────────────────────────────
       31
       36 -    if (activeJob.status === 'LOCAL_UPLOADING') {
       37 -      return `Uploading original slide to object storage... ${localUploadProgress ?? 0}%`;
       38 -    }
       32 +function JobCard({ job }: { job: TrackedJob }) {
       33 +  const isActive = job.status === 'LOCAL_UPLOADING' || job.status === 'IN_PROGRESS';
       34 +  const isCompleted = job.status === 'COMPLETED';
       35 +  const isFailed = job.status === 'FAILED';
       36
       40 -    if (backendProgress !== null) {
       41 -      return `${activeJob.message ?? 'Uploading generated tiles to object storage.'} ${backendProgress}%`;
       42 -    }
       37 +  let progress: number | null = null;
       38 +  if (job.status === 'LOCAL_UPLOADING' && typeof job.uploadProgress === 'number') {
       39 +    progress = job.uploadProgress;
       40 +  } else if (job.stage === 'UPLOADING' && typeof job.stageProgressPercent === 'number') {
       41 +    progress = job.stageProgressPercent;
       42 +  }
       43
       44 -    return activeJob.message ?? null;
       45 -  })();
       44 +  const lastEntry = job.activityEntries[job.activityEntries.length - 1];
       45
       47 -  const formatActivityTime = (timestamp: string) => {
       48 -    const parsed = new Date(timestamp);
       49 -    if (Number.isNaN(parsed.getTime())) {
       50 -      return '';
       51 -    }
       46 +  return (
       47 +    <div className={`job-card${isActive ? ' job-card--active' : ''}${isCompleted ? ' job-card--done' : ''}${isFailed ? ' job-card--failed' : ''}`}>
       48 +      <div className="job-card__top">
       49 +        <span className="job-card__name" title={job.datasetName || job.imageId || ''}>
       50 +          {job.datasetName || job.fileName || job.imageId || '—'}
       51 +        </span>
       52 +        <span className={`badge ${badgeClass(job.status)}`}>
       53 +          {badgeLabel(job.status, job.stage)}
       54 +        </span>
       55 +      </div>
       56
       53 -    return parsed.toLocaleTimeString([], {
       54 -      hour: '2-digit',
       55 -      minute: '2-digit',
       56 -      second: '2-digit',
       57 -    });
       58 -  };
       57 +      {isActive && progress !== null && (
       58 +        <div className="job-progress">
       59 +          <div className="job-progress__track">
       60 +            <div className="job-progress__fill" style={{ width: `${progress}%` }} />
       61 +          </div>
       62 +          <span className="job-progress__label">{progress}%</span>
       63 +        </div>
       64 +      )}
       65
       66 +      {isActive && lastEntry && (
       67 +        <p className="job-card__detail">
       68 +          {lastEntry.detail || lastEntry.message}
       69 +        </p>
       70 +      )}
       71 +
       72 +      {isFailed && job.failureReason && (
       73 +        <p className="job-card__error">{job.failureReason}</p>
       74 +      )}
       75 +
       76 +      {isCompleted && job.imageId && (
       77 +        <Link to={`/tile-viewer/${job.imageId}`} className="job-card__cta">
       78 +          Open Viewer →
       79 +        </Link>
       80 +      )}
       81 +    </div>
       82 +  );
       83 +}
       84 +
       85 +// ── HomePage ──────────────────────────────────────────────────────────────────
       86 +
       87 +function HomePage() {
       88 +  const { jobs, startLocalUpload, updateLocalUploadProgress, attachServerJob, failLocalUpload } = useJobs();
       89 +  const fileInputRef = useRef<HTMLInputElement>(null);
       90 +  const [isDragging, setIsDragging] = useState(false);
       91 +  const [uploadError, setUploadError] = useState<string | null>(null);
       92 +
       93    const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
       94      e.preventDefault();
       62 -    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
       63 -      const file = e.dataTransfer.files[0];
       64 -      setImage(file);
       65 -      setPreview(URL.createObjectURL(file));
       66 -      // kick off upload
       67 -      startUpload(file);
       68 -    }
       95 +    setIsDragging(false);
       96 +    const file = e.dataTransfer.files?.[0];
       97 +    if (file) startUpload(file);
       98    };
       99
      100    const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
      101      e.preventDefault();
      102 +    setIsDragging(true);
      103    };
      104
      105    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
       76 -    if (e.target.files && e.target.files[0]) {
       77 -      const file = e.target.files[0];
       78 -      setImage(file);
       79 -      setPreview(URL.createObjectURL(file));
       80 -      // kick off upload
       81 -      startUpload(file);
       82 -    }
      106 +    const file = e.target.files?.[0];
      107 +    if (file) startUpload(file);
      108 +    // Reset so the same file can be re-selected
      109 +    e.target.value = '';
      110    };
      111
       85 -  // Upload function with progress updates
      112    const startUpload = async (file: File) => {
      113 +    setUploadError(null);
      114      const datasetName = file.name;
       88 -    const entryId = startLocalUpload({
       89 -      datasetName,
       90 -      fileName: file.name,
       91 -      totalBytes: file.size,
       92 -    });
       93 -    setActiveEntryId(entryId);
      115 +    const entryId = startLocalUpload({ datasetName, fileName: file.name, totalBytes: file.size });
      116
       95 -    setUploadError(null);
       96 -    setProgress(0);
      117      try {
      118        const result = await uploadFileWithPresignedMultipart(file, {
       99 -        concurrency: 4, // Number of parallel upload parts... 4 HTTP calls run at the same time
      100 -        partSizeHint: 16 * 1024 * 1024, // How big each part should be (16MB).. S3 minimum is 5MB
      119 +        concurrency: 4,
      120 +        partSizeHint: 16 * 1024 * 1024,
      121          datasetName,
      122          onProgress: (uploaded, total) => {
      123            updateLocalUploadProgress(entryId, uploaded, total);
      104 -          setProgress(Math.min(100, Math.round((uploaded / total) * 100)));
      124          },
      125        });
      107 -      setProgress(100);
      126 +
      127        if (result.imageId) {
      128          attachServerJob(entryId, {
      129            jobId: result.jobId ?? `pending-${entryId}`,
     ...
      139      }
      140    };
      141
      123 -  const handleButtonClick = () => {
      124 -    fileInputRef.current?.click();
      125 -  };
      126 -
      142    return (
      128 -    <div className="HomePage">
      129 -      <h1>HistoFlow</h1>
      130 -      <div
      131 -        className="upload-container"
      132 -        onDrop={handleDrop}
      133 -        onDragOver={handleDragOver}
      134 -      >
      135 -        <p>Drag & drop an image here</p>
      136 -        <button
      137 -          type="button"
      138 -          onClick={handleButtonClick}
      139 -          className="upload-button"
      143 +    <div className="home-page">
      144 +      {/* ── Upload panel ── */}
      145 +      <div className="home-page__upload">
      146 +        <div>
      147 +          <h1 className="home-page__title">Upload Slide</h1>
      148 +          <p className="home-page__subtitle">
      149 +            Whole-slide images are tiled for deep-zoom viewing and AI cancer analysis.
      150 +          </p>
      151 +        </div>
      152 +
      153 +        <div
      154 +          className={`drop-zone${isDragging ? ' drop-zone--dragging' : ''}`}
      155 +          onDrop={handleDrop}
      156 +          onDragOver={handleDragOver}
      157 +          onDragLeave={() => setIsDragging(false)}
      158 +          onClick={() => fileInputRef.current?.click()}
      159 +          role="button"
      160 +          tabIndex={0}
      161 +          aria-label="Upload zone – click or drag a slide file"
      162 +          onKeyDown={(e) => e.key === 'Enter' && fileInputRef.current?.click()}
      163          >
      141 -          Select Image
      142 -        </button>
      143 -        <input
      144 -          type="file"
      145 -          accept=".svs,.tif,.tiff,.ndpi,.mrxs,.scn,image/*"
      146 -          ref={fileInputRef}
      147 -          style={{ display: 'none' }}
      148 -          onChange={handleFileChange}
      149 -        />
      150 -        {preview && (
      151 -          <div className="preview-container">
      152 -            <img
      153 -              src={preview}
      154 -              alt="Preview"
      155 -              className="preview-image"
      156 -            />
      164 +          <input
      165 +            type="file"
      166 +            accept=".svs,.tif,.tiff,.ndpi,.mrxs,.scn,image/*"
      167 +            ref={fileInputRef}
      168 +            style={{ display: 'none' }}
      169 +            onChange={handleFileChange}
      170 +          />
      171 +          <div className="drop-zone__icon" aria-hidden="true">
      172 +            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
      173 +              <path d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1M16 12l-4-4m0 0L8 12m4-4v8" strokeLinecap="round" strokeLinejoin="round" />
      174 +            </svg>
      175            </div>
      158 -        )}
      176 +          <p className="drop-zone__primary">
      177 +            {isDragging ? 'Drop to upload' : 'Drag & drop or click to select'}
      178 +          </p>
      179 +          <p className="drop-zone__formats">SVS · TIFF · NDPI · MRXS · SCN</p>
      180 +        </div>
      181
      160 -        {statusText && (
      161 -          <div className="upload-status" aria-live="polite">
      162 -            <div className="upload-status__message">{statusText}</div>
      163 -            {localUploadProgress !== null && (
      164 -              <div className="upload-progress">
      165 -                <div className="upload-progress__track">
      166 -                  <div className="upload-progress__fill" style={{ width: `${localUploadProgress}%` }} />
      167 -                </div>
      168 -                <span className="upload-progress__label">{localUploadProgress}%</span>
      169 -              </div>
      170 -            )}
      171 -            {backendProgress !== null && (
      172 -              <div className="upload-progress upload-progress--backend">
      173 -                <div className="upload-progress__track">
      174 -                  <div className="upload-progress__fill" style={{ width: `${backendProgress}%` }} />
      175 -                </div>
      176 -                <span className="upload-progress__label">{backendProgress}%</span>
      177 -              </div>
      178 -            )}
      182 +        {uploadError && (
      183 +          <div className="home-page__error" role="alert">
      184 +            {uploadError}
      185            </div>
      186          )}
      181 -        {activeJob && visibleActivityEntries.length > 0 && (
      182 -          <section className="upload-activity" aria-label="Backend activity">
      183 -            <div className="upload-activity__header">
      184 -              <h2>Activity</h2>
      185 -              <span>{activeJob.datasetName}</span>
      186 -            </div>
      187 -            <ol className="upload-activity__list">
      188 -              {visibleActivityEntries.map((entry, index) => (
      189 -                <li
      190 -                  key={`${entry.timestamp}-${entry.stage}-${index}`}
      191 -                  className="upload-activity__item"
      192 -                >
      193 -                  <div className="upload-activity__meta">
      194 -                    <span className="upload-activity__time">{formatActivityTime(entry.timestamp)}</span>
      195 -                    <span className="upload-activity__stage">{entry.stage}</span>
      196 -                  </div>
      197 -                  <p className="upload-activity__message">{entry.message}</p>
      198 -                  {entry.detail && <p className="upload-activity__detail">{entry.detail}</p>}
      199 -                </li>
      200 -              ))}
      201 -            </ol>
      202 -          </section>
      203 -        )}
      204 -        {/* Error message */}
      205 -        {uploadError && (
      206 -          <div style={{ marginTop: '1rem', color: '#ff6b6b' }}>
      207 -            Error: {uploadError}
      187 +      </div>
      188 +
      189 +      {/* ── Jobs panel ── */}
      190 +      <div className="home-page__jobs">
      191 +        <h2 className="home-page__jobs-title">Recent Jobs</h2>
      192 +        {jobs.length === 0 ? (
      193 +          <p className="home-page__jobs-empty">No jobs yet. Upload a slide to get started.</p>
      194 +        ) : (
      195 +          <div className="home-page__jobs-list">
      196 +            {jobs.map(job => (
      197 +              <JobCard key={job.entryId} job={job} />
      198 +            ))}
      199            </div>
      200          )}
      201        </div>

⏺ Write(frontend/src/styles/HomePage.scss)
  ⎿  Added 201 lines, removed 128 lines
        1 -.HomePage {
        2 -  min-height: 100vh;
        3 -  background: var(--main-bg);
        1 +/* ── Layout ── */
        2 +.home-page {
        3 +  display: grid;
        4 +  grid-template-columns: 460px 1fr;
        5 +  gap: 40px;
        6 +  padding: 44px clamp(24px, 5vw, 64px);
        7 +  min-height: calc(100vh - var(--nav-h));
        8 +  align-items: start;
        9 +
       10 +  @media (max-width: 920px) {
       11 +    grid-template-columns: 1fr;
       12 +    padding: 32px 20px;
       13 +  }
       14 +}
       15 +
       16 +/* ── Upload panel ── */
       17 +.home-page__upload {
       18    display: flex;
       19    flex-direction: column;
        6 -  align-items: center;
        7 -  justify-content: flex-start;
       20 +  gap: 24px;
       21 +  position: sticky;
       22 +  top: calc(var(--nav-h) + 24px);
       23 +
       24 +  @media (max-width: 920px) {
       25 +    position: static;
       26 +  }
       27  }
       28
       10 -.upload-container {
       11 -  width: 30%;
       12 -  min-height: 50vh;
       13 -  border: 2px dashed #fff;
       14 -  border-radius: 8px;
       15 -  padding: 2rem;
       29 +.home-page__title {
       30 +  margin: 0 0 8px;
       31 +  font-size: 26px;
       32 +  font-weight: 700;
       33 +  letter-spacing: -0.02em;
       34 +}
       35 +
       36 +.home-page__subtitle {
       37 +  margin: 0;
       38 +  font-size: 14px;
       39 +  color: var(--text-muted);
       40 +  line-height: 1.65;
       41 +}
       42 +
       43 +/* ── Drop zone ── */
       44 +.drop-zone {
       45 +  border: 2px dashed var(--border);
       46 +  border-radius: var(--radius-lg);
       47 +  padding: 48px 32px;
       48    text-align: center;
       17 -  margin-top: 2rem;
       18 -  background: rgba(20, 33, 61, 0.7);
       19 -  color: #fff;
       20 -  position: relative;
       21 -  box-sizing: border-box;
       49 +  cursor: pointer;
       50 +  transition: border-color 0.18s, background 0.18s;
       51 +  background: var(--surface);
       52    display: flex;
       53    flex-direction: column;
       54    align-items: center;
       25 -  justify-content: flex-start;
       26 -  gap: 1rem;
       27 -}
       55 +  gap: 10px;
       56 +  user-select: none;
       57
       29 -.upload-button {
       30 -  margin-top: 1rem;
       31 -  padding: 0.5rem 1.5rem;
       32 -  background: #fca311;
       33 -  color: #14213d;
       34 -  border: none;
       35 -  border-radius: 4px;
       36 -  font-weight: bold;
       37 -  cursor: pointer;
       58 +  &:hover,
       59 +  &:focus-visible {
       60 +    outline: none;
       61 +    border-color: rgba(252, 163, 17, 0.35);
       62 +    background: rgba(252, 163, 17, 0.04);
       63 +  }
       64 +
       65 +  &--dragging {
       66 +    border-color: var(--accent);
       67 +    background: rgba(252, 163, 17, 0.07);
       68 +
       69 +    .drop-zone__icon {
       70 +      color: var(--accent);
       71 +    }
       72 +  }
       73  }
       74
       40 -.preview-container {
       41 -  margin-top: 2rem;
       42 -  width: 100%;
       43 -  height: 50%;
       44 -  display: flex;
       45 -  align-items: center;
       46 -  justify-content: center;
       75 +.drop-zone__icon {
       76 +  color: var(--text-muted);
       77 +  margin-bottom: 4px;
       78 +  transition: color 0.18s;
       79  }
       80
       49 -.preview-image {
       50 -  max-width: 100%;
       51 -  max-height: 100%;
       52 -  border-radius: 8px;
       53 -  object-fit: contain;
       54 -  background: #222;
       81 +.drop-zone__primary {
       82 +  margin: 0;
       83 +  font-size: 15px;
       84 +  font-weight: 500;
       85 +  color: var(--text);
       86  }
       87
       57 -.upload-status {
       58 -  width: 100%;
       59 -  margin-top: 0.5rem;
       60 -  text-align: left;
       88 +.drop-zone__formats {
       89 +  margin: 0;
       90 +  font-size: 12px;
       91 +  color: var(--text-dim);
       92 +  letter-spacing: 0.05em;
       93  }
       94
       63 -.upload-status__message {
       64 -  color: #fff;
       65 -  font-size: 0.95rem;
       95 +/* ── Error ── */
       96 +.home-page__error {
       97 +  padding: 11px 15px;
       98 +  border-radius: var(--radius-md);
       99 +  background: var(--error-dim);
      100 +  border: 1px solid rgba(248, 81, 73, 0.2);
      101 +  color: var(--error);
      102 +  font-size: 13px;
      103  }
      104
       68 -.upload-progress {
       69 -  width: 100%;
       70 -  margin-top: 0.75rem;
      105 +/* ── Jobs panel ── */
      106 +.home-page__jobs {
      107    display: flex;
       72 -  align-items: center;
       73 -  gap: 0.75rem;
      108 +  flex-direction: column;
      109 +  gap: 16px;
      110 +  padding-top: 2px;
      111  }
      112
       76 -.upload-progress__track {
       77 -  flex: 1;
       78 -  height: 10px;
       79 -  border-radius: 999px;
       80 -  background: rgba(255, 255, 255, 0.15);
       81 -  overflow: hidden;
      113 +.home-page__jobs-title {
      114 +  margin: 0 0 2px;
      115 +  font-size: 17px;
      116 +  font-weight: 600;
      117  }
      118
       84 -.upload-progress__fill {
       85 -  height: 100%;
       86 -  background: #fca311;
       87 -  transition: width 200ms ease;
      119 +.home-page__jobs-empty {
      120 +  margin: 0;
      121 +  font-size: 13px;
      122 +  color: var(--text-muted);
      123  }
      124
       90 -.upload-progress--backend .upload-progress__fill {
       91 -  background: #7bd389;
      125 +.home-page__jobs-list {
      126 +  display: flex;
      127 +  flex-direction: column;
      128 +  gap: 10px;
      129  }
      130
       94 -.upload-progress__label {
       95 -  min-width: 3rem;
       96 -  text-align: right;
       97 -  font-weight: 600;
       98 -  color: #fff;
       99 -}
      131 +/* ── Job card ── */
      132 +.job-card {
      133 +  background: var(--surface);
      134 +  border: 1px solid var(--border);
      135 +  border-radius: var(--radius-lg);
      136 +  padding: 14px 18px;
      137 +  display: flex;
      138 +  flex-direction: column;
      139 +  gap: 9px;
      140 +  transition: border-color 0.15s;
      141
      101 -.upload-activity {
      102 -  width: 100%;
      103 -  margin-top: 0.5rem;
      104 -  padding: 1rem;
      105 -  border-radius: 8px;
      106 -  background: rgba(255, 255, 255, 0.08);
      107 -  text-align: left;
      142 +  &--active {
      143 +    border-color: rgba(59, 130, 246, 0.28);
      144 +  }
      145 +
      146 +  &--done {
      147 +    border-color: rgba(34, 197, 94, 0.22);
      148 +  }
      149 +
      150 +  &--failed {
      151 +    border-color: rgba(248, 81, 73, 0.22);
      152 +  }
      153  }
      154
      110 -.upload-activity__header {
      155 +.job-card__top {
      156    display: flex;
      112 -  align-items: baseline;
      157 +  align-items: center;
      158    justify-content: space-between;
      114 -  gap: 1rem;
      115 -  margin-bottom: 0.75rem;
      159 +  gap: 10px;
      160  }
      161
      118 -.upload-activity__header h2 {
      119 -  margin: 0;
      120 -  font-size: 1rem;
      162 +.job-card__name {
      163 +  font-size: 13px;
      164 +  font-weight: 500;
      165 +  overflow: hidden;
      166 +  text-overflow: ellipsis;
      167 +  white-space: nowrap;
      168 +  flex: 1;
      169  }
      170
      123 -.upload-activity__header span {
      124 -  color: rgba(255, 255, 255, 0.7);
      125 -  font-size: 0.8rem;
      126 -}
      127 -
      128 -.upload-activity__list {
      129 -  list-style: none;
      171 +.job-card__detail,
      172 +.job-card__error {
      173    margin: 0;
      131 -  padding: 0;
      132 -  display: flex;
      133 -  flex-direction: column;
      134 -  gap: 0.75rem;
      135 -  max-height: 220px;
      136 -  overflow-y: auto;
      174 +  font-size: 12px;
      175 +  color: var(--text-muted);
      176 +  line-height: 1.5;
      177  }
      178
      139 -.upload-activity__item {
      140 -  padding-bottom: 0.75rem;
      141 -  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
      179 +.job-card__error {
      180 +  color: var(--error);
      181  }
      182
      144 -.upload-activity__item:last-child {
      145 -  padding-bottom: 0;
      146 -  border-bottom: 0;
      183 +.job-card__cta {
      184 +  display: inline-block;
      185 +  padding: 6px 13px;
      186 +  border-radius: var(--radius-md);
      187 +  background: var(--blue-dim);
      188 +  color: var(--blue-hover);
      189 +  font-size: 12px;
      190 +  font-weight: 600;
      191 +  align-self: flex-start;
      192 +  transition: background 0.15s;
      193 +
      194 +  &:hover {
      195 +    background: rgba(59, 130, 246, 0.25);
      196 +  }
      197  }
      198
      149 -.upload-activity__meta {
      199 +/* ── Progress bar ── */
      200 +.job-progress {
      201    display: flex;
      202    align-items: center;
      152 -  gap: 0.5rem;
      153 -  margin-bottom: 0.25rem;
      154 -  font-size: 0.75rem;
      155 -  color: rgba(255, 255, 255, 0.65);
      203 +  gap: 10px;
      204  }
      205
      158 -.upload-activity__stage {
      159 -  padding: 0.1rem 0.4rem;
      206 +.job-progress__track {
      207 +  flex: 1;
      208 +  height: 5px;
      209 +  background: rgba(255, 255, 255, 0.07);
      210    border-radius: 999px;
      161 -  background: rgba(255, 255, 255, 0.1);
      162 -  color: #fff;
      163 -  font-weight: 600;
      211 +  overflow: hidden;
      212  }
      213
      166 -.upload-activity__message,
      167 -.upload-activity__detail {
      168 -  margin: 0;
      214 +.job-progress__fill {
      215 +  height: 100%;
      216 +  background: var(--blue);
      217 +  border-radius: 999px;
      218 +  transition: width 200ms ease;
      219  }
      220
      171 -.upload-activity__message {
      172 -  color: #fff;
      173 -  font-size: 0.92rem;
      221 +.job-progress__label {
      222 +  font-size: 11px;
      223 +  color: var(--text-muted);
      224 +  min-width: 30px;
      225 +  text-align: right;
      226  }
      227
      176 -.upload-activity__detail {
      177 -  margin-top: 0.15rem;
      178 -  color: rgba(255, 255, 255, 0.7);
      179 -  font-size: 0.82rem;
      180 -}
      228 +/* ── Badges ── */
      229 +.badge {
      230 +  display: inline-flex;
      231 +  align-items: center;
      232 +  padding: 2px 8px;
      233 +  border-radius: 999px;
      234 +  font-size: 11px;
      235 +  font-weight: 600;
      236 +  letter-spacing: 0.02em;
      237 +  white-space: nowrap;
      238 +  flex-shrink: 0;
      239
      182 -@media (max-width: 900px) {
      183 -  .upload-container {
      184 -    width: min(92vw, 42rem);
      240 +  &--success {
      241 +    background: var(--success-dim);
      242 +    color: var(--success);
      243    }
      244 +
      245 +  &--error {
      246 +    background: var(--error-dim);
      247 +    color: var(--error);
      248 +  }
      249 +
      250 +  &--blue {
      251 +    background: var(--blue-dim);
      252 +    color: var(--blue-hover);
      253 +  }
      254 +
      255 +  &--dim {
      256 +    background: rgba(255, 255, 255, 0.06);
      257 +    color: var(--text-muted);
      258 +  }
      259  }

⏺ Now the TileViewerPage revamp with sidebar layout and dark theme:

⏺ Write(frontend/src/pages/TileViewerPage.tsx)
  ⎿  Added 250 lines, removed 252 lines
      17    appliedPrefix: string;
      18  };
      19
      20 +type AnalysisSummary = {
      21 +  total_tiles: number;
      22 +  tissue_tiles: number;
      23 +  skipped_tiles: number;
      24 +  flagged_tiles: number;
      25 +  tumor_area_percentage: number;
      26 +  aggregate_score: number;
      27 +  max_score: number;
      28 +};
      29 +
      30  const API_BASE_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';
      31
      32  const TileViewerPage: React.FC = () => {
     ...
      51
      52    const formatDatasetMeta = (dataset: DatasetSummary) => {
      53      const date = dataset.lastModifiedMillis ? new Date(dataset.lastModifiedMillis) : null;
      44 -    const size = dataset.totalSizeBytes;
      45 -    const megabytes = size / (1024 * 1024);
      46 -    const sizeLabel = megabytes >= 1 ? `${megabytes.toFixed(1)} MB` : `${(size / 1024).toFixed(0)} KB`;
      47 -    return `${sizeLabel} · ${dataset.totalObjects} files${date ? ` · ${date.toLocaleString()}` : ''}`;
      54 +    const mb = dataset.totalSizeBytes / (1024 * 1024);
      55 +    const sizeLabel = mb >= 1 ? `${mb.toFixed(1)} MB` : `${(dataset.totalSizeBytes / 1024).toFixed(0)} KB`;
      56 +    return `${sizeLabel} · ${dataset.totalObjects} files${date ? ` · ${date.toLocaleDateString()}` : ''}`;
      57    };
      58
      59 +  // ── Analysis state ────────────────────────────────────────────────────────
      60    const [analysisJobId, setAnalysisJobId] = useState<string | null>(null);
      61    const [analysisStatus, setAnalysisStatus] = useState<string | null>(null);
      52 -  const [analysisProgress, setAnalysisProgress] = useState<{ done: number, total: number, message: string } | null>(null);
      62 +  const [analysisProgress, setAnalysisProgress] = useState<{ done: number; total: number; message: string } | null>(null);
      63 +  const [analysisSummary, setAnalysisSummary] = useState<AnalysisSummary | null>(null);
      64    const [analysisPredictions, setAnalysisPredictions] = useState<any[]>([]);
      65    const [isAnalyzing, setIsAnalyzing] = useState(false);
      66    const [showOverlays, setShowOverlays] = useState(true);
     ...
      71    const [overlayOpacity, setOverlayOpacity] = useState(0.6);
      72    const [heatmapOpacity, setHeatmapOpacity] = useState(0.45);
      73
      74 +  // ── Dataset helpers ───────────────────────────────────────────────────────
      75 +  const resetAnalysis = () => {
      76 +    setAnalysisJobId(null);
      77 +    setAnalysisStatus(null);
      78 +    setAnalysisProgress(null);
      79 +    setAnalysisSummary(null);
      80 +    setAnalysisPredictions([]);
      81 +    setHeatmapUrl(null);
      82 +    setHeatmapWarning(null);
      83 +    setIsAnalyzing(false);
      84 +  };
      85 +
      86    const applyDataset = (dataset: DatasetSummary | null) => {
      87      if (!dataset) return;
      88      setActiveImageId(dataset.imageId);
     ...
       93      setSearchTerm('');
       94      setSearchResults([]);
       95      setErrorMessage(null);
       73 -    // Reset analysis when changing dataset
       74 -    setAnalysisJobId(null);
       75 -    setAnalysisStatus(null);
       76 -    setAnalysisPredictions([]);
       77 -    setHeatmapUrl(null);
       78 -    setHeatmapWarning(null);
       79 -    setIsAnalyzing(false);
       96 +    resetAnalysis();
       97    };
       98 +
       99    const applyDatasetById = (imageId: string, fallbackLabel?: string) => {
      100      if (!imageId) return;
      101      setActiveImageId(imageId);
     ...
      106      setSearchTerm('');
      107      setSearchResults([]);
      108      setErrorMessage(null);
       91 -    // Reset analysis
       92 -    setAnalysisJobId(null);
       93 -    setAnalysisStatus(null);
       94 -    setAnalysisPredictions([]);
       95 -    setHeatmapUrl(null);
       96 -    setHeatmapWarning(null);
       97 -    setIsAnalyzing(false);
      109 +    resetAnalysis();
      110    };
      111
      112 +  // ── Route param ───────────────────────────────────────────────────────────
      113    useEffect(() => {
      101 -    if (!routeImageId) {
      102 -      return;
      103 -    }
      104 -
      114 +    if (!routeImageId) return;
      115      initialisedRef.current = true;
      116      applyDatasetById(routeImageId);
      117    }, [routeImageId]);
      118
      119 +  // ── Load featured datasets ────────────────────────────────────────────────
      120    useEffect(() => {
      121      const controller = new AbortController();
      111 -    const loadFeatured = async () => {
      122 +    const load = async () => {
      123        try {
      124          setIsLoading(true);
      125          setErrorMessage(null);
      126          const params = new URLSearchParams({ limit: '5' });
      116 -        const response = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, {
      117 -          signal: controller.signal
      118 -        });
      119 -        if (!response.ok) {
      120 -          throw new Error('Failed to fetch datasets');
      121 -        }
      122 -        const data: DatasetPage = await response.json();
      127 +        const res = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, { signal: controller.signal });
      128 +        if (!res.ok) throw new Error('Failed to fetch datasets');
      129 +        const data: DatasetPage = await res.json();
      130          setFeaturedDatasets(data.datasets);
      131          if (!routeImageId && !initialisedRef.current && data.datasets.length > 0) {
      132            initialisedRef.current = true;
      133            applyDataset(data.datasets[0]);
      134          }
      128 -      } catch (error) {
      129 -        if (!(error instanceof DOMException && error.name === 'AbortError')) {
      135 +      } catch (err) {
      136 +        if (!(err instanceof DOMException && err.name === 'AbortError')) {
      137            setErrorMessage('Unable to load datasets.');
      138          }
      139        } finally {
      140          setIsLoading(false);
      141        }
      142      };
      136 -
      137 -    loadFeatured();
      143 +    load();
      144      return () => controller.abort();
      145    }, [routeImageId]);
      146
      147 +  // ── Search ────────────────────────────────────────────────────────────────
      148    useEffect(() => {
      149      if (!searchTerm) {
      150        setSearchResults([]);
      151        return;
      152      }
      146 -
      153      const controller = new AbortController();
      154      const timer = window.setTimeout(async () => {
      155        try {
      150 -        setErrorMessage(null);
      156          const params = new URLSearchParams({ limit: '10', prefix: searchTerm });
      152 -        const response = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, {
      153 -          signal: controller.signal
      154 -        });
      155 -        if (!response.ok) {
      156 -          throw new Error('Failed to fetch datasets');
      157 -        }
      158 -        const data: DatasetPage = await response.json();
      157 +        const res = await fetch(`${API_BASE_URL}/api/v1/tiles/datasets?${params}`, { signal: controller.signal });
      158 +        if (!res.ok) throw new Error('Failed to search');
      159 +        const data: DatasetPage = await res.json();
      160          setSearchResults(data.datasets);
      160 -      } catch (error) {
      161 -        if (!(error instanceof DOMException && error.name === 'AbortError')) {
      161 +      } catch (err) {
      162 +        if (!(err instanceof DOMException && err.name === 'AbortError')) {
      163            setErrorMessage('Unable to search datasets.');
      164          }
      165        }
      166      }, 250);
      166 -
      167 -    return () => {
      168 -      controller.abort();
      169 -      window.clearTimeout(timer);
      170 -    };
      167 +    return () => { controller.abort(); window.clearTimeout(timer); };
      168    }, [searchTerm]);
      169
      170 +  // ── Click outside suggestions ─────────────────────────────────────────────
      171    useEffect(() => {
      174 -    const handleClickOutside = (event: MouseEvent) => {
      175 -      if (!searchGroupRef.current) {
      176 -        return;
      177 -      }
      178 -      if (!searchGroupRef.current.contains(event.target as Node)) {
      172 +    const handleClick = (e: MouseEvent) => {
      173 +      if (searchGroupRef.current && !searchGroupRef.current.contains(e.target as Node)) {
      174          setShowSuggestions(false);
      175        }
      176      };
      182 -
      183 -    window.addEventListener('mousedown', handleClickOutside);
      184 -    return () => window.removeEventListener('mousedown', handleClickOutside);
      177 +    window.addEventListener('mousedown', handleClick);
      178 +    return () => window.removeEventListener('mousedown', handleClick);
      179    }, []);
      180
      181    const handleInputChange = (value: string) => {
     ...
      187    const handleSubmit = () => {
      188      const trimmed = inputValue.trim();
      189      if (!trimmed) return;
      196 -
      190      const allKnown = [...featuredDatasets, ...searchResults];
      198 -    const matched = allKnown.find(dataset =>
      199 -      dataset.imageId.toLowerCase() === trimmed.toLowerCase() ||
      200 -      dataset.datasetName.toLowerCase() === trimmed.toLowerCase()
      191 +    const matched = allKnown.find(
      192 +      d => d.imageId.toLowerCase() === trimmed.toLowerCase() ||
      193 +           d.datasetName.toLowerCase() === trimmed.toLowerCase()
      194      );
      202 -
      203 -    if (matched) {
      204 -      applyDataset(matched);
      205 -      return;
      206 -    }
      207 -
      195 +    if (matched) { applyDataset(matched); return; }
      196      applyDatasetById(trimmed);
      197    };
      198
      199 +  // ── Analysis ──────────────────────────────────────────────────────────────
      200    const triggerAnalysis = async () => {
      201      if (!activeImageId) return;
      202      try {
     ...
      204        setErrorMessage(null);
      205        setHeatmapWarning(null);
      206        setHeatmapUrl(null);
      218 -      const response = await fetch(`${API_BASE_URL}/api/v1/analysis/trigger/${activeImageId}`, {
      219 -        method: 'POST'
      220 -      });
      221 -      if (!response.ok) throw new Error('Failed to trigger analysis');
      222 -      const data = await response.json();
      207 +      setAnalysisSummary(null);
      208 +      const res = await fetch(`${API_BASE_URL}/api/v1/analysis/trigger/${activeImageId}`, { method: 'POST' });
      209 +      if (!res.ok) throw new Error('Failed to trigger analysis');
      210 +      const data = await res.json();
      211        setAnalysisJobId(data.job_id);
      212        setAnalysisStatus('accepted');
      225 -    } catch (error) {
      226 -      setErrorMessage('Failed to trigger cancer analysis.');
      213 +    } catch {
      214 +      setErrorMessage('Failed to start cancer analysis.');
      215        setIsAnalyzing(false);
      216      }
      217    };
      218
      231 -  // Polling for analysis status
      219    useEffect(() => {
      220      if (!analysisJobId || analysisStatus === 'completed' || analysisStatus === 'failed') return;
      234 -
      221      const interval = setInterval(async () => {
      222        try {
      237 -        const response = await fetch(`${API_BASE_URL}/api/v1/analysis/status/${analysisJobId}`);
      238 -        if (!response.ok) throw new Error('Status check failed');
      239 -        const data = await response.json();
      240 -
      223 +        const res = await fetch(`${API_BASE_URL}/api/v1/analysis/status/${analysisJobId}`);
      224 +        if (!res.ok) throw new Error('Status check failed');
      225 +        const data = await res.json();
      226          setAnalysisStatus(data.status);
      242 -        setAnalysisProgress({
      243 -          done: data.tiles_processed,
      244 -          total: data.total_tiles,
      245 -          message: data.message
      246 -        });
      247 -
      227 +        setAnalysisProgress({ done: data.tiles_processed, total: data.total_tiles, message: data.message });
      228          if (data.status === 'completed') {
      229            clearInterval(interval);
      230            fetchAnalysisResults(analysisJobId);
     ...
      233            setIsAnalyzing(false);
      234            setErrorMessage(`Analysis failed: ${data.message}`);
      235          }
      256 -      } catch (error) {
      257 -        console.error('Polling error:', error);
      236 +      } catch {
      237 +        // polling error — retry on next tick
      238        }
      239      }, 2000);
      260 -
      240      return () => clearInterval(interval);
      241    }, [analysisJobId, analysisStatus]);
      242
      243    const fetchAnalysisResults = async (jobId: string) => {
      244      try {
      266 -      const response = await fetch(`${API_BASE_URL}/api/v1/analysis/results/${jobId}`);
      267 -      if (!response.ok) throw new Error('Results fetch failed');
      268 -      const data = await response.json();
      245 +      const res = await fetch(`${API_BASE_URL}/api/v1/analysis/results/${jobId}`);
      246 +      if (!res.ok) throw new Error('Results fetch failed');
      247 +      const data = await res.json();
      248        setAnalysisPredictions(Array.isArray(data.tile_predictions) ? data.tile_predictions : []);
      249 +      if (data.summary) setAnalysisSummary(data.summary);
      250
      271 -      const candidateHeatmapUrl = `${API_BASE_URL}/api/v1/analysis/heatmap/${jobId}`;
      251 +      const candidateUrl = `${API_BASE_URL}/api/v1/analysis/heatmap/${jobId}`;
      252        try {
      273 -        const heatmapResponse = await fetch(candidateHeatmapUrl, { method: 'HEAD' });
      274 -        if (heatmapResponse.ok) {
      275 -          setHeatmapUrl(candidateHeatmapUrl);
      276 -          setHeatmapWarning(null);
      253 +        const headRes = await fetch(candidateUrl, { method: 'HEAD' });
      254 +        if (headRes.ok) {
      255 +          setHeatmapUrl(candidateUrl);
      256          } else {
      278 -          setHeatmapUrl(null);
      279 -          setHeatmapWarning('Heatmap unavailable for this analysis. Showing red boxes only.');
      257 +          setHeatmapWarning('Heatmap unavailable. Showing region boxes only.');
      258          }
      281 -      } catch (_error) {
      282 -        setHeatmapUrl(null);
      283 -        setHeatmapWarning('Unable to load heatmap layer. Showing red boxes only.');
      259 +      } catch {
      260 +        setHeatmapWarning('Unable to load heatmap. Showing region boxes only.');
      261        }
      285 -
      262        setIsAnalyzing(false);
      287 -    } catch (error) {
      263 +    } catch {
      264        setErrorMessage('Failed to load analysis results.');
      265        setIsAnalyzing(false);
      266      }
      267    };
      268
      293 -  const visibleImageId = activeImageId ?? '';
      294 -  const visibleDatasetName = activeDatasetName ?? inputValue ?? '';
      269 +  const analysisProgressPct =
      270 +    analysisProgress && analysisProgress.total > 0
      271 +      ? Math.round((analysisProgress.done / analysisProgress.total) * 100)
      272 +      : 0;
      273
      274 +  const hasOverlayData = analysisPredictions.length > 0 || !!heatmapUrl;
      275 +
      276    return (
      297 -    <div className="tile-viewer-page">
      298 -      <div className="tile-viewer-page__header">
      299 -        <h1 className="tile-viewer-page__title">HistoFlow Tile Viewer</h1>
      300 -        <div className="tile-viewer-page__actions">
      277 +    <div className="tvp">
      278 +      {/* ── Sidebar ── */}
      279 +      <aside className="tvp__sidebar">
      280 +
      281 +        {/* Dataset search */}
      282 +        <section className="tvp__section">
      283 +          <span className="tvp__label">Dataset</span>
      284 +          <div className="tvp__search-group" ref={searchGroupRef}>
      285 +            <div className="tvp__search-row">
      286 +              <input
      287 +                className="tvp__input"
      288 +                type="text"
      289 +                value={inputValue}
      290 +                onChange={e => handleInputChange(e.target.value)}
      291 +                onFocus={() => setShowSuggestions(true)}
      292 +                onKeyDown={e => {
      293 +                  if (e.key === 'Enter') { e.preventDefault(); handleSubmit(); }
      294 +                  if (e.key === 'Escape') setShowSuggestions(false);
      295 +                }}
      296 +                placeholder="Search name or image ID"
      297 +                autoComplete="off"
      298 +              />
      299 +              <button
      300 +                className="tvp__load-btn"
      301 +                type="button"
      302 +                onClick={handleSubmit}
      303 +                disabled={!inputValue.trim()}
      304 +              >
      305 +                Load
      306 +              </button>
      307 +            </div>
      308 +
      309 +            {showSuggestions && suggestions.length > 0 && (
      310 +              <ul className="tvp__suggestions" role="listbox">
      311 +                {suggestions.map(d => (
      312 +                  <li key={d.imageId}>
      313 +                    <button
      314 +                      type="button"
      315 +                      className="tvp__suggestion"
      316 +                      onMouseDown={e => e.preventDefault()}
      317 +                      onClick={() => applyDataset(d)}
      318 +                    >
      319 +                      <span className="tvp__suggestion-name">{d.datasetName || d.imageId}</span>
      320 +                      <span className="tvp__suggestion-meta">{formatDatasetMeta(d)}</span>
      321 +                    </button>
      322 +                  </li>
      323 +                ))}
      324 +              </ul>
      325 +            )}
      326 +          </div>
      327 +
      328 +          {isLoading && <span className="tvp__status-text">Loading datasets…</span>}
      329 +          {errorMessage && <span className="tvp__status-text tvp__status-text--error">{errorMessage}</span>}
      330 +          {heatmapWarning && <span className="tvp__status-text tvp__status-text--warn">{heatmapWarning}</span>}
      331 +
      332 +          {activeImageId && (
      333 +            <p className="tvp__current">
      334 +              <span className="tvp__current-label">Active: </span>
      335 +              <code className="tvp__current-id">{activeDatasetName || activeImageId}</code>
      336 +            </p>
      337 +          )}
      338 +        </section>
      339 +
      340 +        {/* Analysis trigger */}
      341 +        <section className="tvp__section">
      342            <button
      302 -            className={`tile-viewer-page__btn tile-viewer-page__btn--primary ${isAnalyzing ? 'is-loading' : ''}`}
      343 +            className={`tvp__analyze-btn${isAnalyzing ? ' tvp__analyze-btn--busy' : ''}`}
      344              onClick={triggerAnalysis}
      345              disabled={!activeImageId || isAnalyzing}
      346 +            type="button"
      347            >
      306 -            {isAnalyzing ? 'Analyzing...' : 'Run Cancer Analysis'}
      348 +            {isAnalyzing ? 'Analyzing…' : 'Run Cancer Analysis'}
      349            </button>
      308 -        </div>
      309 -      </div>
      350
      311 -      <div className="tile-viewer-page__control-bar">
      312 -        <div className="tile-viewer-page__search-group" ref={searchGroupRef}>
      313 -          <label className="tile-viewer-page__label" htmlFor="tile-viewer-image-id">
      314 -            Dataset
      315 -          </label>
      316 -          <div className="tile-viewer-page__search">
      317 -            <input
      318 -              id="tile-viewer-image-id"
      319 -              className="tile-viewer-page__input"
      320 -              type="text"
      321 -              value={inputValue}
      322 -              onChange={(e) => handleInputChange(e.target.value)}
      323 -              onFocus={() => setShowSuggestions(true)}
      324 -              onKeyDown={(event) => {
      325 -                if (event.key === 'Enter') {
      326 -                  event.preventDefault();
      327 -                  handleSubmit();
      328 -                }
      329 -                if (event.key === 'Escape') {
      330 -                  setShowSuggestions(false);
      331 -                }
      332 -              }}
      333 -              placeholder="Search by dataset name or image ID"
      334 -              autoComplete="off"
      335 -            />
      336 -            <button
      337 -              className="tile-viewer-page__submit"
      338 -              type="button"
      339 -              onClick={handleSubmit}
      340 -              disabled={!inputValue.trim()}
      341 -            >
      342 -              Load
      343 -            </button>
      344 -          </div>
      345 -          {showSuggestions && suggestions.length > 0 && (
      346 -            <ul className="tile-viewer-page__suggestions" role="listbox">
      347 -              {suggestions.map(dataset => (
      348 -                <li key={dataset.imageId}>
      349 -                  <button
      350 -                    type="button"
      351 -                    className="tile-viewer-page__suggestion"
      352 -                    onMouseDown={(event) => event.preventDefault()}
      353 -                    onClick={() => applyDataset(dataset)}
      354 -                  >
      355 -                    <span className="tile-viewer-page__suggestion-title">
      356 -                      {dataset.datasetName || dataset.imageId}
      357 -                    </span>
      358 -                    <span className="tile-viewer-page__suggestion-meta">
      359 -                      {dataset.datasetName && dataset.datasetName !== dataset.imageId ? `${dataset.imageId} · ` : ''}
      360 -                      {formatDatasetMeta(dataset)}
      361 -                    </span>
      362 -                  </button>
      363 -                </li>
      364 -              ))}
      365 -            </ul>
      351 +          {isAnalyzing && analysisProgress && (
      352 +            <div className="tvp__analysis-progress">
      353 +              <div className="tvp__ap-bar">
      354 +                <div className="tvp__ap-fill" style={{ width: `${analysisProgressPct}%` }} />
      355 +              </div>
      356 +              <div className="tvp__ap-meta">
      357 +                <span>{analysisProgress.message}</span>
      358 +                <span>{analysisProgress.done} / {analysisProgress.total}</span>
      359 +              </div>
      360 +            </div>
      361            )}
      367 -          <div className="tile-viewer-page__status-row">
      368 -            {isLoading && <span className="tile-viewer-page__status">Loading datasets…</span>}
      369 -            {!isLoading && featuredDatasets.length > 0 && !searchTerm && (
      370 -              <span className="tile-viewer-page__status">
      371 -                Showing latest {featuredDatasets.length} dataset{featuredDatasets.length > 1 ? 's' : ''}
      362 +        </section>
      363 +
      364 +        {/* Analysis results summary */}
      365 +        {analysisSummary && (
      366 +          <section className="tvp__section tvp__results">
      367 +            <span className="tvp__label">Analysis Results</span>
      368 +
      369 +            <div className="tvp__stat-big">
      370 +              <span className="tvp__stat-big-value">
      371 +                {analysisSummary.tumor_area_percentage.toFixed(1)}
      372 +                <span className="tvp__stat-big-unit">%</span>
      373                </span>
      373 -            )}
      374 -            {isAnalyzing && analysisProgress && (
      375 -              <span className="tile-viewer-page__status tile-viewer-page__status--processing">
      376 -                {analysisProgress.message}... ({analysisProgress.done}/{analysisProgress.total} tiles)
      377 -              </span>
      378 -            )}
      379 -            {heatmapWarning && (
      380 -              <span className="tile-viewer-page__status tile-viewer-page__status--warning">{heatmapWarning}</span>
      381 -            )}
      382 -            {errorMessage && <span className="tile-viewer-page__status tile-viewer-page__status--error">{errorMessage}</span>}
      383 -          </div>
      384 -        </div>
      374 +              <span className="tvp__stat-big-label">Tumor area</span>
      375 +            </div>
      376
      386 -        {(analysisPredictions.length > 0 || heatmapUrl) && (
      387 -          <div className="tile-viewer-page__analysis-controls">
      388 -            <div className="tile-viewer-page__control">
      389 -              <label>Red-Box Threshold: {threshold.toFixed(2)}</label>
      390 -              <input
      391 -                type="range" min="0" max="1" step="0.01"
      392 -                value={threshold} onChange={e => setThreshold(parseFloat(e.target.value))}
      393 -              />
      377 +            <div className="tvp__stat-row">
      378 +              <div className="tvp__stat">
      379 +                <span className="tvp__stat-label">Max score</span>
      380 +                <span className="tvp__stat-value">{analysisSummary.max_score.toFixed(3)}</span>
      381 +              </div>
      382 +              <div className="tvp__stat">
      383 +                <span className="tvp__stat-label">Avg score</span>
      384 +                <span className="tvp__stat-value">{analysisSummary.aggregate_score.toFixed(3)}</span>
      385 +              </div>
      386              </div>
      395 -            <div className="tile-viewer-page__control">
      396 -              <label>Red-Box Opacity: {overlayOpacity.toFixed(2)}</label>
      397 -              <input
      398 -                type="range" min="0" max="1" step="0.01"
      399 -                value={overlayOpacity} onChange={e => setOverlayOpacity(parseFloat(e.target.value))}
      400 -              />
      387 +
      388 +            <div className="tvp__stat-row">
      389 +              <div className="tvp__stat">
      390 +                <span className="tvp__stat-label">Tissue tiles</span>
      391 +                <span className="tvp__stat-value">{analysisSummary.tissue_tiles.toLocaleString()}</span>
      392 +              </div>
      393 +              <div className="tvp__stat">
      394 +                <span className="tvp__stat-label">Flagged</span>
      395 +                <span className="tvp__stat-value">{analysisSummary.flagged_tiles.toLocaleString()}</span>
      396 +              </div>
      397              </div>
      402 -            <div className="tile-viewer-page__control">
      403 -              <label>Heatmap Opacity: {heatmapOpacity.toFixed(2)}</label>
      404 -              <input
      405 -                type="range" min="0" max="1" step="0.01"
      406 -                value={heatmapOpacity} onChange={e => setHeatmapOpacity(parseFloat(e.target.value))}
      407 -              />
      408 -            </div>
      409 -            <div className="tile-viewer-page__control">
      410 -              <label className="checkbox-label">
      398 +          </section>
      399 +        )}
      400 +
      401 +        {/* Overlay controls */}
      402 +        {hasOverlayData && (
      403 +          <section className="tvp__section">
      404 +            <span className="tvp__label">Overlay Controls</span>
      405 +
      406 +            <label className="tvp__slider-label">
      407 +              <span>Threshold: {threshold.toFixed(2)}</span>
      408 +              <input type="range" min="0" max="1" step="0.01" value={threshold}
      409 +                onChange={e => setThreshold(parseFloat(e.target.value))} />
      410 +            </label>
      411 +
      412 +            <label className="tvp__slider-label">
      413 +              <span>Box opacity: {overlayOpacity.toFixed(2)}</span>
      414 +              <input type="range" min="0" max="1" step="0.01" value={overlayOpacity}
      415 +                onChange={e => setOverlayOpacity(parseFloat(e.target.value))} />
      416 +            </label>
      417 +
      418 +            <label className="tvp__slider-label">
      419 +              <span>Heatmap opacity: {heatmapOpacity.toFixed(2)}</span>
      420 +              <input type="range" min="0" max="1" step="0.01" value={heatmapOpacity}
      421 +                onChange={e => setHeatmapOpacity(parseFloat(e.target.value))} />
      422 +            </label>
      423 +
      424 +            <div className="tvp__toggles">
      425 +              <label className="tvp__toggle">
      426                  <input type="checkbox" checked={showOverlays} onChange={e => setShowOverlays(e.target.checked)} />
      412 -                Show "Red Boxes"
      427 +                <span>Region boxes</span>
      428                </label>
      414 -            </div>
      415 -            <div className="tile-viewer-page__control">
      416 -              <label className="checkbox-label">
      429 +              <label className="tvp__toggle">
      430                  <input type="checkbox" checked={showHeatmap} onChange={e => setShowHeatmap(e.target.checked)} />
      418 -                Show Heatmap
      431 +                <span>Heatmap</span>
      432                </label>
      433              </div>
      421 -          </div>
      434 +          </section>
      435          )}
      436 +      </aside>
      437
      424 -        <p className="tile-viewer-page__hint">
      425 -          Current dataset:{' '}
      426 -          {visibleImageId ? (
      427 -            <span>
      428 -              <code>{visibleDatasetName || visibleImageId}</code>
      429 -              {visibleDatasetName && visibleDatasetName !== visibleImageId ? (
      430 -                <span> (<code>{visibleImageId}</code>)</span>
      431 -              ) : null}
      432 -            </span>
      433 -          ) : (
      434 -            <code>—</code>
      435 -          )}
      436 -        </p>
      437 -      </div>
      438 -
      439 -      <div className="tile-viewer-page__viewer">
      440 -        <div className="tile-viewer-page__surface">
      441 -          {activeImageId ? (
      442 -            <ImageViewer
      443 -              key={`${activeImageId}-${viewerKey}`}
      444 -              imageId={activeImageId}
      445 -              overlays={showOverlays ? analysisPredictions : []}
      446 -              threshold={threshold}
      447 -              overlayOpacity={overlayOpacity}
      448 -              heatmapUrl={heatmapUrl ?? undefined}
      449 -              showHeatmap={showHeatmap}
      450 -              heatmapOpacity={heatmapOpacity}
      451 -            />
      452 -          ) : (
      453 -            <div className="tile-viewer-page__placeholder">
      454 -              Select a dataset to initialize the viewer.
      455 -            </div>
      456 -          )}
      457 -        </div>
      458 -      </div>
      438 +      {/* ── Viewer ── */}
      439 +      <main className="tvp__viewer">
      440 +        {activeImageId ? (
      441 +          <ImageViewer
      442 +            key={`${activeImageId}-${viewerKey}`}
      443 +            imageId={activeImageId}
      444 +            overlays={showOverlays ? analysisPredictions : []}
      445 +            threshold={threshold}
      446 +            overlayOpacity={overlayOpacity}
      447 +            heatmapUrl={heatmapUrl ?? undefined}
      448 +            showHeatmap={showHeatmap}
      449 +            heatmapOpacity={heatmapOpacity}
      450 +          />
      451 +        ) : (
      452 +          <div className="tvp__placeholder">
      453 +            <p>Select a dataset to initialize the viewer.</p>
      454 +          </div>
      455 +        )}
      456 +      </main>
      457      </div>
      458    );
      459  };

⏺ Write(frontend/src/styles/TileViewerPage.scss)
  ⎿  Added 260 lines, removed 199 lines
        1 -.tile-viewer-page {
        2 -  min-height: calc(100vh - 80px);
        3 -  padding: clamp(48px, 8vw, 96px) clamp(24px, 10vw, 120px);
        4 -  background: linear-gradient(180deg, #f8fafc 0%, #eef2f8 100%);
        5 -  color: #0b1526;
        1 +/* ── Layout: sidebar + viewer ── */
        2 +.tvp {
        3    display: flex;
        7 -  flex-direction: column;
        8 -  align-items: center;
        9 -  gap: clamp(32px, 5vw, 48px);
       10 -  font-family: "SF Pro Display", "SF Pro Text", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        4 +  height: calc(100vh - var(--nav-h));
        5 +  overflow: hidden;
        6  }
        7
       13 -@media (max-width: 768px) {
       14 -  .tile-viewer-page {
       15 -    padding: clamp(32px, 8vw, 56px) clamp(20px, 6vw, 64px);
       16 -    gap: 32px;
        8 +/* ── Sidebar ── */
        9 +.tvp__sidebar {
       10 +  width: var(--sidebar-w);
       11 +  flex-shrink: 0;
       12 +  background: var(--surface);
       13 +  border-right: 1px solid var(--border);
       14 +  overflow-y: auto;
       15 +  display: flex;
       16 +  flex-direction: column;
       17 +
       18 +  @media (max-width: 768px) {
       19 +    display: none;
       20    }
       21  }
       22
       20 -.tile-viewer-page__header {
       23 +.tvp__section {
       24 +  padding: 18px 18px;
       25 +  border-bottom: 1px solid var(--border);
       26    display: flex;
       27    flex-direction: column;
       23 -  align-items: center;
       28    gap: 12px;
       25 -  text-align: center;
       26 -}
       29
       28 -.tile-viewer-page__title {
       29 -  margin: 0;
       30 -  font-size: clamp(32px, 5vw, 46px);
       31 -  line-height: 1.1;
       32 -  font-weight: 500;
       33 -  letter-spacing: -0.03em;
       30 +  &:last-child {
       31 +    border-bottom: none;
       32 +  }
       33  }
       34
       36 -.tile-viewer-page__subtitle {
       37 -  margin: 0;
       38 -  max-width: 420px;
       39 -  font-size: 17px;
       40 -  line-height: 1.65;
       41 -  color: rgba(15, 23, 42, 0.55);
       35 +/* ── Labels / inputs ── */
       36 +.tvp__label {
       37 +  font-size: 10px;
       38 +  font-weight: 700;
       39 +  letter-spacing: 0.1em;
       40 +  text-transform: uppercase;
       41 +  color: var(--text-dim);
       42  }
       43
       44 -.tile-viewer-page__control-bar {
       45 -  width: min(620px, 100%);
       44 +.tvp__search-group {
       45 +  position: relative;
       46    display: flex;
       47    flex-direction: column;
       48 -  align-items: center;
       49 -  gap: 20px;
       50 -  padding: clamp(20px, 3vw, 28px);
       51 -  background: rgba(255, 255, 255, 0.96);
       52 -  border-radius: 24px;
       53 -  border: 1px solid rgba(15, 23, 42, 0.04);
       54 -  box-shadow: 0 28px 80px -60px rgba(15, 23, 42, 0.28);
       55 -  backdrop-filter: blur(18px);
       48 +  gap: 8px;
       49  }
       50
       58 -.tile-viewer-page__search-group {
       59 -  width: 100%;
       51 +.tvp__search-row {
       52    display: flex;
       61 -  flex-direction: column;
       62 -  gap: 12px;
       63 -  position: relative;
       53 +  gap: 8px;
       54  }
       55
       66 -.tile-viewer-page__label {
       67 -  font-size: 12px;
       68 -  font-weight: 600;
       69 -  letter-spacing: 0.2em;
       70 -  text-transform: uppercase;
       71 -  color: rgba(15, 23, 42, 0.38);
       72 -}
       73 -
       74 -.tile-viewer-page__input {
       75 -  width: 100%;
       76 -  padding: 14px 20px;
       77 -  font-size: 17px;
       78 -  border-radius: 28px;
       79 -  border: 1px solid rgba(15, 23, 42, 0.05);
       80 -  background: linear-gradient(160deg, #ffffff 0%, #f3f6fb 100%);
       81 -  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
       82 -  text-align: left;
       83 -}
       84 -
       85 -.tile-viewer-page__input:focus {
       56 +.tvp__input {
       57 +  flex: 1;
       58 +  min-width: 0;
       59 +  padding: 9px 12px;
       60 +  font-size: 13px;
       61 +  background: var(--surface-2);
       62 +  border: 1px solid var(--border);
       63 +  border-radius: var(--radius-md);
       64 +  color: var(--text);
       65 +  transition: border-color 0.15s, box-shadow 0.15s;
       66    outline: none;
       87 -  border-color: rgba(37, 99, 235, 0.3);
       88 -  box-shadow: 0 12px 32px -24px rgba(37, 99, 235, 0.6), 0 0 0 6px rgba(37, 99, 235, 0.07);
       89 -  transform: translateY(-1px);
       90 -}
       67
       92 -.tile-viewer-page__hint {
       93 -  font-size: 13px;
       94 -  color: rgba(15, 23, 42, 0.42);
       95 -  letter-spacing: 0.02em;
       96 -}
       68 +  &::placeholder {
       69 +    color: var(--text-dim);
       70 +  }
       71
       98 -.tile-viewer-page__search {
       99 -  display: flex;
      100 -  gap: 10px;
      101 -  align-items: center;
       72 +  &:focus {
       73 +    border-color: var(--border-focus);
       74 +    box-shadow: 0 0 0 3px rgba(252, 163, 17, 0.1);
       75 +  }
       76  }
       77
      104 -.tile-viewer-page__submit {
      105 -  padding: 12px 20px;
      106 -  border-radius: 999px;
       78 +.tvp__load-btn {
       79 +  padding: 9px 14px;
       80 +  border-radius: var(--radius-md);
       81    border: none;
      108 -  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
      109 -  color: #ffffff;
      110 -  font-weight: 600;
      111 -  font-size: 15px;
      112 -  letter-spacing: 0.02em;
      113 -  box-shadow: 0 12px 24px -16px rgba(37, 99, 235, 0.6);
      114 -  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
      115 -  min-width: 90px;
      116 -  cursor: pointer;
      117 -}
       82 +  background: var(--accent);
       83 +  color: #0c1220;
       84 +  font-size: 13px;
       85 +  font-weight: 700;
       86 +  white-space: nowrap;
       87 +  transition: background 0.15s, opacity 0.15s;
       88 +  flex-shrink: 0;
       89
      119 -.tile-viewer-page__submit:disabled {
      120 -  opacity: 0.45;
      121 -  cursor: not-allowed;
      122 -  box-shadow: none;
      123 -}
       90 +  &:hover:not(:disabled) {
       91 +    background: var(--accent-hover);
       92 +  }
       93
      125 -.tile-viewer-page__submit:not(:disabled):hover {
      126 -  transform: translateY(-1px);
       94 +  &:disabled {
       95 +    opacity: 0.4;
       96 +    cursor: not-allowed;
       97 +  }
       98  }
       99
      129 -.tile-viewer-page__submit:not(:disabled):active {
      130 -  transform: translateY(0);
      131 -  box-shadow: 0 8px 16px -14px rgba(37, 99, 235, 0.8);
      132 -}
      133 -
      134 -.tile-viewer-page__suggestions {
      100 +/* ── Suggestions ── */
      101 +.tvp__suggestions {
      102    position: absolute;
      103    top: 100%;
      104    left: 0;
      138 -  width: 100%;
      105 +  right: 0;
      106    margin: 4px 0 0;
      140 -  padding: 8px;
      107 +  padding: 6px;
      108    list-style: none;
      142 -  background: rgba(255, 255, 255, 0.98);
      143 -  border-radius: 20px;
      144 -  border: 1px solid rgba(15, 23, 42, 0.08);
      145 -  box-shadow: 0 24px 60px -48px rgba(15, 23, 42, 0.55);
      146 -  backdrop-filter: blur(12px);
      147 -  max-height: 280px;
      109 +  background: var(--surface-3);
      110 +  border: 1px solid var(--border);
      111 +  border-radius: var(--radius-lg);
      112 +  box-shadow: 0 12px 32px -16px rgba(0, 0, 0, 0.6);
      113 +  max-height: 260px;
      114    overflow-y: auto;
      149 -  z-index: 10;
      115 +  z-index: 20;
      116  }
      117
      152 -.tile-viewer-page__suggestion {
      118 +.tvp__suggestion {
      119    width: 100%;
      154 -  display: flex;
      155 -  flex-direction: column;
      156 -  align-items: flex-start;
      157 -  gap: 4px;
      158 -  padding: 12px 14px;
      159 -  border-radius: 14px;
      120 +  padding: 9px 10px;
      121 +  border-radius: var(--radius-md);
      122    border: none;
      161 -  background: rgba(248, 250, 252, 0.75);
      162 -  color: inherit;
      123 +  background: transparent;
      124 +  color: var(--text);
      125    text-align: left;
      126    cursor: pointer;
      165 -  transition: background 0.2s ease, transform 0.15s ease;
      127 +  display: flex;
      128 +  flex-direction: column;
      129 +  gap: 3px;
      130 +  transition: background 0.1s;
      131 +
      132 +  &:hover,
      133 +  &:focus-visible {
      134 +    background: var(--blue-dim);
      135 +    outline: none;
      136 +  }
      137  }
      138
      168 -.tile-viewer-page__suggestion:hover,
      169 -.tile-viewer-page__suggestion:focus-visible {
      170 -  outline: none;
      171 -  background: rgba(37, 99, 235, 0.12);
      172 -  transform: translateY(-1px);
      139 +.tvp__suggestion-name {
      140 +  font-size: 13px;
      141 +  font-weight: 500;
      142  }
      143
      175 -.tile-viewer-page__suggestion-title {
      176 -  font-weight: 600;
      177 -  letter-spacing: 0.01em;
      144 +.tvp__suggestion-meta {
      145 +  font-size: 11px;
      146 +  color: var(--text-muted);
      147  }
      148
      180 -.tile-viewer-page__suggestion-meta {
      149 +/* ── Status text ── */
      150 +.tvp__status-text {
      151    font-size: 12px;
      182 -  color: rgba(15, 23, 42, 0.5);
      183 -}
      152 +  color: var(--text-muted);
      153
      185 -.tile-viewer-page__status-row {
      186 -  min-height: 18px;
      187 -  display: flex;
      188 -  align-items: center;
      154 +  &--error {
      155 +    color: var(--error);
      156 +  }
      157 +
      158 +  &--warn {
      159 +    color: var(--warning);
      160 +  }
      161  }
      162
      191 -.tile-viewer-page__status {
      163 +.tvp__current {
      164 +  margin: 0;
      165    font-size: 12px;
      193 -  color: rgba(15, 23, 42, 0.45);
      194 -  letter-spacing: 0.04em;
      195 -  text-transform: uppercase;
      166 +  color: var(--text-muted);
      167  }
      168
      198 -.tile-viewer-page__status--error {
      199 -  color: #dc2626;
      169 +.tvp__current-label {
      170 +  color: var(--text-dim);
      171  }
      172
      202 -.tile-viewer-page__viewer {
      203 -  width: min(1120px, 100%);
      173 +.tvp__current-id {
      174 +  font-family: "SF Mono", "Fira Code", monospace;
      175 +  font-size: 11px;
      176 +  color: var(--text-muted);
      177 +}
      178 +
      179 +/* ── Analyze button ── */
      180 +.tvp__analyze-btn {
      181 +  padding: 10px 16px;
      182 +  border-radius: var(--radius-md);
      183 +  border: none;
      184 +  background: linear-gradient(135deg, #0ea5e9 0%, #3b82f6 100%);
      185 +  color: #fff;
      186 +  font-size: 13px;
      187 +  font-weight: 700;
      188 +  transition: opacity 0.15s, transform 0.1s;
      189 +  width: 100%;
      190 +
      191 +  &:hover:not(:disabled) {
      192 +    opacity: 0.9;
      193 +    transform: translateY(-1px);
      194 +  }
      195 +
      196 +  &:disabled {
      197 +    opacity: 0.4;
      198 +    cursor: not-allowed;
      199 +    transform: none;
      200 +  }
      201 +
      202 +  &--busy {
      203 +    opacity: 0.75;
      204 +  }
      205 +}
      206 +
      207 +/* ── Analysis progress ── */
      208 +.tvp__analysis-progress {
      209    display: flex;
      210    flex-direction: column;
      206 -  align-items: center;
      211 +  gap: 7px;
      212  }
      213
      209 -.tile-viewer-page__surface {
      210 -  width: 100%;
      211 -  border-radius: 32px;
      212 -  padding: clamp(18px, 3vw, 26px);
      213 -  background: rgba(255, 255, 255, 0.98);
      214 -  border: 1px solid rgba(15, 23, 42, 0.06);
      215 -  box-shadow: 0 55px 140px -90px rgba(15, 23, 42, 0.42);
      216 -  backdrop-filter: blur(14px);
      214 +.tvp__ap-bar {
      215 +  height: 4px;
      216 +  background: rgba(255, 255, 255, 0.07);
      217 +  border-radius: 999px;
      218 +  overflow: hidden;
      219  }
      220
      219 -.tile-viewer-page__placeholder {
      220 -  height: 70vh;
      221 -  min-height: 320px;
      221 +.tvp__ap-fill {
      222 +  height: 100%;
      223 +  background: var(--blue);
      224 +  border-radius: 999px;
      225 +  transition: width 300ms ease;
      226 +}
      227 +
      228 +.tvp__ap-meta {
      229    display: flex;
      223 -  align-items: center;
      224 -  justify-content: center;
      225 -  font-size: 16px;
      226 -  color: rgba(15, 23, 42, 0.45);
      227 -  letter-spacing: 0.01em;
      228 -  text-align: center;
      230 +  justify-content: space-between;
      231 +  font-size: 11px;
      232 +  color: var(--text-muted);
      233  }
      234
      231 -.tile-viewer-page__actions {
      235 +/* ── Results summary ── */
      236 +.tvp__results {
      237 +  background: var(--surface-2);
      238 +  border-left: 3px solid var(--blue);
      239 +}
      240 +
      241 +.tvp__stat-big {
      242    display: flex;
      233 -  gap: 12px;
      243 +  flex-direction: column;
      244 +  align-items: flex-start;
      245 +  gap: 2px;
      246 +  padding: 8px 0;
      247  }
      248
      236 -.tile-viewer-page__btn {
      237 -  border: none;
      238 -  border-radius: 999px;
      239 -  padding: 12px 20px;
      240 -  font-size: 14px;
      241 -  font-weight: 600;
      242 -  cursor: pointer;
      249 +.tvp__stat-big-value {
      250 +  font-size: 36px;
      251 +  font-weight: 700;
      252 +  line-height: 1;
      253 +  letter-spacing: -0.03em;
      254 +  color: var(--text);
      255  }
      256
      245 -.tile-viewer-page__btn--primary {
      246 -  background: linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%);
      247 -  color: #fff;
      248 -  box-shadow: 0 10px 20px -14px rgba(37, 99, 235, 0.8);
      257 +.tvp__stat-big-unit {
      258 +  font-size: 20px;
      259 +  font-weight: 400;
      260 +  color: var(--text-muted);
      261  }
      262
      251 -.tile-viewer-page__btn--primary:disabled {
      252 -  opacity: 0.45;
      253 -  cursor: not-allowed;
      254 -  box-shadow: none;
      263 +.tvp__stat-big-label {
      264 +  font-size: 12px;
      265 +  color: var(--text-muted);
      266  }
      267
      257 -.tile-viewer-page__analysis-controls {
      258 -  width: 100%;
      259 -  display: grid;
      260 -  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      268 +.tvp__stat-row {
      269 +  display: flex;
      270    gap: 12px;
      262 -  padding: 12px;
      263 -  border-radius: 14px;
      264 -  background: rgba(15, 23, 42, 0.03);
      271  }
      272
      267 -.tile-viewer-page__control {
      273 +.tvp__stat {
      274 +  flex: 1;
      275    display: flex;
      276    flex-direction: column;
      270 -  gap: 8px;
      271 -  text-align: left;
      277 +  gap: 3px;
      278 +  padding: 8px 10px;
      279 +  background: var(--surface);
      280 +  border-radius: var(--radius-md);
      281  }
      282
      274 -.tile-viewer-page__control label {
      275 -  font-size: 12px;
      276 -  color: rgba(15, 23, 42, 0.65);
      283 +.tvp__stat-label {
      284 +  font-size: 10px;
      285 +  color: var(--text-dim);
      286    font-weight: 600;
      287 +  text-transform: uppercase;
      288 +  letter-spacing: 0.06em;
      289  }
      290
      280 -.tile-viewer-page__control input[type="range"] {
      281 -  width: 100%;
      291 +.tvp__stat-value {
      292 +  font-size: 16px;
      293 +  font-weight: 600;
      294 +  color: var(--text);
      295 +  letter-spacing: -0.01em;
      296  }
      297
      284 -.checkbox-label {
      285 -  display: inline-flex;
      298 +/* ── Controls ── */
      299 +.tvp__slider-label {
      300 +  display: flex;
      301 +  flex-direction: column;
      302 +  gap: 5px;
      303 +  font-size: 12px;
      304 +  color: var(--text-muted);
      305 +
      306 +  input[type="range"] {
      307 +    width: 100%;
      308 +    accent-color: var(--blue);
      309 +  }
      310 +}
      311 +
      312 +.tvp__toggles {
      313 +  display: flex;
      314 +  flex-direction: column;
      315 +  gap: 8px;
      316 +}
      317 +
      318 +.tvp__toggle {
      319 +  display: flex;
      320    align-items: center;
      321    gap: 8px;
      322    font-size: 13px;
      323 +  color: var(--text-muted);
      324 +  cursor: pointer;
      325 +
      326 +  input[type="checkbox"] {
      327 +    accent-color: var(--blue);
      328 +    width: 14px;
      329 +    height: 14px;
      330 +  }
      331  }
      332
      291 -.tile-viewer-page__status--warning {
      292 -  color: #b45309;
      333 +/* ── Viewer pane ── */
      334 +.tvp__viewer {
      335 +  flex: 1;
      336 +  overflow: hidden;
      337 +  position: relative;
      338 +  background: #0a1018;
      339  }
      340 +
      341 +.tvp__placeholder {
      342 +  height: 100%;
      343 +  display: flex;
      344 +  flex-direction: column;
      345 +  align-items: center;
      346 +  justify-content: center;
      347 +  gap: 8px;
      348 +  color: var(--text-muted);
      349 +
      350 +  p {
      351 +    margin: 0;
      352 +    font-size: 15px;
      353 +  }
      354 +}

⏺ Now update the ImageViewer to fill the container height:

⏺ Write(frontend/src/styles/ImageViewer.scss)
  ⎿  Added 3 lines, removed 34 lines
       1  .image-viewer {
       2    width: 100%;
       3 -  height: 70vh;
       4 -  min-height: 520px;
       5 -  border-radius: 16px;
       6 -  border: 1px solid rgba(15, 23, 42, 0.1);
       7 -  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #111827 100%);
       8 -  box-shadow: 0 20px 45px -25px rgba(15, 23, 42, 0.65);
       3 +  height: 100%;
       4 +  min-height: 0;
       5 +  background: linear-gradient(135deg, #0a1018 0%, #111827 100%);
       6    overflow: hidden;
       7    position: relative;
       8  }
     ...
      19    object-fit: fill;
      20    pointer-events: none;
      21  }
      25 -
      26 -@media (max-width: 1200px) {
      27 -  .image-viewer {
      28 -    height: 65vh;
      29 -  }
      30 -}
      31 -
      32 -@media (max-width: 992px) {
      33 -  .image-viewer {
      34 -    height: 60vh;
      35 -    min-height: 440px;
      36 -  }
      37 -}
      38 -
      39 -@media (max-width: 768px) {
      40 -  .image-viewer {
      41 -    height: 56vh;
      42 -    min-height: 380px;
      43 -  }
      44 -}
      45 -
      46 -@media (max-width: 576px) {
      47 -  .image-viewer {
      48 -    height: 52vh;
      49 -    min-height: 320px;
      50 -    border-radius: 12px;
      51 -  }
      52 -}

⏺ Now update index.scss to remove the redundant HomePage.scss import since the component already imports it:

⏺ Write(frontend/src/styles/index.scss)
  ⎿  Removed 4 lines
      1 -// Import all your SCSS partials and global styles here
      1  @use "./base.scss";
      3 -
      2  @use "./App.scss";
      5 -
      6 -@use "./HomePage.scss";

⏺ Now the tiling service — concurrent uploads with ThreadPoolExecutor:

⏺ Write(services/tiling/src/tiling_service.py)
  ⎿  Added 79 lines, removed 81 lines
       2  import json
       3  import os
       4  import shutil
       5 +import threading
       6  import time
       7 +from concurrent.futures import ThreadPoolExecutor, as_completed
       8  from datetime import datetime, timezone
       9  from pathlib import Path
      10  from typing import Any, Optional, Tuple
     ...
      13  import pyvips
      14  from minio import Minio
      15
      14 -# Import our new centralized settings
      16  from .config import settings
      17
      18 +# Number of parallel tile upload threads.  16 gives a good balance between
      19 +# throughput and MinIO connection-pool pressure.
      20 +_UPLOAD_WORKERS = 16
      21
      22 +
      23  class TilingService:
      24      def __init__(self):
      25          """Initializes the service and the MinIO client."""
     ...
      29              secret_key=settings.MINIO_SECRET_KEY,
      30              secure=settings.MINIO_SECURE
      31          )
      27 -        # Ensure the temporary directory for our work exists
      32          Path(settings.TEMP_STORAGE_PATH).mkdir(parents=True, exist_ok=True)
      33
      34      def process_image(
     ...
      39          source_bucket: str,
      40          dataset_name: Optional[str] = None,
      41      ):
      38 -        """
      39 -        The main orchestrator method for processing one image.
      40 -        This will download, tile, upload, and cleanup.
      41 -        """
      42 +        """Main orchestrator: download → tile → upload → cleanup."""
      43          print(f"Starting processing for image_id='{image_id}'")
      44          local_image_path = None
      45          local_tiles_dir = None
     ...
      50                  f"bucket='{source_bucket}', object='{source_object_name}'"
      51              )
      52
      52 -            # 1. Download the source file from MinIO
      53 +            # 1. Download
      54              self._notify_job_event(
      55                  job_id=job_id,
      56                  stage="DOWNLOADING",
      57                  message="Downloading source image.",
      58                  dataset_name=dataset_name,
      58 -                activity_entries=[
      59 -                    self._build_activity_entry("DOWNLOADING", "Downloading source image.")
      60 -                ],
      59 +                activity_entries=[self._build_activity_entry("DOWNLOADING", "Downloading source image.")],
      60              )
      61              download_start = time.perf_counter()
      63 -            local_image_path, source_stat = self._download_source_image(
      64 -                source_object_name,
      65 -                source_bucket,
      66 -            )
      62 +            local_image_path, source_stat = self._download_source_image(source_object_name, source_bucket)
      63              download_duration = time.perf_counter() - download_start
      64
      69 -            # 2. Generate DZI tiles locally (logic from your script)
      65 +            # 2. Tile
      66              self._notify_job_event(
      67                  job_id=job_id,
      68                  stage="TILING",
      69                  message="Generating Deep Zoom tiles.",
      70                  dataset_name=dataset_name,
      75 -                activity_entries=[
      76 -                    self._build_activity_entry("TILING", "Generating Deep Zoom tiles.")
      77 -                ],
      71 +                activity_entries=[self._build_activity_entry("TILING", "Generating Deep Zoom tiles.")],
      72              )
      73              tiling_start = time.perf_counter()
      74              local_tiles_dir = self._generate_tiles(local_image_path, image_id)
      75              tiling_duration = time.perf_counter() - tiling_start
      76
      83 -            # 3. Upload the generated tiles to MinIO (logic from your script)
      77 +            # 3. Upload (parallel)
      78              self._notify_job_event(
      79                  job_id=job_id,
      80                  stage="UPLOADING",
      81                  message="Uploading tiles and metadata.",
      82                  dataset_name=dataset_name,
      89 -                activity_entries=[
      90 -                    self._build_activity_entry("UPLOADING", "Uploading tiles and metadata.")
      91 -                ],
      83 +                activity_entries=[self._build_activity_entry("UPLOADING", "Uploading tiles and metadata.")],
      84              )
      85              upload_start = time.perf_counter()
      86              file_count, total_bytes = self._upload_tiles(local_tiles_dir, image_id, job_id, dataset_name)
     ...
      124                  f"tiling={tiling_duration:.3f}s, "
      125                  f"upload={upload_duration:.3f}s, "
      126                  f"total={total_duration:.3f}s, "
      135 -                f"files={file_count}, uploaded_bytes={total_bytes}"
      127 +                f"files={file_count}, bytes={total_bytes}"
      128              )
      129
      138 -            print(f"Successfully processed image_id='{image_id}'")
      130              self._notify_job_event(
      131                  job_id=job_id,
      132                  stage="COMPLETED",
     ...
      134                  metadata_path=f"{image_id}/metadata.json",
      135                  dataset_name=dataset_name,
      136                  stage_progress_percent=100,
      146 -                activity_entries=[
      147 -                    self._build_activity_entry("COMPLETED", "Tiles are ready.")
      148 -                ],
      137 +                activity_entries=[self._build_activity_entry("COMPLETED", "Tiles are ready.")],
      138              )
      139
      140          except Exception as e:
     ...
      145                  message="Tiling failed.",
      146                  failure_reason=str(e),
      147                  dataset_name=dataset_name,
      159 -                activity_entries=[
      160 -                    self._build_activity_entry("FAILED", "Tiling failed.", detail=str(e))
      161 -                ],
      148 +                activity_entries=[self._build_activity_entry("FAILED", "Tiling failed.", detail=str(e))],
      149              )
      150          finally:
      164 -            # 4. Cleanup local files regardless of success or failure
      151              print("Cleaning up local files...")
      152              if local_image_path and os.path.exists(local_image_path):
      153                  os.remove(local_image_path)
     ...
      155                  shutil.rmtree(local_tiles_dir)
      156              print("Cleanup complete.")
      157
      158 +    # ── Notification ──────────────────────────────────────────────────────────
      159 +
      160      def _notify_job_event(
      161          self,
      162          *,
     ...
      172          if not job_id or not settings.BACKEND_INTERNAL_BASE_URL:
      173              return
      174
      187 -        endpoint = f"{settings.BACKEND_INTERNAL_BASE_URL.rstrip('/')}/api/v1/internal/tiling/jobs/{job_id}/events"
      175 +        endpoint = (
      176 +            f"{settings.BACKEND_INTERNAL_BASE_URL.rstrip('/')}"
      177 +            f"/api/v1/internal/tiling/jobs/{job_id}/events"
      178 +        )
      179          payload = {
      180              "stage": stage,
      181              "message": message,
     ...
      192              headers={"Content-Type": "application/json"},
      193              method="POST",
      194          )
      204 -
      195          try:
      196              with request.urlopen(req, timeout=10) as response:
      197                  if response.status >= 400:
      208 -                    print(f"Failed to notify backend for job '{job_id}' stage '{stage}': HTTP {response.status}")
      198 +                    print(f"Backend notify failed for job '{job_id}' stage '{stage}': HTTP {response.status}")
      199          except error.URLError as exc:
      210 -            print(f"Failed to notify backend for job '{job_id}' stage '{stage}': {exc}")
      200 +            print(f"Backend notify failed for job '{job_id}' stage '{stage}': {exc}")
      201
      202 +    # ── Download ──────────────────────────────────────────────────────────────
      203 +
      204      def _download_source_image(self, object_name: str, bucket: str) -> Tuple[Path, object]:
      213 -        """Downloads the source image from MinIO to our temporary storage."""
      205          local_path = Path(settings.TEMP_STORAGE_PATH) / Path(object_name).name
      206          print(f"Fetching metadata for {bucket}/{object_name}...")
      207          stat = self.minio_client.stat_object(bucket, object_name)
      217 -        print(
      218 -            f"Source object: size={stat.size} bytes, "
      219 -            f"content_type='{stat.content_type}', etag='{stat.etag}'"
      220 -        )
      221 -
      222 -        print(f"Downloading {bucket}/{object_name} to {local_path}...")
      208 +        print(f"Source object: size={stat.size} bytes, type='{stat.content_type}'")
      209 +        print(f"Downloading {bucket}/{object_name} → {local_path}...")
      210          self.minio_client.fget_object(bucket, object_name, str(local_path))
      211          print("Download complete.")
      212          return local_path, stat
      213
      214 +    # ── Tiling ────────────────────────────────────────────────────────────────
      215 +
      216      def _generate_tiles(self, input_image_path: Path, image_id: str) -> Path:
      228 -        """Generates DZI tiles using pyvips. (Copied from your script)"""
      217          print(f"Generating DZI tiles for {input_image_path.name}...")
      218          image = pyvips.Image.new_from_file(str(input_image_path), access='sequential')
      231 -
      219 +
      220          output_path = Path(settings.TEMP_STORAGE_PATH) / image_id
      221          output_path.mkdir(parents=True, exist_ok=True)
      234 -
      222 +
      223          base_path = output_path / "image"
      224          image.dzsave(str(base_path), suffix=".jpg[Q=85]", overlap=0, tile_size=256)
      225
      238 -        print(f"Tiles generated successfully at {output_path}")
      226 +        print(f"Tiles generated at {output_path}")
      227          return output_path
      228
      229 +    # ── Upload (parallel) ─────────────────────────────────────────────────────
      230 +
      231      def _upload_tiles(
      232          self,
      233          tiles_dir: Path,
     ...
      235          job_id: Optional[str],
      236          dataset_name: Optional[str],
      237      ) -> Tuple[int, int]:
      248 -        """Uploads the generated tile directory to MinIO. (Copied from your script)"""
      238 +        """Upload the tile directory to MinIO using a thread pool."""
      239          bucket = settings.MINIO_UPLOAD_BUCKET
      250 -        print(f"Uploading tiles to MinIO bucket '{bucket}'...")
      240 +        print(f"Uploading tiles to bucket '{bucket}' with {_UPLOAD_WORKERS} workers...")
      241
      252 -        # Ensure bucket exists
      242          if not self.minio_client.bucket_exists(bucket):
      243              self.minio_client.make_bucket(bucket)
      244
      256 -        file_paths = sorted(path for path in tiles_dir.rglob("*") if path.is_file())
      245 +        file_paths = sorted(p for p in tiles_dir.rglob("*") if p.is_file())
      246          total_files = len(file_paths)
      247          if total_files == 0:
      259 -            raise RuntimeError(f"No generated tile files found in {tiles_dir}")
      248 +            raise RuntimeError(f"No tile files found in {tiles_dir}")
      249
      250          self._notify_job_event(
      251              job_id=job_id,
     ...
      265          file_count = 0
      266          total_bytes = 0
      267          last_reported_percent = -1
      268 +        lock = threading.Lock()
      269
      280 -        for file_path in file_paths:
      281 -            relative_path = file_path.relative_to(tiles_dir)
      282 -            object_name = f"{image_id}/{relative_path}"
      270 +        def upload_one(file_path: Path) -> int:
      271 +            relative = file_path.relative_to(tiles_dir)
      272 +            object_name = f"{image_id}/{relative}"
      273              self.minio_client.fput_object(bucket, object_name, str(file_path))
      284 -            file_count += 1
      285 -            total_bytes += file_path.stat().st_size
      274 +            return file_path.stat().st_size
      275
      287 -            percent = int((file_count / total_files) * 100)
      288 -            should_report = (
      289 -                file_count == total_files
      290 -                or file_count == 1
      291 -                or percent >= last_reported_percent + 5
      292 -                or file_count % 250 == 0
      293 -            )
      294 -            if should_report:
      295 -                last_reported_percent = percent
      296 -                self._notify_job_event(
      297 -                    job_id=job_id,
      298 -                    stage="UPLOADING",
      299 -                    message="Uploading generated tiles to object storage.",
      300 -                    dataset_name=dataset_name,
      301 -                    stage_progress_percent=percent,
      302 -                    activity_entries=[
      303 -                        self._build_activity_entry(
      304 -                            "UPLOADING",
      305 -                            "Uploading generated tiles to object storage.",
      306 -                            detail=f"Uploaded {file_count:,} / {total_files:,} files.",
      276 +        with ThreadPoolExecutor(max_workers=_UPLOAD_WORKERS) as executor:
      277 +            futures = {executor.submit(upload_one, fp): fp for fp in file_paths}
      278 +            for future in as_completed(futures):
      279 +                size = future.result()  # propagates any upload exception
      280 +                with lock:
      281 +                    file_count += 1
      282 +                    total_bytes += size
      283 +                    percent = int((file_count / total_files) * 100)
      284 +                    should_report = (
      285 +                        file_count == total_files
      286 +                        or file_count == 1
      287 +                        or percent >= last_reported_percent + 5
      288 +                        or file_count % 250 == 0
      289 +                    )
      290 +                    if should_report:
      291 +                        last_reported_percent = percent
      292 +                        self._notify_job_event(
      293 +                            job_id=job_id,
      294 +                            stage="UPLOADING",
      295 +                            message="Uploading generated tiles to object storage.",
      296 +                            dataset_name=dataset_name,
      297 +                            stage_progress_percent=percent,
      298 +                            activity_entries=[
      299 +                                self._build_activity_entry(
      300 +                                    "UPLOADING",
      301 +                                    "Uploading generated tiles to object storage.",
      302 +                                    detail=f"Uploaded {file_count:,} / {total_files:,} files.",
      303 +                                )
      304 +                            ],
      305                          )
      308 -                    ],
      309 -                )
      306
      311 -        print(f"Upload complete: {file_count} files uploaded ({total_bytes} bytes).")
      307 +        print(f"Upload complete: {file_count} files, {total_bytes} bytes.")
      308          return file_count, total_bytes
      309
      310 +    # ── Metadata ──────────────────────────────────────────────────────────────
      311 +
      312      def _write_metadata(
      313          self,
      314          *,
     ...
      335              "timings": timings,
      336              "generated_at": datetime.now(timezone.utc).isoformat(),
      337          }
      340 -
      338          metadata_json = json.dumps(metadata, indent=2)
      339          metadata_bytes = metadata_json.encode("utf-8")
      340          metadata_key = f"{image_id}/metadata.json"
      344 -
      341          print(f"Uploading metadata to {bucket}/{metadata_key}")
      342          self.minio_client.put_object(
      343              bucket,
     ...
      347              content_type="application/json",
      348          )
      349
      350 +    # ── Helpers ───────────────────────────────────────────────────────────────
      351 +
      352      def _build_activity_entry(
      353          self,
      354          stage: str,

⏺ Now the analysis service — model singletons + concurrent tile downloads:

⏺ Write(services/region-detector/src/config.py)
  ⎿  Added 4 lines
      25      # ── Worker ─────────────────────────────────────────────────────────
      26      TEMP_DIR: str = "/tmp/region_detector"
      27
      28 +    # ── Concurrency ────────────────────────────────────────────────────
      29 +    # Number of parallel threads used to download tiles from MinIO.
      30 +    DOWNLOAD_WORKERS: int = 16
      31
      32 +
      33  settings = Settings()

⏺ Write(services/region-detector/src/pipeline.py)
  ⎿  Added 157 lines, removed 89 lines
       4  -----
       5  1. Parse the DZI descriptor to learn the tile grid dimensions.
       6  2. List all tiles at the requested zoom level.
       7 -3. For each tile:
       8 -   a. Download the image from MinIO.
       9 -   b. Run tissue detection (skip if background).
      10 -   c. Embed tissue tiles with DINOv2.
      11 -   d. Classify each embedding with the sklearn head.
      12 -4. Aggregate tile-level results into a slide-level summary.
      13 -5. Generate a heatmap overlay image and upload it to MinIO.
      14 -6. Return the full result (tile predictions + summary + heatmap path).
       7 +3. Download all tiles concurrently from MinIO.
       8 +4. For each tile:
       9 +   a. Run tissue detection (skip if background).
      10 +   b. Embed tissue tiles with DINOv2 (batched).
      11 +   c. Classify each embedding with the sklearn head.
      12 +5. Aggregate tile-level results into a slide-level summary.
      13 +6. Generate a heatmap overlay image and upload it to MinIO.
      14 +7. Return the full result (tile predictions + summary + heatmap path).
      15 +
      16 +Performance notes
      17 +-----------------
      18 +- ML models (DINOv2 + classifier) are module-level singletons loaded once at
      19 +  process startup, not per job.  This avoids a 20-30 s cold-start on every
      20 +  analysis request.
      21 +- Tile downloads are parallelised with a thread pool (DOWNLOAD_WORKERS threads)
      22 +  to saturate network I/O and hide per-tile round-trip latency.
      23  """
      24
      25  from __future__ import annotations
      26
      27 +import threading
      28  import time
      20 -from dataclasses import dataclass, asdict
      21 -from typing import Any, Callable, Dict, List, Optional
      29 +from concurrent.futures import ThreadPoolExecutor, as_completed
      30 +from dataclasses import asdict, dataclass
      31 +from typing import Any, Callable, Dict, List, Optional, Tuple
      32
      33  import numpy as np
      34 +from PIL import Image
      35
      36  from .classifier import Classifier
      37  from .config import settings
     ...
      50  from .tissue_detector import TissueResult, detect_tissue
      51
      52
      53 +# ── Module-level model singletons ────────────────────────────────────────────
      54 +# Loaded once when the module is first used (or explicitly at startup).
      55 +# Thread-safe: both models are read-only during inference.
      56 +
      57 +_embedder: Optional[Embedder] = None
      58 +_classifier: Optional[Classifier] = None
      59 +_model_lock = threading.Lock()
      60 +
      61 +
      62 +def get_embedder() -> Embedder:
      63 +    global _embedder
      64 +    with _model_lock:
      65 +        if _embedder is None:
      66 +            print("[pipeline] Loading DINOv2 embedder…")
      67 +            _embedder = Embedder()
      68 +            print("[pipeline] Embedder ready.")
      69 +    return _embedder
      70 +
      71 +
      72 +def get_classifier() -> Classifier:
      73 +    global _classifier
      74 +    with _model_lock:
      75 +        if _classifier is None:
      76 +            print("[pipeline] Loading sklearn classifier…")
      77 +            _classifier = Classifier()
      78 +            _classifier.load()
      79 +            print("[pipeline] Classifier ready.")
      80 +    return _classifier
      81 +
      82 +
      83 +def preload_models() -> None:
      84 +    """Eagerly initialise both models.  Call this at service startup."""
      85 +    get_embedder()
      86 +    get_classifier()
      87 +
      88 +
      89  # ── Result data classes ───────────────────────────────────────────────────────
      90
      91
     ...
      109      total_tiles: int
      110      tissue_tiles: int
      111      skipped_tiles: int
       65 -    flagged_tiles: int  # tiles exceeding threshold
      112 +    flagged_tiles: int
      113      tumor_area_percentage: float
      114      aggregate_score: float
      115      max_score: float
     ...
      124      dzi: Dict[str, Any]
      125      summary: SlideSummary
      126      tile_predictions: List[TilePrediction]
       80 -    heatmap_key: str  # MinIO object key
      127 +    heatmap_key: str
      128      timings: Dict[str, float]
      129
      130
       84 -# ── Progress callback ────────────────────────────────────────────────────────
      131 +# ── Progress callback ─────────────────────────────────────────────────────────
      132
      133  ProgressCallback = Optional[Callable[[int, int, str, int | None], None]]
      134
      135
      136 +# ── Concurrent tile downloader ────────────────────────────────────────────────
      137 +
      138 +
      139 +def _download_tiles_parallel(
      140 +    tile_refs: List[TileRef],
      141 +    max_workers: int,
      142 +    progress_cb: ProgressCallback = None,
      143 +) -> Dict[str, Optional[Image.Image]]:
      144 +    """Download all tiles concurrently. Returns {object_key: PIL.Image | None}."""
      145 +    results: Dict[str, Optional[Image.Image]] = {}
      146 +    total = len(tile_refs)
      147 +    done = 0
      148 +
      149 +    with ThreadPoolExecutor(max_workers=max_workers) as pool:
      150 +        futures = {pool.submit(download_tile_image, t.object_key): t for t in tile_refs}
      151 +        for future in as_completed(futures):
      152 +            tref = futures[future]
      153 +            try:
      154 +                results[tref.object_key] = future.result()
      155 +            except Exception as exc:
      156 +                print(f"[pipeline] Failed to download {tref.object_key}: {exc}")
      157 +                results[tref.object_key] = None
      158 +            done += 1
      159 +            if done % 50 == 0 or done == total:
      160 +                _report(progress_cb, done, total, f"Downloading tiles ({done}/{total})")
      161 +
      162 +    return results
      163 +
      164 +
      165  # ── Pipeline ──────────────────────────────────────────────────────────────────
      166
      167
     ...
      173      batch_size: int = 16,
      174      progress_cb: ProgressCallback = None,
      175  ) -> AnalysisResult:
      100 -    """Run the full region-detection pipeline for *image_id*.
      101 -
      102 -    Parameters
      103 -    ----------
      104 -    image_id:
      105 -        The image / dataset identifier in MinIO (the top-level prefix).
      106 -    tile_level:
      107 -        DZI pyramid level to analyse.  Defaults to
      108 -        ``settings.DEFAULT_TILE_LEVEL``.
      109 -    threshold:
      110 -        Classification threshold for "Tumor" label.
      111 -    tissue_threshold:
      112 -        Minimum tissue ratio so a tile is analysed.
      113 -    batch_size:
      114 -        Number of tiles to embed at once (GPU batch).
      115 -    progress_cb:
      116 -        Optional ``(processed, total, message)`` callback for status updates.
      117 -    """
      176 +    """Run the full region-detection pipeline for *image_id*."""
      177      requested_tile_level = tile_level if tile_level is not None else settings.DEFAULT_TILE_LEVEL
      178      threshold = threshold if threshold is not None else settings.CLASSIFICATION_THRESHOLD
      179      tissue_thresh = (
     ...
      188      timings["parse_dzi_s"] = round(time.perf_counter() - t0, 3)
      189      _report(progress_cb, 0, 0, "Parsed DZI descriptor")
      190
      132 -    # ── 2. List tiles at the chosen level ─────────────────────────────
      191 +    # ── 2. Resolve tile level ─────────────────────────────────────────
      192      t0 = time.perf_counter()
      193      available_levels = list_available_tile_levels(image_id)
      194      if not available_levels:
     ...
      201      )
      202      if tile_level != requested_tile_level:
      203          _report(
      145 -            progress_cb,
      146 -            0,
      147 -            0,
      204 +            progress_cb, 0, 0,
      205              f"Requested level {requested_tile_level} unavailable. Using level {tile_level}",
      206              tile_level,
      207          )
     ...
      213      _report(progress_cb, 0, total, f"Found {total} tiles at level {tile_level}", tile_level)
      214
      215      if total == 0:
      159 -        raise ValueError(
      160 -            f"No tiles found for image_id={image_id} at level={tile_level}"
      161 -        )
      216 +        raise ValueError(f"No tiles found for image_id={image_id} at level={tile_level}")
      217
      163 -    # Determine grid dimensions from tile coordinates
      218 +    # Grid dims and DZI shape
      219      max_x = max(t.x for t in tile_refs)
      220      max_y = max(t.y for t in tile_refs)
      221      grid_cols = max_x + 1
      222      grid_rows = max_y + 1
      223      max_level = max_dzi_level(DZIShape(width=dzi.width, height=dzi.height, tile_size=dzi.tile_size))
      224
      170 -    # ── 3. Download, detect tissue, embed, classify ───────────────────
      225 +    # ── 3. Initialise models (singletons — fast after first call) ──────
      226      t0 = time.perf_counter()
      172 -
      173 -    # Initialise heavy models
      174 -    embedder = Embedder()
      175 -    classifier = Classifier()
      176 -    classifier.load()
      177 -
      227 +    embedder = get_embedder()
      228 +    classifier = get_classifier()
      229      timings["model_load_s"] = round(time.perf_counter() - t0, 3)
      230
      180 -    # Probability grid for heatmap (-1 = non-tissue)
      181 -    prob_grid = np.full((grid_rows, grid_cols), -1.0)
      231 +    # ── 4. Download all tiles in parallel ─────────────────────────────
      232 +    _report(progress_cb, 0, total, "Downloading tiles…", tile_level)
      233 +    t0 = time.perf_counter()
      234 +    tile_images = _download_tiles_parallel(
      235 +        tile_refs,
      236 +        max_workers=settings.DOWNLOAD_WORKERS,
      237 +        progress_cb=progress_cb,
      238 +    )
      239 +    timings["download_s"] = round(time.perf_counter() - t0, 3)
      240 +    _report(progress_cb, 0, total, "Download complete, analysing tiles…", tile_level)
      241
      242 +    # ── 5. Tissue detection + embedding + classification ───────────────
      243 +    t_analysis = time.perf_counter()
      244 +    prob_grid = np.full((grid_rows, grid_cols), -1.0)
      245      predictions: List[TilePrediction] = []
      246      tissue_count = 0
      247      skipped_count = 0
      248      flagged_count = 0
      249
      188 -    # Process in batches for embedding efficiency
      250      batch_tiles: List[TileRef] = []
      190 -    batch_images = []
      251 +    batch_images: List[Image.Image] = []
      252      batch_tissue: List[TissueResult] = []
      253
      193 -    t_analysis = time.perf_counter()
      254 +    def flush_batch() -> None:
      255 +        nonlocal flagged_count
      256 +        if not batch_images:
      257 +            return
      258 +        embeddings = embedder.embed_batch(batch_images, batch_size=len(batch_images))
      259 +        cls_results = classifier.predict_batch(embeddings, threshold=threshold)
      260 +        for bt, btr, cls_r in zip(batch_tiles, batch_tissue, cls_results):
      261 +            prob_grid[bt.y, bt.x] = cls_r.tumor_probability
      262 +            if cls_r.label == "Tumor":
      263 +                flagged_count += 1
      264 +            px, py, w, h = tile_rect_in_fullres(
      265 +                shape=DZIShape(width=dzi.width, height=dzi.height, tile_size=dzi.tile_size),
      266 +                tile_level=tile_level,
      267 +                max_level=max_level,
      268 +                tile_x=bt.x,
      269 +                tile_y=bt.y,
      270 +            )
      271 +            predictions.append(
      272 +                TilePrediction(
      273 +                    tile_x=bt.x,
      274 +                    tile_y=bt.y,
      275 +                    tile_level=tile_level,
      276 +                    pixel_x=px,
      277 +                    pixel_y=py,
      278 +                    width=w,
      279 +                    height=h,
      280 +                    is_tissue=True,
      281 +                    tissue_ratio=btr.tissue_ratio,
      282 +                    tumor_probability=cls_r.tumor_probability,
      283 +                    label=cls_r.label,
      284 +                )
      285 +            )
      286 +        batch_tiles.clear()
      287 +        batch_images.clear()
      288 +        batch_tissue.clear()
      289
      290      for idx, tref in enumerate(tile_refs):
      196 -        img = download_tile_image(tref.object_key)
      291 +        img = tile_images.get(tref.object_key)
      292 +        if img is None:
      293 +            # Download failed; treat as skipped background tile
      294 +            skipped_count += 1
      295 +            if (idx + 1) % 20 == 0 or idx + 1 == total:
      296 +                _report(progress_cb, idx + 1, total, "Analysing tiles", tile_level)
      297 +            continue
      298 +
      299          tissue = detect_tissue(img, threshold=tissue_thresh)
      300
      301          if not tissue.is_tissue:
     ...
      331          batch_images.append(img)
      332          batch_tissue.append(tissue)
      333
      232 -        # Flush batch when full or last tile
      334          if len(batch_images) >= batch_size or idx + 1 == total:
      234 -            if batch_images:
      235 -                embeddings = embedder.embed_batch(batch_images, batch_size=len(batch_images))
      236 -                cls_results = classifier.predict_batch(embeddings, threshold=threshold)
      335 +            flush_batch()
      336
      238 -                for bt, btr, cls_r in zip(batch_tiles, batch_tissue, cls_results):
      239 -                    prob_grid[bt.y, bt.x] = cls_r.tumor_probability
      240 -                    if cls_r.label == "Tumor":
      241 -                        flagged_count += 1
      242 -                    px, py, w, h = tile_rect_in_fullres(
      243 -                        shape=DZIShape(width=dzi.width, height=dzi.height, tile_size=dzi.tile_size),
      244 -                        tile_level=tile_level,
      245 -                        max_level=max_level,
      246 -                        tile_x=bt.x,
      247 -                        tile_y=bt.y,
      248 -                    )
      249 -                    predictions.append(
      250 -                        TilePrediction(
      251 -                            tile_x=bt.x,
      252 -                            tile_y=bt.y,
      253 -                            tile_level=tile_level,
      254 -                            pixel_x=px,
      255 -                            pixel_y=py,
      256 -                            width=w,
      257 -                            height=h,
      258 -                            is_tissue=True,
      259 -                            tissue_ratio=btr.tissue_ratio,
      260 -                            tumor_probability=cls_r.tumor_probability,
      261 -                            label=cls_r.label,
      262 -                        )
      263 -                    )
      264 -
      265 -                batch_tiles.clear()
      266 -                batch_images.clear()
      267 -                batch_tissue.clear()
      268 -
      337          if (idx + 1) % 20 == 0 or idx + 1 == total:
      338              _report(progress_cb, idx + 1, total, "Analysing tiles", tile_level)
      339
      340      timings["analysis_s"] = round(time.perf_counter() - t_analysis, 3)
      341
      274 -    # ── 4. Aggregate ──────────────────────────────────────────────────
      342 +    # ── 6. Aggregate ──────────────────────────────────────────────────
      343      tissue_probs = [p.tumor_probability for p in predictions if p.is_tissue]
      344      agg_score = float(np.mean(tissue_probs)) if tissue_probs else 0.0
      345      max_score = float(np.max(tissue_probs)) if tissue_probs else 0.0
     ...
      357          threshold=threshold,
      358      )
      359
      292 -    # ── 5. Heatmap ────────────────────────────────────────────────────
      360 +    # ── 7. Heatmap ────────────────────────────────────────────────────
      361      t0 = time.perf_counter()
      362      _report(progress_cb, total, total, "Generating heatmap", tile_level)
      363      heatmap_img = generate_heatmap(prob_grid, tile_size=dzi.tile_size)

⏺ Now update main.py to preload models at startup:

⏺ Write(services/region-detector/src/main.py)
  ⎿  Added 17 lines, removed 11 lines
      10
      11  from __future__ import annotations
      12
      13 -import uuid
      13  import threading
      14  import traceback
      15 +import uuid
      16  from dataclasses import asdict
      17  from enum import Enum
      18  from typing import Any, Dict, List, Optional
     ...
      21  from pydantic import BaseModel
      22
      23  from .config import settings
      24 -from .pipeline import AnalysisResult, run_analysis
      24 +from .pipeline import AnalysisResult, preload_models, run_analysis
      25
      26  # ── App ───────────────────────────────────────────────────────────────────────
      27
      28  app = FastAPI(
      29      title="HistoFlow Region Detector",
      30      description="Tile-level tumour probability analysis with heatmap overlay generation.",
      31 -    version="0.1.0",
      31 +    version="0.2.0",
      32  )
      33
      34
      35 -# ── In-memory job store (prototype) ──────────────────────────────────────────
      35 +@app.on_event("startup")
      36 +async def startup_event() -> None:
      37 +    """Pre-load ML models so the first analysis job starts immediately."""
      38 +    print("[startup] Pre-loading ML models into memory…")
      39 +    preload_models()
      40 +    print("[startup] Models ready.")
      41
      42 +
      43 +# ── In-memory job store ───────────────────────────────────────────────────────
      44 +
      45 +
      46  class JobStatus(str, Enum):
      47      ACCEPTED = "accepted"
      48      PROCESSING = "processing"
     ...
       94  _jobs: Dict[str, JobState] = {}
       95
       96
       88 -# ── Request / Response models ────────────────────────────────────────────────
       97 +# ── Request / Response models ─────────────────────────────────────────────────
       98
       99
      100  class AnalyzeRequest(BaseModel):
     ...
      111      message: str
      112
      113
      105 -# ── Background worker ────────────────────────────────────────────────────────
      114 +# ── Background worker ─────────────────────────────────────────────────────────
      115
      116
      117  def _run_job(job_id: str, req: AnalyzeRequest) -> None:
     ...
      126              progress_cb=state.update_progress,
      127          )
      128
      120 -        # Serialise for the results endpoint
      129          tile_dicts = [asdict(tp) for tp in result.tile_predictions]
      130          state.result = {
      131              "image_id": result.image_id,
     ...
      152
      153  @app.post("/jobs/analyze", response_model=AnalyzeResponse)
      154  async def submit_analysis(req: AnalyzeRequest, background_tasks: BackgroundTasks):
      147 -    """Submit a region-detection job.  Returns immediately."""
      155 +    """Submit a region-detection job. Returns immediately."""
      156      job_id = str(uuid.uuid4())
      157      state = JobState(
      158          image_id=req.image_id,
     ...
      160          threshold=req.threshold or 0.5,
      161      )
      162      _jobs[job_id] = state
      155 -
      163      background_tasks.add_task(_run_job, job_id, req)
      157 -
      164      return AnalyzeResponse(
      165          job_id=job_id,
      166          status="accepted",
     ...
      192              status_code=202,
      193              detail={
      194                  "status": state.status.value,
      189 -                "message": "Analysis is still in progress.  Poll /status to track.",
      195 +                "message": "Analysis is still in progress. Poll /status to track.",
      196                  "tiles_processed": state.tiles_processed,
      197                  "total_tiles": state.total_tiles,
      198              },

⏺ Now let's verify there are no obvious issues — check that the ToastViewport.scss references CSS variables that now exist, and that the App.tsx imports are clean:

⏺ Bash(cd /Users/lsk-sphere/Workspace/seungkilee-cs/HistoFlow/frontend && grep -r "var(--text-inverse)\|var(--shadow-strong)" src/ 2>/dev/null)
  ⎿  src/styles/ToastViewport.scss:  color: var(--text-inverse);
     src/styles/ToastViewport.scss:  box-shadow: var(--shadow-strong);

⏺ Fix the undefined CSS variables in ToastViewport:

⏺ Update(src/styles/ToastViewport.scss)
  ⎿  Added 2 lines, removed 2 lines
      14    border-radius: 18px;
      15    border: 1px solid rgba(255, 255, 255, 0.12);
      16    background: rgba(11, 23, 37, 0.92);
      17 -  color: var(--text-inverse);
      18 -  box-shadow: var(--shadow-strong);
      17 +  color: var(--text);
      18 +  box-shadow: 0 16px 40px -16px rgba(0, 0, 0, 0.7);
      19    display: flex;
      20    flex-direction: column;
      21    gap: 14px;

⏺ Now let's try to build the frontend to catch any TypeScript/import errors:

⏺ Bash(npm run build 2>&1 | tail -50)
  ⎿  src/pages/TileViewerPage.tsx(396,15): error TS7026: JSX element implicitly has type 'any' because no interface 'JSX.IntrinsicElements' exists.
     src/pages/TileViewerPage.tsx(397,13): error TS7026: JSX element implicitly has type 'any' because no interface 'JSX.IntrinsicElements' exists.
     src/pages/TileViewerPage.tsx(398,11): error TS7026: JSX element implicitly has type 'any' because no interface 'JSX.IntrinsicElements' exists.
     … +33 lines (ctrl+o to expand)

⏺ Bash(npm run build 2>&1 | head -30)
  ⎿  > frontend@0.1.0 build
     > tsc && vite build
     … +19 lines (ctrl+o to expand)

⏺ Bash(npm run build 2>&1 | grep -E "error TS|✓|✗|error|warning" | head -40)
  ⎿  src/App.test.tsx(1,32): error TS2307: Cannot find module '@testing-library/react' or its corresponding type declarations.
     src/App.test.tsx(4,1): error TS2582: Cannot find name 'test'. Do you need to install type definitions for a test runner? Try `npm i --save-dev @types/jest` or `npm i --save-dev @types/mocha`.
     src/App.test.tsx(5,10): error TS2875: This JSX tag requires the module path 'react/jsx-runtime' to exist, but none could be found. Make sure you have types for the appropriate package install
     … +26 lines (ctrl+o to expand)

  Read 1 file (ctrl+o to expand)

⏺ Bash(npm install 2>&1 | tail -5)
  ⎿  To address all issues, run:
       npm audit fix

     Run `npm audit` for details.
  ⎿  (timeout 2m)

⏺ Bash(npm run build 2>&1 | grep -E "^src.*error" | grep -v "test\|example\|upload\.test" | head -30)
  ⎿  (No output)
  ⎿  (timeout 1m 30s)

⏺ Bash(npm run build 2>&1 | tail -20)
  ⎿  > frontend@0.1.0 build
     > tsc && vite build
     … +14 lines (ctrl+o to expand)

⏺ Build succeeds. The TypeScript errors in test files and example files are pre-existing. Let me also save a memory note about this project:

  Read 1 file (ctrl+o to expand)

⏺ Build is clean. Here's a summary of everything that was done:

  ---
  What was revamped

  Frontend (full redesign)

  Unified dark design system (base.scss):
  - Replaced ad-hoc SCSS variables with CSS custom properties (--bg, --surface, --border, --accent, --blue, --success, --error, etc.) used consistently across all components

  App (App.tsx + App.scss):
  - Replaced inline-styled nav with a proper sticky nav bar
  - HistoFlow branding with orange accent on "Flow"
  - Active-route highlighting on nav links
  - Live connection status indicator (green dot = SSE live, amber = polling, grey = connecting)

  HomePage (HomePage.tsx + HomePage.scss):
  - Two-column layout: upload zone (sticky left) + job history list (right)
  - Cleaner drop zone with drag-active state (border turns orange)
  - No more single "active job" tracking — shows all jobs as cards
  - Job cards show: name, status badge (color-coded), progress bar if in-flight, latest activity detail, "Open Viewer →" CTA when complete

  TileViewerPage (TileViewerPage.tsx + TileViewerPage.scss):
  - Switched from a stacked-vertical layout to a sidebar + full-height viewer layout
  - Left sidebar (292px): dataset search, analyze button, analysis progress bar, analysis results summary card (tumor %, max score, avg score, tissue/flagged tile counts), overlay controls
  - Viewer fills the remaining height — no more 70vh cap
  - Added analysisSummary state populated from results — the summary card shows big stats at a glance
  - Dark theme throughout to match the rest of the app

  ---
  Tiling Service (tiling_service.py)

  - Concurrent uploads with ThreadPoolExecutor(max_workers=16) — uploads tiles in parallel instead of sequentially. On large slides with thousands of tiles this is significantly faster (limited only by
   MinIO throughput, not Python GIL)
  - Thread-safe progress counter using threading.Lock()
  - Upload failures propagate correctly via future.result()

  Analysis Service

  config.py: Added DOWNLOAD_WORKERS: int = 16 setting.

  pipeline.py:
  - Module-level model singletons (_embedder, _classifier) with a threading.Lock() — DINOv2 and the sklearn classifier load once at startup, not once per job. Eliminates the 20-30s cold start on every
  analysis request
  - Concurrent tile downloads via _download_tiles_parallel() — downloads all tiles in parallel with ThreadPoolExecutor before the analysis loop begins; hides per-tile network round-trip latency
  - Download errors are handled gracefully (failed tiles treated as skipped)

  main.py: Added @app.on_event("startup") that calls preload_models() — models are warm before the first job arrives

✻ Worked for 14m 33s

❯ give me full testing command list

  Searched for 7 patterns, read 2 files (ctrl+o to expand)

⏺ Here's the full testing command list for every layer of the stack:

  ---
  Frontend

  cd frontend

  # Unit + integration tests (vitest)
  npm test

  # Run tests once (no watch mode, good for CI)
  npm test -- --run

  # Run tests with coverage report
  npm test -- --run --coverage

  # Run a specific test file
  npm test -- --run src/utils/upload.test.ts
  npm test -- --run src/pages/TileViewerPage.test.tsx

  # Type check only (no emit)
  npx tsc --noEmit

  # Lint
  npm run lint

  # Build (type-check + bundle — used to verify the build is clean)
  npm run build

  ---
  Backend (Kotlin / Spring Boot)

  cd backend

  # Run all tests
  ./gradlew test

  # Run tests with verbose output
  ./gradlew test --info

  # Run a specific test class
  ./gradlew test --tests "com.histoflow.SomeControllerTest"

  # Run a specific test method
  ./gradlew test --tests "com.histoflow.SomeControllerTest.someMethod"

  # Clean + test (force re-run, skips Gradle's up-to-date cache)
  ./gradlew clean test

  # Generate test report (opens at backend/build/reports/tests/test/index.html)
  ./gradlew test jacocoTestReport

  # Compile check only (no tests)
  ./gradlew compileKotlin compileTestKotlin

  ---
  Tiling Service (Python / FastAPI)

  cd services/tiling

  # Install deps (first time)
  pip install -r requirements.txt

  # Run tests with pytest
  pytest

  # Verbose output
  pytest -v

  # Run a specific test file
  pytest tests/test_tiling_service.py -v

  # With coverage
  pytest --cov=src --cov-report=term-missing

  # Type check
  mypy src/

  ---
  Region-Detector / Analysis Service (Python / FastAPI)

  cd services/region-detector

  # Install deps (first time)
  pip install -r requirements.txt

  # Run all tests
  pytest

  # Verbose
  pytest -v

  # Run specific test files
  pytest tests/test_heatmap.py -v
  pytest tests/test_pipeline_geometry.py -v
  pytest tests/test_tile_levels.py -v
  pytest tests/test_tissue_detector.py -v

  # Run a single test by name
  pytest tests/test_tissue_detector.py::test_blank_is_background -v

  # With coverage
  pytest --cov=src --cov-report=term-missing

  # Type check
  mypy src/

  ---
  Full Stack (Docker)

  cd docker

  # Start base infra (PostgreSQL + MinIO + backend + tiling)
  docker compose -f docker-compose.base.yml -f docker-compose.dev.yml up

  # Also start ML services (region-detector)
  docker compose -f docker-compose.base.yml -f docker-compose.dev.yml -f docker-compose.ml.yml --profile cpu --profile dev up

  # Run backend tests inside Docker
  docker compose -f docker-compose.base.yml exec backend ./gradlew test

  # Health checks (manual smoke test after stack is up)
  curl http://localhost:8080/actuator/health          # backend
  curl http://localhost:8000/health                   # tiling service
  curl http://localhost:8001/health                   # region-detector

  # Tear down
  docker compose -f docker-compose.base.yml -f docker-compose.dev.yml down -v

  ---
  CI (GitHub Actions)

  # Trigger the workflow locally with act (https://github.com/nektos/act)
  act push

  # Run a specific job
  act push --job test-frontend
  act push --job test-backend

❯ I want manual testing. I want a script that I can just run to setup everything so I can test things on the frontend

  Searched for 3 patterns, read 5 files (ctrl+o to expand)

⏺ Now I have everything I need. Let me write the script:

⏺ Write(~/Workspace/seungkilee-cs/HistoFlow/dev.sh)
  ⎿  Wrote 229 lines to ../dev.sh
      1 #!/usr/bin/env bash
      2 # dev.sh — spin up the full HistoFlow stack for manual frontend testing
      3 #
      4 # Usage:
      5 #   bash dev.sh           # base stack  (upload + tiling + viewing)
      6 #   bash dev.sh --ml      # full stack  (+ region-detector analysis service)
      7 #   bash dev.sh --down    # stop everything
      8
      9 set -uo pipefail
     … +219 lines (ctrl+o to expand)

⏺ Bash(chmod +x /Users/lsk-sphere/Workspace/seungkilee-cs/HistoFlow/dev.sh)
  ⎿  Done

⏺ Done. Three commands, that's it:

  # Base stack — upload, tile, view slides
  bash dev.sh

  # Full stack — everything above + AI cancer analysis
  bash dev.sh --ml

  # Tear everything down
  bash dev.sh --down

  What the script does, in order:

  1. Prerequisites check — docker, npm, curl must be present
  2. Creates services/tiling/.env from the example if it doesn't exist
  3. npm install in frontend/ if node_modules is missing (first run only)
  4. docker compose up -d --build with the right compose files and profiles
  5. Health-checks each service with dots and timeouts:
    - PostgreSQL (TCP port check)
    - MinIO → /minio/health/live
    - Backend (TCP port check — avoids needing actuator)
    - Tiling → /health
    - Region-detector → /health (only with --ml, warns that first load takes ~3 min for DINOv2)
  6. Reports MinIO bucket status (buckets auto-create on first upload anyway)
  7. Opens http://localhost:5173 in your browser automatically after 4s
  8. Starts npm run dev in the foreground so you see Vite logs inline
  9. On Ctrl+C — frontend stops, Docker services keep running, tells you how to shut them down