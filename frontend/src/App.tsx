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
          <nav style={{
            padding: '20px',
            backgroundColor: '#2c3e50',
            color: 'white',
            display: 'flex',
            gap: '20px'
          }}>
            <Link to="/" style={{ color: 'white', textDecoration: 'none', fontSize: '18px' }}>
              Home
            </Link>
            <Link to="/tile-viewer" style={{ color: 'white', textDecoration: 'none', fontSize: '18px' }}>
              Tile Viewer
            </Link>
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
