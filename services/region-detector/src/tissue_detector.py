"""Tissue detector — separates actual tissue tiles from glass/background.

Histopathology slides are mostly white background (glass). Running inference
on blank tiles wastes compute and dilutes the results.  This module uses the
**saturation channel** of the HSV colour space to decide whether a tile
contains meaningful tissue:

    tissue pixels → colourful (H&E stain)  → high saturation
    background    → white / near-white     → near-zero saturation

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
) -> TissueResult:
    """Determine whether *tile* contains enough tissue to be worth analysing.

    Parameters
    ----------
    tile:
        A PIL RGB image (typically 256×256 from a DZI pyramid).
    threshold:
        Minimum fraction of pixels that must exceed *saturation_floor*
        for the tile to be classified as tissue.
    saturation_floor:
        Absolute saturation value (0-255) below which a pixel is
        considered background.

    Returns
    -------
    TissueResult
        ``is_tissue`` flag and the computed ``tissue_ratio``.
    """
    hsv = np.array(tile.convert("HSV"))
    saturation = hsv[:, :, 1]  # 0 = grey/white, 255 = fully saturated
    tissue_pixels = np.sum(saturation > saturation_floor)
    total_pixels = saturation.size
    ratio = float(tissue_pixels / total_pixels)
    return TissueResult(is_tissue=ratio >= threshold, tissue_ratio=ratio)
