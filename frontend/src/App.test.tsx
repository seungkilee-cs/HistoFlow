import { render, screen } from '@testing-library/react';
import App from './App';

test('renders the viewer nav link', () => {
  render(<App />);
  const linkElement = screen.getByRole('link', { name: /^Viewer$/i });
  expect(linkElement).toBeTruthy();
});
