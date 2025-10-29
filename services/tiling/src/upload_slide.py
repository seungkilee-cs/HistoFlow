import argparse
from pathlib import Path
from minio import Minio
from .config import settings

def create_minio_client() -> Minio:
    return Minio(
        settings.MINIO_ENDPOINT,
        access_key=settings.MINIO_ACCESS_KEY,
        secret_key=settings.MINIO_SECRET_KEY,
        secure=settings.MINIO_SECURE,
    )

def ensure_bucket(client: Minio, bucket: str) -> None:
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)

def build_object_name(prefix: str | None, image_id: str, file_name: str, override: str | None) -> str:
    if override:
        return override
    parts = []
    if prefix:
        parts.append(prefix.strip("/"))
    parts.append(image_id.strip("/"))
    parts.append(file_name)
    return "/".join(part for part in parts if part)

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", dest="file_path", required=True)
    parser.add_argument("--image-id", dest="image_id", required=True)
    parser.add_argument("--bucket", dest="bucket", default="histoflow-unprocessed")
    parser.add_argument("--prefix", dest="prefix", default="unprocessed")
    parser.add_argument("--object-name", dest="object_name")
    args = parser.parse_args()

    file_path = Path(args.file_path).expanduser().resolve()
    if not file_path.exists() or not file_path.is_file():
        parser.error(f"File not found: {file_path}")

    client = create_minio_client()
    ensure_bucket(client, args.bucket)

    object_name = build_object_name(args.prefix, args.image_id, file_path.name, args.object_name)

    client.fput_object(args.bucket, object_name, str(file_path))

    print("Upload complete")
    print(f"  Bucket: {args.bucket}")
    print(f"  Object: {object_name}")

if __name__ == "__main__":
    main()
