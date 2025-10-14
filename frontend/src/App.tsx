import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import HomePage from './components/HomePage';
import TestViewerPage from './pages/TestViewerPage';

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
          <Link to="/test-viewer" style={{ color: 'white', textDecoration: 'none', fontSize: '18px' }}>
            Test Viewer
          </Link>
        </nav>

        {/* Routes */}
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/test-viewer" element={<TestViewerPage />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
