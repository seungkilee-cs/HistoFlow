from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Tuple
from urllib.parse import urlparse
import tempfile
import shutil

from minio import Minio

SUPPORTED_SCHEMES = {"s3", "minio"}

def parse_uri(uri: str) -> Tuple[str, str]:
    """Parse an s3/minio URI into (bucket, key).
    
    Example: s3://my-bucket/path/to/file.svs -> ("my-bucket", "path/to/file.svs")
    """
    p = urlparse(uri)
    if p.scheme not in SUPPORTED_SCHEMES:
        raise ValueError(f"Unsupported URI scheme: {p.scheme}")
    if not p.netloc:
        raise ValueError("Missing bucket in URI")
    bucket = p.netloc
    key = p.path.lstrip("/")
    if not key:
        raise ValueError("Missing object key in URI")
    return bucket, key


@dataclass
class MinioConfig:
    endpoint: str
    access_key: str
    secret_key: str
    secure: bool = False

    def client(self) -> Minio:
        return Minio(
            self.endpoint,
            access_key=self.access_key,
            secret_key=self.secret_key,
            secure=self.secure,
        )


def download_to_temp(uri: str, cfg: MinioConfig) -> Path:
    """Download an object to a temporary directory and return the local file path.

    Creates a temp dir with prefix 'skreg_' that the caller can clean up if desired.
    """
    bucket, key = parse_uri(uri)
    client = cfg.client()
    tmp_dir = Path(tempfile.mkdtemp(prefix="skreg_"))
    local_path = tmp_dir / Path(key).name
    client.fget_object(bucket, key, str(local_path))
    return local_path


def cleanup_temp(path: Path) -> None:
    """Remove a temp file or directory created for downloads."""
    try:
        p = Path(path)
        if p.is_dir():
            shutil.rmtree(p, ignore_errors=True)
        elif p.exists():
            p.unlink(missing_ok=True)
    except Exception:
        # Best-effort cleanup
        pass
