import io
import json
import os
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional, Tuple

import pyvips
from minio import Minio

# Import our new centralized settings
from .config import settings

class TilingService:
    def __init__(self):
        """Initializes the service and the MinIO client."""
        self.minio_client = Minio(
            settings.MINIO_ENDPOINT,
            access_key=settings.MINIO_ACCESS_KEY,
            secret_key=settings.MINIO_SECRET_KEY,
            secure=settings.MINIO_SECURE
        )
        # Ensure the temporary directory for our work exists
        Path(settings.TEMP_STORAGE_PATH).mkdir(parents=True, exist_ok=True)

    def process_image(
        self,
        image_id: str,
        source_object_name: str,
        source_bucket: str,
        dataset_name: Optional[str] = None,
    ):
        """
        The main orchestrator method for processing one image.
        This will download, tile, upload, and cleanup.
        """
        print(f"Starting processing for image_id='{image_id}'")
        local_image_path = None
        local_tiles_dir = None

        try:
            print(
                f"Job metadata: dataset_name='{dataset_name or 'N/A'}', "
                f"bucket='{source_bucket}', object='{source_object_name}'"
            )

            # 1. Download the source file from MinIO
            download_start = time.perf_counter()
            local_image_path, source_stat = self._download_source_image(
                source_object_name,
                source_bucket,
            )
            download_duration = time.perf_counter() - download_start

            # 2. Generate DZI tiles locally (logic from your script)
            tiling_start = time.perf_counter()
            local_tiles_dir = self._generate_tiles(local_image_path, image_id)
            tiling_duration = time.perf_counter() - tiling_start

            # 3. Upload the generated tiles to MinIO (logic from your script)
            upload_start = time.perf_counter()
            file_count, total_bytes = self._upload_tiles(local_tiles_dir, image_id)
            upload_duration = time.perf_counter() - upload_start

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

            print(
                "Processing summary: "
                f"download={download_duration:.3f}s, "
                f"tiling={tiling_duration:.3f}s, "
                f"upload={upload_duration:.3f}s, "
                f"total={total_duration:.3f}s, "
                f"files={file_count}, uploaded_bytes={total_bytes}"
            )

            print(f"Successfully processed image_id='{image_id}'")

        
        except Exception as e:
            print(f"ERROR processing image_id='{image_id}': {e}")
            # In a real system, you would add error reporting here
            # (e.g., update a status in the database via an API call)
        
        finally:
            # 4. Cleanup local files regardless of success or failure
            print("Cleaning up local files...")
            if local_image_path and os.path.exists(local_image_path):
                os.remove(local_image_path)
            if local_tiles_dir and os.path.exists(local_tiles_dir):
                shutil.rmtree(local_tiles_dir)
            print("Cleanup complete.")

    def _download_source_image(self, object_name: str, bucket: str) -> Tuple[Path, object]:
        """Downloads the source image from MinIO to our temporary storage."""
        local_path = Path(settings.TEMP_STORAGE_PATH) / Path(object_name).name
        print(f"Fetching metadata for {bucket}/{object_name}...")
        stat = self.minio_client.stat_object(bucket, object_name)
        print(
            f"Source object: size={stat.size} bytes, "
            f"content_type='{stat.content_type}', etag='{stat.etag}'"
        )

        print(f"Downloading {bucket}/{object_name} to {local_path}...")
        self.minio_client.fget_object(bucket, object_name, str(local_path))
        print("Download complete.")
        return local_path, stat

    def _generate_tiles(self, input_image_path: Path, image_id: str) -> Path:
        """Generates DZI tiles using pyvips. (Copied from your script)"""
        print(f"Generating DZI tiles for {input_image_path.name}...")
        image = pyvips.Image.new_from_file(str(input_image_path), access='sequential')
        
        output_path = Path(settings.TEMP_STORAGE_PATH) / image_id
        output_path.mkdir(parents=True, exist_ok=True)
        
        base_path = output_path / "image"
        image.dzsave(str(base_path), suffix=".jpg[Q=85]", overlap=0, tile_size=256)

        print(f"Tiles generated successfully at {output_path}")
        return output_path

    def _upload_tiles(self, tiles_dir: Path, image_id: str) -> Tuple[int, int]:
        """Uploads the generated tile directory to MinIO. (Copied from your script)"""
        bucket = settings.MINIO_UPLOAD_BUCKET
        print(f"Uploading tiles to MinIO bucket '{bucket}'...")

        # Ensure bucket exists
        if not self.minio_client.bucket_exists(bucket):
            self.minio_client.make_bucket(bucket)

        file_count = 0
        total_bytes = 0
        for file_path in tiles_dir.rglob("*"):
            if file_path.is_file():
                relative_path = file_path.relative_to(tiles_dir)
                object_name = f"{image_id}/{relative_path}"
                self.minio_client.fput_object(bucket, object_name, str(file_path))
                file_count += 1
                total_bytes += file_path.stat().st_size
        print(f"Upload complete: {file_count} files uploaded ({total_bytes} bytes).")
        return file_count, total_bytes

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