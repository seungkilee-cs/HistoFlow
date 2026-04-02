import io
import json
import os
import shutil
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional, Tuple
from urllib import error, request
from xml.etree import ElementTree

import pyvips
from minio import Minio

from .config import settings

# Number of parallel tile upload threads.  16 gives a good balance between
# throughput and MinIO connection-pool pressure.
_UPLOAD_WORKERS = 16


class TilingService:
    def __init__(self):
        """Initializes the service and the MinIO client."""
        self.minio_client = Minio(
            settings.MINIO_ENDPOINT,
            access_key=settings.MINIO_ACCESS_KEY,
            secret_key=settings.MINIO_SECRET_KEY,
            secure=settings.MINIO_SECURE
        )
        self._upload_bucket_ready = False
        Path(settings.TEMP_STORAGE_PATH).mkdir(parents=True, exist_ok=True)

    def process_image(
        self,
        job_id: Optional[str],
        image_id: str,
        source_object_name: str,
        source_bucket: str,
        dataset_name: Optional[str] = None,
    ):
        """Main orchestrator: download → tile → upload → cleanup."""
        print(f"Starting processing for image_id='{image_id}'")
        local_image_path = None
        local_tiles_dir = None

        try:
            print(
                f"Job metadata: dataset_name='{dataset_name or 'N/A'}', "
                f"bucket='{source_bucket}', object='{source_object_name}'"
            )

            # 1. Download
            self._notify_job_event(
                job_id=job_id,
                stage="DOWNLOADING",
                message="Downloading source image.",
                dataset_name=dataset_name,
                activity_entries=[self._build_activity_entry("DOWNLOADING", "Downloading source image.")],
            )
            download_start = time.perf_counter()
            local_image_path, source_stat = self._download_source_image(source_object_name, source_bucket)
            download_duration = time.perf_counter() - download_start

            # 2. Tile
            self._notify_job_event(
                job_id=job_id,
                stage="TILING",
                message="Generating Deep Zoom tiles.",
                dataset_name=dataset_name,
                activity_entries=[self._build_activity_entry("TILING", "Generating Deep Zoom tiles.")],
            )
            tiling_start = time.perf_counter()
            local_tiles_dir = self._generate_tiles(local_image_path, image_id)
            tiling_duration = time.perf_counter() - tiling_start

            # 3. Upload (parallel)
            self._notify_job_event(
                job_id=job_id,
                stage="UPLOADING",
                message="Uploading tiles and metadata.",
                dataset_name=dataset_name,
                activity_entries=[self._build_activity_entry("UPLOADING", "Uploading tiles and metadata.")],
            )
            self._ensure_upload_bucket()
            upload_start = time.perf_counter()
            file_count, total_bytes = self._upload_tiles(local_tiles_dir, image_id, job_id, dataset_name)
            upload_duration = time.perf_counter() - upload_start
            manifest = self._build_manifest(local_tiles_dir, image_id)

            self._notify_job_event(
                job_id=job_id,
                stage="UPLOADING",
                message="Uploading metadata.",
                dataset_name=dataset_name,
                stage_progress_percent=100,
                activity_entries=[
                    self._build_activity_entry(
                        "UPLOADING",
                        "Uploading metadata.",
                        detail=f"Uploading {image_id}/metadata.json",
                    )
                ],
            )
            total_duration = download_duration + tiling_duration + upload_duration

            self._write_metadata(
                image_id=image_id,
                dataset_name=dataset_name,
                source_bucket=source_bucket,
                source_object_name=source_object_name,
                source_stat=source_stat,
                file_count=file_count,
                total_bytes=total_bytes,
                timings={
                    "download_seconds": round(download_duration, 3),
                    "tiling_seconds": round(tiling_duration, 3),
                    "upload_seconds": round(upload_duration, 3),
                    "total_seconds": round(total_duration, 3),
                },
            )
            self._write_manifest(manifest)

            print(
                "Processing summary: "
                f"download={download_duration:.3f}s, "
                f"tiling={tiling_duration:.3f}s, "
                f"upload={upload_duration:.3f}s, "
                f"total={total_duration:.3f}s, "
                f"files={file_count}, bytes={total_bytes}"
            )

            self._notify_job_event(
                job_id=job_id,
                stage="COMPLETED",
                message="Tiles are ready.",
                metadata_path=f"{image_id}/metadata.json",
                dataset_name=dataset_name,
                stage_progress_percent=100,
                activity_entries=[self._build_activity_entry("COMPLETED", "Tiles are ready.")],
            )

        except Exception as e:
            print(f"ERROR processing image_id='{image_id}': {e}")
            self._notify_job_event(
                job_id=job_id,
                stage="FAILED",
                message="Tiling failed.",
                failure_reason=str(e),
                dataset_name=dataset_name,
                activity_entries=[self._build_activity_entry("FAILED", "Tiling failed.", detail=str(e))],
            )
        finally:
            print("Cleaning up local files...")
            if local_image_path and os.path.exists(local_image_path):
                os.remove(local_image_path)
            if local_tiles_dir and os.path.exists(local_tiles_dir):
                shutil.rmtree(local_tiles_dir)
            print("Cleanup complete.")

    # ── Notification ──────────────────────────────────────────────────────────

    def _notify_job_event(
        self,
        *,
        job_id: Optional[str],
        stage: str,
        message: str,
        dataset_name: Optional[str] = None,
        failure_reason: Optional[str] = None,
        metadata_path: Optional[str] = None,
        stage_progress_percent: Optional[int] = None,
        activity_entries: Optional[list[dict[str, Any]]] = None,
    ) -> None:
        if not job_id or not settings.BACKEND_INTERNAL_BASE_URL:
            return

        endpoint = (
            f"{settings.BACKEND_INTERNAL_BASE_URL.rstrip('/')}"
            f"/api/v1/internal/tiling/jobs/{job_id}/events"
        )
        payload = {
            "stage": stage,
            "message": message,
            "datasetName": dataset_name,
            "failureReason": failure_reason,
            "metadataPath": metadata_path,
            "stageProgressPercent": stage_progress_percent,
            "activityEntries": activity_entries or [],
        }
        body = json.dumps(payload).encode("utf-8")
        req = request.Request(
            endpoint,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with request.urlopen(req, timeout=10) as response:
                if response.status >= 400:
                    print(f"Backend notify failed for job '{job_id}' stage '{stage}': HTTP {response.status}")
        except error.URLError as exc:
            print(f"Backend notify failed for job '{job_id}' stage '{stage}': {exc}")

    # ── Download ──────────────────────────────────────────────────────────────

    def _download_source_image(self, object_name: str, bucket: str) -> Tuple[Path, object]:
        local_path = Path(settings.TEMP_STORAGE_PATH) / Path(object_name).name
        print(f"Fetching metadata for {bucket}/{object_name}...")
        stat = self.minio_client.stat_object(bucket, object_name)
        print(f"Source object: size={stat.size} bytes, type='{stat.content_type}'")
        print(f"Downloading {bucket}/{object_name} → {local_path}...")
        self.minio_client.fget_object(bucket, object_name, str(local_path))
        print("Download complete.")
        return local_path, stat

    # ── Tiling ────────────────────────────────────────────────────────────────

    def _generate_tiles(self, input_image_path: Path, image_id: str) -> Path:
        print(f"Generating DZI tiles for {input_image_path.name}...")
        image = pyvips.Image.new_from_file(str(input_image_path), access='sequential')

        output_path = Path(settings.TEMP_STORAGE_PATH) / image_id
        output_path.mkdir(parents=True, exist_ok=True)

        base_path = output_path / "image"
        image.dzsave(str(base_path), suffix=".jpg[Q=85]", overlap=0, tile_size=256)

        print(f"Tiles generated at {output_path}")
        return output_path

    # ── Upload (parallel) ─────────────────────────────────────────────────────

    def _upload_tiles(
        self,
        tiles_dir: Path,
        image_id: str,
        job_id: Optional[str],
        dataset_name: Optional[str],
    ) -> Tuple[int, int]:
        """Upload the tile directory to MinIO using a thread pool."""
        bucket = settings.MINIO_UPLOAD_BUCKET
        print(f"Uploading tiles to bucket '{bucket}' with {_UPLOAD_WORKERS} workers...")

        file_paths = sorted(p for p in tiles_dir.rglob("*") if p.is_file())
        total_files = len(file_paths)
        if total_files == 0:
            raise RuntimeError(f"No tile files found in {tiles_dir}")

        self._notify_job_event(
            job_id=job_id,
            stage="UPLOADING",
            message="Uploading generated tiles to object storage.",
            dataset_name=dataset_name,
            stage_progress_percent=0,
            activity_entries=[
                self._build_activity_entry(
                    "UPLOADING",
                    "Preparing generated tiles for upload.",
                    detail=f"Found {total_files:,} files to upload.",
                )
            ],
        )

        file_count = 0
        total_bytes = 0
        last_reported_percent = -1
        lock = threading.Lock()

        def upload_one(file_path: Path) -> int:
            relative = file_path.relative_to(tiles_dir)
            object_name = f"{image_id}/{relative}"
            self.minio_client.fput_object(bucket, object_name, str(file_path))
            return file_path.stat().st_size

        with ThreadPoolExecutor(max_workers=_UPLOAD_WORKERS) as executor:
            futures = {executor.submit(upload_one, fp): fp for fp in file_paths}
            for future in as_completed(futures):
                size = future.result()  # propagates any upload exception
                with lock:
                    file_count += 1
                    total_bytes += size
                    percent = int((file_count / total_files) * 100)
                    should_report = (
                        file_count == total_files
                        or file_count == 1
                        or percent >= last_reported_percent + 5
                        or file_count % 250 == 0
                    )
                    if should_report:
                        last_reported_percent = percent
                        self._notify_job_event(
                            job_id=job_id,
                            stage="UPLOADING",
                            message="Uploading generated tiles to object storage.",
                            dataset_name=dataset_name,
                            stage_progress_percent=percent,
                            activity_entries=[
                                self._build_activity_entry(
                                    "UPLOADING",
                                    "Uploading generated tiles to object storage.",
                                    detail=f"Uploaded {file_count:,} / {total_files:,} files.",
                                )
                            ],
                        )

        print(f"Upload complete: {file_count} files, {total_bytes} bytes.")
        return file_count, total_bytes

    # ── Metadata ──────────────────────────────────────────────────────────────

    def _write_metadata(
        self,
        *,
        image_id: str,
        dataset_name: Optional[str],
        source_bucket: str,
        source_object_name: str,
        source_stat: object,
        file_count: int,
        total_bytes: int,
        timings: dict,
    ) -> None:
        bucket = settings.MINIO_UPLOAD_BUCKET
        metadata = {
            "image_id": image_id,
            "dataset_name": dataset_name,
            "source_bucket": source_bucket,
            "source_object_name": source_object_name,
            "source_size_bytes": getattr(source_stat, "size", None),
            "source_content_type": getattr(source_stat, "content_type", None),
            "tile_upload_bucket": bucket,
            "tile_file_count": file_count,
            "tile_total_size_bytes": total_bytes,
            "manifest_path": f"{image_id}/manifest.json",
            "timings": timings,
            "generated_at": datetime.now(timezone.utc).isoformat(),
        }
        metadata_json = json.dumps(metadata, indent=2)
        metadata_bytes = metadata_json.encode("utf-8")
        metadata_key = f"{image_id}/metadata.json"
        print(f"Uploading metadata to {bucket}/{metadata_key}")
        self.minio_client.put_object(
            bucket,
            metadata_key,
            data=io.BytesIO(metadata_bytes),
            length=len(metadata_bytes),
            content_type="application/json",
        )

    def _write_manifest(self, manifest: dict[str, Any]) -> None:
        bucket = settings.MINIO_UPLOAD_BUCKET
        manifest_key = f"{manifest['image_id']}/manifest.json"
        manifest_bytes = json.dumps(manifest, indent=2).encode("utf-8")
        print(f"Uploading manifest to {bucket}/{manifest_key}")
        self.minio_client.put_object(
            bucket,
            manifest_key,
            data=io.BytesIO(manifest_bytes),
            length=len(manifest_bytes),
            content_type="application/json",
        )

    # ── Helpers ───────────────────────────────────────────────────────────────

    def _ensure_upload_bucket(self) -> None:
        if self._upload_bucket_ready:
            return

        bucket = settings.MINIO_UPLOAD_BUCKET
        if not self.minio_client.bucket_exists(bucket):
            self.minio_client.make_bucket(bucket)
        self._upload_bucket_ready = True

    def _build_manifest(self, tiles_dir: Path, image_id: str) -> dict[str, Any]:
        dzi_path = tiles_dir / "image.dzi"
        root = ElementTree.fromstring(dzi_path.read_bytes())
        namespace = ""
        if root.tag.startswith("{"):
            namespace = root.tag.split("}")[0] + "}"

        size_el = root.find(f"{namespace}Size")
        if size_el is None:
            raise RuntimeError(f"Missing <Size> element in {dzi_path}")

        levels_dir = tiles_dir / "image_files"
        level_tile_counts = {
            level.name: len([tile for tile in level.iterdir() if tile.is_file()])
            for level in sorted(levels_dir.iterdir(), key=lambda entry: int(entry.name))
            if level.is_dir() and level.name.isdigit()
        }

        return {
            "image_id": image_id,
            "width": int(size_el.attrib["Width"]),
            "height": int(size_el.attrib["Height"]),
            "tile_size": int(root.attrib.get("TileSize", "256")),
            "format": root.attrib.get("Format", "jpg"),
            "available_levels": [int(level) for level in level_tile_counts.keys()],
            "level_tile_counts": level_tile_counts,
            "generated_at": datetime.now(timezone.utc).isoformat(),
        }

    def _build_activity_entry(
        self,
        stage: str,
        message: str,
        detail: Optional[str] = None,
    ) -> dict[str, Any]:
        return {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "stage": stage,
            "message": message,
            "detail": detail,
        }
