"""MinIO I/O helpers for the region-detector service.

Provides listing, downloading, and uploading of tile images and analysis
artifacts from/to the MinIO object store.
"""

from __future__ import annotations

import io
import json
import re
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, List, Optional
from xml.etree import ElementTree

from minio import Minio
from minio.error import S3Error
from PIL import Image

from .config import settings


_client_instance: Optional[Minio] = None
_client_lock = threading.Lock()


def _client() -> Minio:
    global _client_instance
    with _client_lock:
        if _client_instance is None:
            _client_instance = Minio(
                settings.MINIO_ENDPOINT,
                access_key=settings.MINIO_ACCESS_KEY,
                secret_key=settings.MINIO_SECRET_KEY,
                secure=settings.MINIO_SECURE,
            )
    return _client_instance


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


@dataclass
class TileManifest:
    image_id: str
    width: int
    height: int
    tile_size: int
    format: str
    available_levels: List[int]
    level_tile_counts: dict[int, int]


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

    for obj in client.list_objects(bucket, prefix=prefix, recursive=True):
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

    for obj in client.list_objects(bucket, prefix=prefix, recursive=True):
        relative_path = obj.object_name[len(prefix):]
        level_token = relative_path.split("/", 1)[0]
        if level_token.isdigit():
            levels.add(int(level_token))

    return sorted(levels)


def load_tile_manifest(
    image_id: str,
    bucket: str | None = None,
) -> TileManifest | None:
    bucket = bucket or settings.TILES_BUCKET
    client = _client()
    object_key = f"{image_id}/manifest.json"
    try:
        resp = client.get_object(bucket, object_key)
    except S3Error as exc:
        if exc.code in {"NoSuchKey", "NoSuchObject"}:
            return None
        raise

    try:
        payload = json.loads(resp.read())
    finally:
        resp.close()
        resp.release_conn()

    counts = {
        int(level): int(count)
        for level, count in (payload.get("level_tile_counts") or {}).items()
    }
    return TileManifest(
        image_id=payload["image_id"],
        width=int(payload["width"]),
        height=int(payload["height"]),
        tile_size=int(payload["tile_size"]),
        format=str(payload.get("format", "jpg")),
        available_levels=[int(level) for level in payload.get("available_levels", [])],
        level_tile_counts=counts,
    )


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


def upload_json(
    payload: Any,
    object_key: str,
    bucket: str | None = None,
) -> None:
    data = json.dumps(payload, indent=2).encode("utf-8")
    upload_bytes(data, object_key, content_type="application/json", bucket=bucket)


def download_json(
    object_key: str,
    bucket: str | None = None,
) -> Any:
    bucket = bucket or settings.TILES_BUCKET
    client = _client()
    resp = client.get_object(bucket, object_key)
    try:
        return json.loads(resp.read())
    finally:
        resp.close()
        resp.release_conn()
