import { BrowserRouter as Router, Routes, Route, NavLink, Link } from 'react-router-dom';
import HomePage from './components/HomePage';
import TileViewerPage from './pages/TileViewerPage';
import ToastViewport from './components/ToastViewport';
import { JobsProvider, useJobs } from './jobs/JobsContext';

function NavBar() {
  const { connectionState } = useJobs();
  const statusLabel =
    connectionState === 'live' ? 'Live'
    : connectionState === 'polling' ? 'Polling'
    : 'Connecting';

  return (
    <nav className="app-nav">
      <Link to="/" className="app-nav__brand">
        Histo<span className="app-nav__brand-accent">Flow</span>
      </Link>
      <div className="app-nav__links">
        <NavLink
          to="/"
          end
          className={({ isActive }) => `app-nav__link${isActive ? ' active' : ''}`}
        >
          Upload
        </NavLink>
        <NavLink
          to="/tile-viewer"
          className={({ isActive }) => `app-nav__link${isActive ? ' active' : ''}`}
        >
          Viewer
        </NavLink>
      </div>
      <div className="app-nav__status">
        <span className={`app-nav__dot app-nav__dot--${connectionState}`} aria-hidden />
        <span>{statusLabel}</span>
      </div>
    </nav>
  );
}

function AppShell() {
  return (
    <div className="app">
      <NavBar />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/tile-viewer" element={<TileViewerPage />} />
          <Route path="/tile-viewer/:imageId" element={<TileViewerPage />} />
        </Routes>
      </main>
      <ToastViewport />
    </div>
  );
}

function App() {
  return (
    <Router>
      <JobsProvider>
        <AppShell />
      </JobsProvider>
    </Router>
  );
}

export default App;
