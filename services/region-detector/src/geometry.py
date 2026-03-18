"""Geometry helpers for mapping DZI tile coordinates to full-resolution pixels."""

from __future__ import annotations

import math
from dataclasses import dataclass


@dataclass
class DZIShape:
    width: int
    height: int
    tile_size: int


def max_dzi_level(shape: DZIShape) -> int:
    """Deep Zoom max level for the full-resolution image."""
    return int(math.ceil(math.log2(max(shape.width, shape.height))))


def tile_rect_in_fullres(
    *,
    shape: DZIShape,
    tile_level: int,
    max_level: int,
    tile_x: int,
    tile_y: int,
) -> tuple[int, int, int, int]:
    """
    Convert (tile_level, tile_x, tile_y) into full-resolution pixel rectangle.

    Returns: (pixel_x, pixel_y, width, height) in full-resolution slide coordinates.
    """
    if tile_level > max_level:
        raise ValueError(f"tile_level {tile_level} exceeds max_level {max_level}")

    scale = 2 ** (max_level - tile_level)

    level_width = int(math.ceil(shape.width / scale))
    level_height = int(math.ceil(shape.height / scale))

    level_px = tile_x * shape.tile_size
    level_py = tile_y * shape.tile_size

    # Clamp at level coordinates for partial edge tiles.
    level_w = max(0, min(shape.tile_size, level_width - level_px))
    level_h = max(0, min(shape.tile_size, level_height - level_py))

    pixel_x = level_px * scale
    pixel_y = level_py * scale
    width = level_w * scale
    height = level_h * scale

    # Final clamp in full-res space to prevent edge overrun.
    width = max(0, min(width, shape.width - pixel_x))
    height = max(0, min(height, shape.height - pixel_y))

    return pixel_x, pixel_y, width, height
