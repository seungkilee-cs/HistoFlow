import os
import shutil
from pathlib import Path
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

    def process_image(self, image_id: str, source_object_name: str, source_bucket: str):
        """
        The main orchestrator method for processing one image.
        This will download, tile, upload, and cleanup.
        """
        print(f"Starting processing for image_id='{image_id}'")
        local_image_path = None
        local_tiles_dir = None

        try:
            # 1. Download the source file from MinIO
            local_image_path = self._download_source_image(source_object_name, source_bucket)

            # 2. Generate DZI tiles locally (logic from your script)
            local_tiles_dir = self._generate_tiles(local_image_path, image_id)

            # 3. Upload the generated tiles to MinIO (logic from your script)
            self._upload_tiles(local_tiles_dir, image_id)

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

    def _download_source_image(self, object_name: str, bucket: str) -> Path:
        """Downloads the source image from MinIO to our temporary storage."""
        local_path = Path(settings.TEMP_STORAGE_PATH) / Path(object_name).name
        print(f"Downloading {bucket}/{object_name} to {local_path}...")
        self.minio_client.fget_object(bucket, object_name, str(local_path))
        print("Download complete.")
        return local_path

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

    def _upload_tiles(self, tiles_dir: Path, image_id: str):
        """Uploads the generated tile directory to MinIO. (Copied from your script)"""
        bucket = settings.MINIO_UPLOAD_BUCKET
        print(f"Uploading tiles to MinIO bucket '{bucket}'...")
        
        # Ensure bucket exists
        if not self.minio_client.bucket_exists(bucket):
            self.minio_client.make_bucket(bucket)

        file_count = 0
        for file_path in tiles_dir.rglob("*"):
            if file_path.is_file():
                relative_path = file_path.relative_to(tiles_dir)
                object_name = f"{image_id}/{relative_path}"
                self.minio_client.fput_object(bucket, object_name, str(file_path))
                file_count += 1
        print(f"Upload complete: {file_count} files uploaded.")