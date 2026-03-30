import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import HomePage from './components/HomePage';
import TileViewerPage from './pages/TileViewerPage';
import ToastViewport from './components/ToastViewport';
import { JobsProvider } from './jobs/JobsContext';

function App() {
  return (
    <Router>
      <JobsProvider>
        <div className="App">
          <nav className="app-nav">
            <Link to="/" className="app-nav__brand">Histo<span>Flow</span></Link>
            <Link to="/" className="app-nav__link">Home</Link>
            <Link to="/tile-viewer" className="app-nav__link">Tile Viewer</Link>
          </nav>

          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/tile-viewer" element={<TileViewerPage />} />
            <Route path="/tile-viewer/:imageId" element={<TileViewerPage />} />
          </Routes>
          <ToastViewport />
        </div>
      </JobsProvider>
    </Router>
  );
}

export default App;
