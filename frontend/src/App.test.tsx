import { render, screen } from '@testing-library/react';
import App from './App';

test('renders test viewer link', () => {
  render(<App />);
  const linkElement = screen.getByText(/Test Viewer/i);
  // Note: toBeInTheDocument requires @testing-library/jest-dom setup
  // For Sprint 1, we'll verify this works in browser instead
  expect(linkElement).toBeTruthy();
});
