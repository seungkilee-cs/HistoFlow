#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IMAGE_PATH=${1:-"${SCRIPT_DIR}/JPG_Test.jpg"}
IMAGE_ID=${2:-"test-image-001"}
MINIO_ENDPOINT=${MINIO_ENDPOINT:-"localhost:9000"}
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY:-"minioadmin"}
MINIO_SECRET_KEY=${MINIO_SECRET_KEY:-"minioadmin"}
MINIO_SECURE=${MINIO_SECURE:-"false"}
MINIO_BUCKET=${MINIO_BUCKET:-"histoflow-tiles"}

printf '\n==========================================\n'
printf 'HistoFlow Full Tile Regeneration\n'
printf '==========================================\n'
printf '\n'
printf 'Project root: %s\n' "${PROJECT_ROOT}"
printf 'Image path:  %s\n' "${IMAGE_PATH}"
printf 'Image ID:    %s\n' "${IMAGE_ID}"
printf '\n'

cd "${SCRIPT_DIR}"

if [ -d venv ]; then
  printf 'Removing existing virtualenv...\n'
  rm -rf venv
fi

printf 'Running setup.sh (creates venv + installs deps)...\n'
./setup.sh

printf '\nSourcing dev.sh to activate venv...\n'
# shellcheck disable=SC1091
source ./dev.sh

printf '\nClearing MinIO tiles under bucket %s/%s (if possible)...\n' "${MINIO_BUCKET}" "${IMAGE_ID}"

if curl --silent --fail "http://${MINIO_ENDPOINT}/minio/health/ready" >/dev/null 2>&1; then
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
else
  printf 'MinIO not reachable on %s; skipping cleanup.\n' "${MINIO_ENDPOINT}"
fi

printf '\nGenerating tiles...\n'
python generate_test_tiles.py "${IMAGE_PATH}" "${IMAGE_ID}"

printf '\nFull regeneration complete.\n'
printf '==========================================\n'
