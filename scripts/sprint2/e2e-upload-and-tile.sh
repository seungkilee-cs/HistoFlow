#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

MINIO_URL="${MINIO_URL:-http://localhost:9000}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
TILING_URL="${TILING_URL:-http://localhost:8000}"
UPLOAD_SCRIPT="$REPO_ROOT/scripts/sprint2/simulate-upload.sh"
TEMP_DIR="$(mktemp -d -t histoflow-e2e-XXXXXX)"
CLEANUP_TEMP="true"

# Default parameters
SLIDE_FILE=""
CONTENT_TYPE="application/octet-stream"
WAIT_FOR_SERVICES="true"

cleanup() {
  if [[ "$CLEANUP_TEMP" == "true" && -d "$TEMP_DIR" ]]; then
    rm -rf "$TEMP_DIR"
  fi
}
trap cleanup EXIT

show_usage() {
  cat <<EOF
Usage: e2e-upload-and-tile.sh --file <path> [options]

Runs an end-to-end upload + tiling test against local services.

Required arguments:
  --file <path>            Path to the slide file to upload.

Optional arguments:
  --backend-url <url>      Backend base URL (default: ${BACKEND_URL}).
  --minio-url <url>        MinIO base URL (default: ${MINIO_URL}).
  --tiling-url <url>       Tiling service base URL (default: ${TILING_URL}).
  --content-type <type>    MIME type for upload (default: ${CONTENT_TYPE}).
  --no-wait                Skip waiting for services to become healthy.
  --keep-temp              Preserve temp artifacts for inspection.
  --help                   Show this help message.
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: missing required command '$1'" >&2
    exit 1
  fi
}

wait_for_endpoint() {
  local url="$1"
  local name="$2"
  local max_retries=30
  local delay=2

  echo "Waiting for ${name} at ${url}"
  for ((i=1; i<=max_retries; i++)); do
    if curl -sSf "$url" >/dev/null; then
      echo "${name} is ready."
      return 0
    fi
    echo "  retry ${i}/${max_retries}..."
    sleep "$delay"
  done
  echo "Timeout waiting for ${name} at ${url}" >&2
  return 1
}

trigger_tiling_job() {
  local image_id="$1"
  local object_name="$2"

  local payload
  payload=$(jq -n \
    --arg imageId "$image_id" \
    --arg bucket "unprocessed-slides" \
    --arg objectName "$object_name" \
    '{image_id: $imageId, source_bucket: $bucket, source_object_name: $objectName}')

  echo "Triggering tiling job for image_id=${image_id}"
  curl -sSf -X POST "${TILING_URL}/jobs/tile-image" \
    -H "Content-Type: application/json" \
    -d "$payload"
  echo
}

verify_tile_availability() {
  local image_id="$1"

  echo "Verifying tiles are accessible via backend"
  curl -sSf "${BACKEND_URL}/api/v1/tiles/${image_id}/image.dzi" -o "$TEMP_DIR/image.dzi"
  echo "  downloaded image.dzi"

  curl -sSf "${BACKEND_URL}/api/v1/tiles/${image_id}/image_files/0/0_0.jpg" \
    -o "$TEMP_DIR/level0_0_0.jpg"
  echo "  downloaded sample tile -> $TEMP_DIR/level0_0_0.jpg"
}

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)
      shift || { show_usage >&2; exit 1; }
      SLIDE_FILE="$1"
      ;;
    --backend-url)
      shift || exit 1
      BACKEND_URL="$1"
      ;;
    --minio-url)
      shift || exit 1
      MINIO_URL="$1"
      ;;
    --tiling-url)
      shift || exit 1
      TILING_URL="$1"
      ;;
    --content-type)
      shift || exit 1
      CONTENT_TYPE="$1"
      ;;
    --no-wait)
      WAIT_FOR_SERVICES="false"
      ;;
    --keep-temp)
      CLEANUP_TEMP="false"
      ;;
    --help|-h)
      show_usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      show_usage >&2
      exit 1
      ;;
  esac
  shift || break
done

[[ -n "$SLIDE_FILE" ]] || { show_usage >&2; exit 1; }
[[ -f "$SLIDE_FILE" ]] || { echo "File not found: $SLIDE_FILE" >&2; exit 1; }

require_cmd curl
require_cmd jq
require_cmd sed
require_cmd awk
require_cmd bash

if [[ ! -x "$UPLOAD_SCRIPT" ]]; then
  echo "Error: upload script not found or not executable: $UPLOAD_SCRIPT" >&2
  exit 1
fi

if [[ "$WAIT_FOR_SERVICES" == "true" ]]; then
  wait_for_endpoint "${MINIO_URL}/minio/health/live" "MinIO"
  wait_for_endpoint "${BACKEND_URL}/actuator/health" "Backend"
  wait_for_endpoint "${TILING_URL}/health" "Tiling service"
fi

UPLOAD_LOG="$TEMP_DIR/upload.log"

echo "Running upload simulation"
"$UPLOAD_SCRIPT" \
  --file "$SLIDE_FILE" \
  --backend-url "$BACKEND_URL" \
  --content-type "$CONTENT_TYPE" \
  | tee "$UPLOAD_LOG"

IMAGE_ID=$(awk -F': ' '/^Image ID:/ {print $2}' "$UPLOAD_LOG")
OBJECT_NAME=$(awk -F': ' '/^Object name:/ {print $2}' "$UPLOAD_LOG")

[[ -n "$IMAGE_ID" ]] || { echo "Failed to extract image ID" >&2; exit 1; }
[[ -n "$OBJECT_NAME" ]] || { echo "Failed to extract object name" >&2; exit 1; }

echo "Image ID resolved to ${IMAGE_ID}"

echo "Triggering tiling job"
trigger_tiling_job "$IMAGE_ID" "$OBJECT_NAME"

echo "Waiting for tiles to appear..."
# Poll backend for DZI availability
MAX_ATTEMPTS=30
SLEEP_SECONDS=5
for ((i=1; i<=MAX_ATTEMPTS; i++)); do
  if curl -sSf "${BACKEND_URL}/api/v1/tiles/${IMAGE_ID}/image.dzi" >/dev/null 2>&1; then
    echo "Tiles are ready."
    break
  fi
  echo "  attempt ${i}/${MAX_ATTEMPTS}: tiles not yet available"
  sleep "$SLEEP_SECONDS"
  if [[ $i -eq MAX_ATTEMPTS ]]; then
    echo "Timed out waiting for tiles" >&2
    exit 1
  fi
done

verify_tile_availability "$IMAGE_ID"

echo "End-to-end test completed successfully."
