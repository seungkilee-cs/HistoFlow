"""Helpers for choosing an analysis level from available Deep Zoom levels."""

from __future__ import annotations

from typing import Iterable


def select_analysis_level(
    available_levels: Iterable[int],
    requested_level: int | None = None,
    default_level: int | None = None,
) -> int:
    levels = sorted(set(int(level) for level in available_levels))
    if not levels:
        raise ValueError("No tile levels are available for analysis")

    if requested_level is None and default_level is None:
        raise ValueError("A requested level or default level is required")

    target = requested_level if requested_level is not None else default_level

    return min(
        levels,
        key=lambda level: (abs(level - target), level > target, level),
    )
