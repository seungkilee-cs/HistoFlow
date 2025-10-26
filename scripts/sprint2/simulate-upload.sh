#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

show_usage() {
  cat <<'EOF'
Usage: simulate-upload.sh --file <path> [options]

Simulates the Sprint 2 frontend upload flow:
  1. Requests a pre-signed upload URL from the Kotlin backend.
  2. Uploads the provided file directly to MinIO.

Required arguments:
  --file <path>            Path to the slide file to upload.

Optional arguments:
  --backend-url <url>      Backend base URL (default: http://localhost:8080).
  --file-name <name>       Override filename sent to backend (default: source filename).
  --content-type <type>    MIME type for upload (default: application/octet-stream).
  --help                   Show this message and exit.

Examples:
  ./scripts/sprint2/simulate-upload.sh --file ~/slides/sample.svs
  ./scripts/sprint2/simulate-upload.sh --file data/test.tiff --content-type image/tiff
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command '$1' not found in PATH" >&2
    exit 1
  fi
}

abort() {
  echo "Error: $1" >&2
  exit 1
}

BACKEND_URL="http://localhost:8080"
FILE_PATH=""
FILE_NAME_OVERRIDE=""
CONTENT_TYPE="application/octet-stream"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)
      shift || abort "Missing value for --file"
      FILE_PATH="$1"
      ;;
    --backend-url)
      shift || abort "Missing value for --backend-url"
      BACKEND_URL="$1"
      ;;
    --file-name)
      shift || abort "Missing value for --file-name"
      FILE_NAME_OVERRIDE="$1"
      ;;
    --content-type)
      shift || abort "Missing value for --content-type"
      CONTENT_TYPE="$1"
      ;;
    --help|-h)
      show_usage
      exit 0
      ;;
    *)
      abort "Unknown argument: $1"
      ;;
  esac
  shift || break
done

[[ -z "$FILE_PATH" ]] && { show_usage >&2; abort "--file is required"; }
[[ -f "$FILE_PATH" ]] || abort "File not found: $FILE_PATH"

require_cmd curl
require_cmd jq

FILE_NAME="${FILE_NAME_OVERRIDE:-$(basename "$FILE_PATH")}" || abort "Unable to determine filename"

INIT_ENDPOINT="$BACKEND_URL/api/v1/uploads/initiate"
REQUEST_BODY=$(jq -n \
  --arg fileName "$FILE_NAME" \
  --arg contentType "$CONTENT_TYPE" \
  '{fileName: $fileName, contentType: $contentType}')

echo "Requesting pre-signed URL from $INIT_ENDPOINT"
RESPONSE_JSON=$(curl -sS -f -X POST "$INIT_ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "$REQUEST_BODY")

echo "Backend response: $RESPONSE_JSON"

UPLOAD_URL=$(echo "$RESPONSE_JSON" | jq -r '.uploadUrl')
OBJECT_NAME=$(echo "$RESPONSE_JSON" | jq -r '.objectName')

[[ "$UPLOAD_URL" != "null" ]] || abort "Backend response missing uploadUrl"
[[ "$OBJECT_NAME" != "null" ]] || abort "Backend response missing objectName"

IMAGE_ID="${OBJECT_NAME%%/*}"

echo "Uploading '$FILE_PATH' to MinIO via pre-signed URL"

curl -f -X PUT "$UPLOAD_URL" \
  -H "Content-Type: $CONTENT_TYPE" \
  --upload-file "$FILE_PATH"

echo "Upload successful."
echo "Image ID: $IMAGE_ID"
echo "Object name: $OBJECT_NAME"
echo "Next step: trigger tiling service with image_id='$IMAGE_ID', source_bucket='unprocessed-slides', source_object_name='$OBJECT_NAME'"
