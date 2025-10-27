import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import HomePage from './components/HomePage';
import TileViewerPage from './pages/TileViewerPage';

function App() {
  return (
    <Router>
      <div className="App">
        {/* Navigation Bar */}
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

        {/* Routes */}
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/tile-viewer" element={<TileViewerPage />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
