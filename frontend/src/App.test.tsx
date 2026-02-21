import { render, screen } from '@testing-library/react';
import App from './App';

test('renders tile viewer link', () => {
  render(<App />);
  const linkElement = screen.getByText(/Tile Viewer/i);
  expect(linkElement).toBeTruthy();
});
