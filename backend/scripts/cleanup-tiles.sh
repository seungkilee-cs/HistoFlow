#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_ID="${1:-test-image-001}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-localhost:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_SECURE="${MINIO_SECURE:-false}"
MINIO_BUCKET="${MINIO_BUCKET:-histoflow-tiles}"

scheme="http"
case "${MINIO_SECURE}" in
  1|true|TRUE|True|yes|YES|Yes)
    scheme="https"
    ;;
esac

printf '\n==========================================\n'
printf 'HistoFlow MinIO Tile Cleanup\n'
printf '==========================================\n'
printf '\n'
printf 'MinIO endpoint: %s\n' "${MINIO_ENDPOINT}"
printf 'Bucket:        %s\n' "${MINIO_BUCKET}"
printf 'Image ID:      %s\n' "${IMAGE_ID}"
printf '\n'

health_url="${scheme}://${MINIO_ENDPOINT}/minio/health/ready"
if ! curl --silent --fail --connect-timeout 3 "${health_url}" >/dev/null 2>&1; then
  printf 'MinIO not reachable at %s; skipping cleanup.\n' "${health_url}"
  exit 0
fi

IMAGE_ID="${IMAGE_ID}" \
MINIO_ENDPOINT="${MINIO_ENDPOINT}" \
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY}" \
MINIO_SECRET_KEY="${MINIO_SECRET_KEY}" \
MINIO_SECURE="${MINIO_SECURE}" \
MINIO_BUCKET="${MINIO_BUCKET}" \
python - <<'PY'
import os
import traceback
from minio import Minio
from minio.error import S3Error
from minio.deleteobjects import DeleteObject

image_id = os.environ["IMAGE_ID"]
bucket = os.environ["MINIO_BUCKET"]
prefix = f"{image_id}/"

secure = os.environ["MINIO_SECURE"].lower() in {"1", "true", "yes"}
client = Minio(
    os.environ["MINIO_ENDPOINT"],
    access_key=os.environ["MINIO_ACCESS_KEY"],
    secret_key=os.environ["MINIO_SECRET_KEY"],
    secure=secure,
)

try:
    if not client.bucket_exists(bucket):
        print(f"Bucket {bucket} does not exist; nothing to clear")
    else:
        objects = list(client.list_objects(bucket, prefix=prefix, recursive=True))
        if not objects:
            print(f"No existing objects found under {bucket}/{prefix}")
        else:
            errors = list(client.remove_objects(
                bucket,
                (DeleteObject(obj.object_name) for obj in objects)
            ))
            removed = len(objects) - len(errors)
            print(f"Removed {removed} objects from {bucket}/{prefix}")
            if errors:
                print("Errors while deleting:")
                for err in errors:
                    print(f"  - {err}")
except S3Error as exc:
    print(f"Warning: failed to clear MinIO objects ({exc})")
    traceback.print_exc()
PY
