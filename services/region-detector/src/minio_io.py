"""MinIO I/O helpers for the region-detector service.

Provides listing, downloading, and uploading of tile images and analysis
artifacts from/to the MinIO object store.
"""

from __future__ import annotations

import io
import re
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional
from xml.etree import ElementTree

from minio import Minio
from PIL import Image

from .config import settings


def _client() -> Minio:
    return Minio(
        settings.MINIO_ENDPOINT,
        access_key=settings.MINIO_ACCESS_KEY,
        secret_key=settings.MINIO_SECRET_KEY,
        secure=settings.MINIO_SECURE,
    )


# ── Data classes ──────────────────────────────────────────────────────────────


@dataclass
class DZIInfo:
    """Parsed metadata from a DZI descriptor."""

    width: int
    height: int
    tile_size: int
    overlap: int
    format: str  # e.g. "jpg"


@dataclass
class TileRef:
    """Reference to a single tile in MinIO, with its grid coordinates."""

    level: int
    x: int
    y: int
    object_key: str


# ── Public helpers ────────────────────────────────────────────────────────────


def parse_dzi(image_id: str, bucket: str | None = None) -> DZIInfo:
    """Download and parse the DZI XML descriptor for *image_id*."""
    bucket = bucket or settings.TILES_BUCKET
    client = _client()
    dzi_key = f"{image_id}/image.dzi"
    resp = client.get_object(bucket, dzi_key)
    try:
        xml_bytes = resp.read()
    finally:
        resp.close()
        resp.release_conn()

    root = ElementTree.fromstring(xml_bytes)
    # DZI namespace varies; handle with or without it
    ns = ""
    if root.tag.startswith("{"):
        ns = root.tag.split("}")[0] + "}"

    fmt = root.attrib.get("Format", "jpg")
    overlap = int(root.attrib.get("Overlap", "0"))
    tile_size = int(root.attrib.get("TileSize", "256"))

    size_el = root.find(f"{ns}Size")
    if size_el is None:
        raise ValueError(f"Missing <Size> element in DZI for {image_id}")

    width = int(size_el.attrib["Width"])
    height = int(size_el.attrib["Height"])

    return DZIInfo(
        width=width,
        height=height,
        tile_size=tile_size,
        overlap=overlap,
        format=fmt,
    )


def list_tiles_at_level(
    image_id: str,
    level: int,
    bucket: str | None = None,
) -> List[TileRef]:
    """Return all tile object keys for *image_id* at the given DZI *level*."""
    bucket = bucket or settings.TILES_BUCKET
    client = _client()
    prefix = f"{image_id}/image_files/{level}/"
    tile_pattern = re.compile(r"(\d+)_(\d+)\.\w+$")
    tiles: List[TileRef] = []

    for obj in client.list_objects(bucket, prefix=prefix):
        m = tile_pattern.search(obj.object_name)
        if m:
            tiles.append(
                TileRef(
                    level=level,
                    x=int(m.group(1)),
                    y=int(m.group(2)),
                    object_key=obj.object_name,
                )
            )

    return tiles


def list_available_tile_levels(
    image_id: str,
    bucket: str | None = None,
) -> List[int]:
    """Return all available DZI levels for *image_id*."""
    bucket = bucket or settings.TILES_BUCKET
    client = _client()
    prefix = f"{image_id}/image_files/"
    levels: set[int] = set()

    for obj in client.list_objects(bucket, prefix=prefix):
        relative_path = obj.object_name[len(prefix):]
        level_token = relative_path.split("/", 1)[0]
        if level_token.isdigit():
            levels.add(int(level_token))

    return sorted(levels)


def download_tile_image(
    object_key: str,
    bucket: str | None = None,
) -> Image.Image:
    """Download a tile from MinIO and return it as a PIL Image."""
    bucket = bucket or settings.TILES_BUCKET
    client = _client()
    resp = client.get_object(bucket, object_key)
    try:
        data = resp.read()
    finally:
        resp.close()
        resp.release_conn()
    return Image.open(io.BytesIO(data)).convert("RGB")


def upload_bytes(
    data: bytes,
    object_key: str,
    content_type: str = "application/octet-stream",
    bucket: str | None = None,
) -> None:
    """Upload raw bytes to MinIO."""
    bucket = bucket or settings.TILES_BUCKET
    client = _client()
    client.put_object(
        bucket,
        object_key,
        data=io.BytesIO(data),
        length=len(data),
        content_type=content_type,
    )
