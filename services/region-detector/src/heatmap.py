"""Heatmap generator — converts a 2-D tile probability grid into an overlay image.

The output is a semi-transparent PNG where:
    Red/Orange  → high tumour probability
    Green/Blue  → low tumour probability
    Transparent → non-tissue (background / glass)

The image is sized so that each grid cell maps to one DZI tile (256×256 px)
at the analysis level.  It can be overlaid directly on the OpenSeadragon viewer.
"""

from __future__ import annotations

import io
from typing import List, Optional

import matplotlib
import matplotlib.cm as cm
import numpy as np
from PIL import Image

# Use a non-interactive backend so we don't need a display
matplotlib.use("Agg")


def generate_heatmap(
    grid: np.ndarray,
    tile_size: int = 256,
    colormap: str = "RdYlGn_r",
    alpha: int = 160,
    upscale: bool = False,
) -> Image.Image:
    """Create a colour-mapped overlay from a probability grid.

    Parameters
    ----------
    grid:
        2-D array ``(rows, cols)`` where each cell is a tumour probability
        in ``[0, 1]``, or ``-1`` for non-tissue tiles.
    tile_size:
        Pixel dimension of each DZI tile (used when *upscale* is True).
    colormap:
        Matplotlib colour-map name.  ``RdYlGn_r`` maps 0 → green, 1 → red.
    alpha:
        Overlay opacity (0-255) for tissue cells.
    upscale:
        If True, the output image is upscaled so each cell covers
        ``tile_size × tile_size`` pixels.  If False the image is one pixel
        per cell (compact; useful for storage).

    Returns
    -------
    PIL.Image.Image
        RGBA image.
    """
    rows, cols = grid.shape
    cmap = cm.get_cmap(colormap)

    rgba = np.zeros((rows, cols, 4), dtype=np.uint8)
    for y in range(rows):
        for x in range(cols):
            prob = grid[y, x]
            if prob < 0:
                # Non-tissue → fully transparent
                continue
            r, g, b, _ = cmap(prob)
            rgba[y, x] = [int(r * 255), int(g * 255), int(b * 255), alpha]

    img = Image.fromarray(rgba, "RGBA")
    if upscale:
        img = img.resize(
            (cols * tile_size, rows * tile_size), Image.Resampling.NEAREST
        )
    return img


def heatmap_to_png_bytes(heatmap: Image.Image) -> bytes:
    """Encode a heatmap image to PNG bytes for storage / transmission."""
    buf = io.BytesIO()
    heatmap.save(buf, format="PNG")
    return buf.getvalue()
