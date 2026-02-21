"""Unit tests for the tissue detector module."""

import numpy as np
from PIL import Image

from src.tissue_detector import detect_tissue, TissueResult


class TestTissueDetector:
    """Tests for detect_tissue()."""

    def test_white_tile_is_not_tissue(self):
        """A pure white tile (glass background) should not be tissue."""
        white = Image.fromarray(np.full((256, 256, 3), 255, dtype=np.uint8), "RGB")
        result = detect_tissue(white)
        assert result.is_tissue is False
        assert result.tissue_ratio < 0.05

    def test_black_tile_is_not_tissue(self):
        """A pure black tile has no saturation and is not tissue."""
        black = Image.fromarray(np.zeros((256, 256, 3), dtype=np.uint8), "RGB")
        result = detect_tissue(black)
        assert result.is_tissue is False

    def test_grey_tile_is_not_tissue(self):
        """A mid-grey tile has no saturation and is not tissue."""
        grey = Image.fromarray(np.full((256, 256, 3), 128, dtype=np.uint8), "RGB")
        result = detect_tissue(grey)
        assert result.is_tissue is False

    def test_coloured_tile_is_tissue(self):
        """A strongly coloured tile should be detected as tissue."""
        # Simulate H&E stain — pinkish/purple
        tile = np.zeros((256, 256, 3), dtype=np.uint8)
        tile[:, :, 0] = 180  # R
        tile[:, :, 1] = 100  # G
        tile[:, :, 2] = 160  # B
        img = Image.fromarray(tile, "RGB")
        result = detect_tissue(img)
        assert result.is_tissue is True
        assert result.tissue_ratio > 0.5

    def test_partially_coloured_tile(self):
        """A tile that is ~50% white and ~50% coloured."""
        tile = np.full((256, 256, 3), 255, dtype=np.uint8)  # start white
        # Fill bottom half with colour
        tile[128:, :, 0] = 200
        tile[128:, :, 1] = 80
        tile[128:, :, 2] = 120
        img = Image.fromarray(tile, "RGB")
        result = detect_tissue(img)
        # With 50% tissue pixels, should pass the 15% threshold
        assert result.is_tissue is True
        assert 0.3 < result.tissue_ratio < 0.7

    def test_custom_threshold(self):
        """Raising the threshold can flip a borderline tile."""
        tile = np.full((256, 256, 3), 255, dtype=np.uint8)
        # 10% coloured pixels
        tile[:26, :, 0] = 200
        tile[:26, :, 1] = 50
        tile[:26, :, 2] = 100
        img = Image.fromarray(tile, "RGB")

        result_low = detect_tissue(img, threshold=0.05)
        result_high = detect_tissue(img, threshold=0.20)
        assert result_low.is_tissue is True
        assert result_high.is_tissue is False

    def test_returns_dataclass(self):
        """Result is a TissueResult dataclass with expected fields."""
        img = Image.fromarray(np.full((64, 64, 3), 200, dtype=np.uint8), "RGB")
        result = detect_tissue(img)
        assert isinstance(result, TissueResult)
        assert isinstance(result.is_tissue, bool)
        assert isinstance(result.tissue_ratio, float)
        assert 0.0 <= result.tissue_ratio <= 1.0
