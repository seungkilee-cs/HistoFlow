"""Unit tests for the heatmap generator."""

import numpy as np
from PIL import Image

from src.heatmap import generate_heatmap, heatmap_to_png_bytes


class TestHeatmap:
    """Tests for heatmap generation."""

    def test_basic_heatmap_shape(self):
        """Output image dimensions match grid size."""
        grid = np.array([
            [0.0, 0.5, 1.0],
            [0.2, -1,  0.8],
        ])
        img = generate_heatmap(grid, upscale=False)
        assert img.size == (3, 2)  # (width, height)
        assert img.mode == "RGBA"

    def test_upscale(self):
        """Upscaled heatmap multiplies dimensions by tile_size."""
        grid = np.array([[0.5, 0.9], [0.1, -1]])
        img = generate_heatmap(grid, tile_size=256, upscale=True)
        assert img.size == (512, 512)

    def test_non_tissue_is_transparent_when_skipped_alpha_zero(self):
        """Cells with value -1 are fully transparent when skipped_alpha=0."""
        grid = np.array([[-1.0]])
        img = generate_heatmap(grid, upscale=False, skipped_alpha=0)
        px = img.getpixel((0, 0))
        assert px[3] == 0  # alpha channel

    def test_non_tissue_shows_grey_tint_by_default(self):
        """Skipped cells render as a subtle grey tint with the default skipped_alpha."""
        grid = np.array([[-1.0]])
        img = generate_heatmap(grid, upscale=False)
        r, g, b, a = img.getpixel((0, 0))
        assert a == 25            # default skipped_alpha
        assert r == g == b == 128  # neutral grey

    def test_skipped_alpha_custom(self):
        """skipped_alpha controls opacity of non-tissue cells."""
        grid = np.array([[-1.0]])
        img = generate_heatmap(grid, upscale=False, skipped_alpha=80)
        assert img.getpixel((0, 0))[3] == 80

    def test_tissue_is_opaque(self):
        """Cells with a probability value should have non-zero alpha."""
        grid = np.array([[0.5]])
        img = generate_heatmap(grid, upscale=False, alpha=160)
        px = img.getpixel((0, 0))
        assert px[3] == 160

    def test_png_bytes_roundtrip(self):
        """Encoded PNG bytes can be loaded back as an image."""
        grid = np.array([[0.1, 0.9], [0.5, -1]])
        img = generate_heatmap(grid, upscale=False)
        data = heatmap_to_png_bytes(img)
        assert isinstance(data, bytes)
        assert len(data) > 0
        # Should start with PNG magic bytes
        assert data[:4] == b"\x89PNG"

    def test_high_prob_is_reddish(self):
        """With RdYlGn_r colormap, probability 1.0 should be reddish."""
        grid = np.array([[1.0]])
        img = generate_heatmap(grid, upscale=False, colormap="RdYlGn_r")
        r, g, b, a = img.getpixel((0, 0))
        # Red channel should dominate for high probability
        assert r > g

    def test_low_prob_is_greenish(self):
        """With RdYlGn_r colormap, probability 0.0 should be greenish."""
        grid = np.array([[0.0]])
        img = generate_heatmap(grid, upscale=False, colormap="RdYlGn_r")
        r, g, b, a = img.getpixel((0, 0))
        assert g > r
