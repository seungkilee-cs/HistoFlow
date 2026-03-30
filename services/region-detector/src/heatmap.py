"""Heatmap generator — converts tile predictions into an overlay image.

The output is a semi-transparent PNG where:
    Red/Orange  → high tumour probability
    Green/Blue  → low tumour probability
    Transparent → non-tissue (background / glass)

Two rendering modes:

* **Pixel-accurate** (default when image dimensions are provided): each tile
  cell is painted at its exact full-resolution pixel extent (``pixel_x``,
  ``pixel_y``, ``width``, ``height`` from TilePrediction).  This aligns the
  heatmap perfectly with the region-box overlays rendered by the viewer,
  which use the same coordinate space.  Used for images whose total pixel
  count is ≤ ``MAX_FULLRES_PIXELS`` (default 25 MP).

* **Grid fallback**: a compact 1-pixel-per-cell image that is stretched to
  cover the image canvas.  Used for very large slides where rendering at
  full resolution would be impractical.  Slight cell-boundary drift occurs at
  edge tiles, but this is acceptable for large pathology slides where the
  analysis level tiles are many pixels in full-res space.
"""

from __future__ import annotations

import io
from dataclasses import dataclass
from typing import List, Optional, Sequence

import matplotlib
import matplotlib.cm as cm
import numpy as np
from PIL import Image

# Use a non-interactive backend so we don't need a display
matplotlib.use("Agg")

# Images above this pixel count fall back to the compact grid rendering.
MAX_FULLRES_PIXELS = 25_000_000  # 25 MP


@dataclass
class TileCell:
    """Minimal tile description needed by the heatmap renderer."""

    pixel_x: int
    pixel_y: int
    width: int
    height: int
    tumor_probability: float  # -1.0 = skipped/non-tissue, 0-1 = classified


def generate_heatmap(
    grid: np.ndarray,
    tile_size: int = 256,
    colormap: str = "RdYlGn_r",
    alpha: int = 160,
    upscale: bool = False,
    skipped_alpha: int = 25,
    # Pixel-accurate rendering — pass these for correct alignment:
    image_width: Optional[int] = None,
    image_height: Optional[int] = None,
    tile_cells: Optional[Sequence[TileCell]] = None,
) -> Image.Image:
    """Create a colour-mapped overlay from a probability grid.

    Parameters
    ----------
    grid:
        2-D array ``(rows, cols)`` where each cell is a tumour probability
        in ``[0, 1]``, or ``-1`` for non-tissue / skipped tiles.
    tile_size:
        Pixel dimension of each DZI tile.  Used for the grid fallback only.
    colormap:
        Matplotlib colour-map name.  ``RdYlGn_r`` maps 0 → green, 1 → red.
    alpha:
        Overlay opacity (0-255) for analysed tissue cells.
    upscale:
        Grid-fallback only: if True the compact grid image is scaled so each
        cell covers ``tile_size × tile_size`` pixels.
    skipped_alpha:
        Opacity (0-255) for tiles skipped during analysis (prob = -1).
        Set to 0 for fully transparent skipped cells.
    image_width, image_height:
        Full-resolution dimensions of the source image.  Required for
        pixel-accurate rendering.
    tile_cells:
        Sequence of :class:`TileCell` objects describing each tile's exact
        full-resolution pixel extent and probability.  Required for
        pixel-accurate rendering.

    Returns
    -------
    PIL.Image.Image
        RGBA image.
    """
    cmap = cm.get_cmap(colormap)

    # ── Pixel-accurate mode ───────────────────────────────────────────────────
    if (
        image_width is not None
        and image_height is not None
        and tile_cells is not None
        and image_width * image_height <= MAX_FULLRES_PIXELS
    ):
        rgba = np.zeros((image_height, image_width, 4), dtype=np.uint8)
        for cell in tile_cells:
            px0 = cell.pixel_x
            py0 = cell.pixel_y
            px1 = min(px0 + cell.width, image_width)
            py1 = min(py0 + cell.height, image_height)
            prob = cell.tumor_probability
            if prob < 0:
                if skipped_alpha > 0:
                    rgba[py0:py1, px0:px1] = [128, 128, 128, skipped_alpha]
            else:
                r, g, b, _ = cmap(prob)
                rgba[py0:py1, px0:px1] = [
                    int(r * 255), int(g * 255), int(b * 255), alpha
                ]
        return Image.fromarray(rgba, "RGBA")

    # ── Grid fallback ─────────────────────────────────────────────────────────
    rows, cols = grid.shape
    rgba = np.zeros((rows, cols, 4), dtype=np.uint8)
    for y in range(rows):
        for x in range(cols):
            prob = grid[y, x]
            if prob < 0:
                if skipped_alpha > 0:
                    rgba[y, x] = [128, 128, 128, skipped_alpha]
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
