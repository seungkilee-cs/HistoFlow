"""Tissue detector — separates actual tissue tiles from glass/background.

Histopathology slides are mostly white background (glass). Running inference
on blank tiles wastes compute and dilutes the results.  This module uses two
complementary strategies so that it works for **any** image type:

Primary (H&E / pathology):
    Uses the **saturation channel** of the HSV colour space.
    tissue pixels → colourful (H&E stain)  → high saturation
    background    → white / near-white     → near-zero saturation

Fallback (generic / non-pathology):
    Uses pixel **standard deviation** (grayscale) to reject near-uniform tiles.
    Non-blank content → high variance   → is_tissue = True
    Pure white / black → low variance  → is_tissue = False

The fallback activates automatically when the saturation check fails,
enabling correct detection for natural photos, X-rays, fluorescence
microscopy, or any other image type that does not rely on H&E colouring.

The default threshold (0.15 = 15% of pixels must exceed a saturation floor)
works well for H&E-stained slides.  It can be tuned via the
``TISSUE_THRESHOLD`` environment variable.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from PIL import Image


@dataclass
class TissueResult:
    """Result of a single tissue-detection check."""

    is_tissue: bool
    tissue_ratio: float  # 0.0 – 1.0


def detect_tissue(
    tile: Image.Image,
    threshold: float = 0.15,
    saturation_floor: int = 30,
    variance_fallback: bool = True,
    std_floor: float = 8.0,
) -> TissueResult:
    """Determine whether *tile* contains enough content to be worth analysing.

    First tries an H&E-specific saturation check.  If that rejects the tile
    and *variance_fallback* is True, a second check on pixel standard deviation
    is applied so that non-pathology images are handled correctly.

    Parameters
    ----------
    tile:
        A PIL RGB image (typically 256×256 from a DZI pyramid).
    threshold:
        Minimum fraction of pixels that must exceed *saturation_floor*
        for the tile to pass the saturation check.
    saturation_floor:
        Absolute saturation value (0-255) below which a pixel is
        considered background in the H&E check.
    variance_fallback:
        When True, tiles that fail the saturation check are tested against
        pixel standard deviation.  Disabling this restores the original
        H&E-only behaviour.
    std_floor:
        Minimum grayscale standard deviation (0-255) for a tile to be
        considered non-blank content in the fallback check.  JPEG background
        tiles typically have std < 5; real content is usually ≥ 10–20.

    Returns
    -------
    TissueResult
        ``is_tissue`` flag and the computed ``tissue_ratio``.
    """
    # ── Primary: H&E saturation check ────────────────────────────────────
    hsv = np.array(tile.convert("HSV"))
    saturation = hsv[:, :, 1]  # 0 = grey/white, 255 = fully saturated
    tissue_pixels = np.sum(saturation > saturation_floor)
    total_pixels = saturation.size
    ratio = float(tissue_pixels / total_pixels)

    if ratio >= threshold:
        return TissueResult(is_tissue=True, tissue_ratio=ratio)

    # ── Fallback: variance-based content detection ────────────────────────
    # Catches non-H&E images (photos, X-rays, fluorescence, etc.) where
    # saturation is low but the tile still contains real content.
    if variance_fallback:
        gray = np.array(tile.convert("L"), dtype=np.float32)
        std = float(np.std(gray))
        if std >= std_floor:
            # Express tissue_ratio as normalised std so callers have a
            # meaningful 0-1 value regardless of detection strategy.
            variance_ratio = min(std / 255.0, 1.0)
            return TissueResult(is_tissue=True, tissue_ratio=max(ratio, variance_ratio))

    return TissueResult(is_tissue=False, tissue_ratio=ratio)
