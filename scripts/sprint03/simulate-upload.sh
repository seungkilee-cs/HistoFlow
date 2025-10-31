#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

show_usage() {
  cat <<'EOF'
Usage: sprint03/simulate-upload.sh --file <path> [options]

Simulates the Sprint 3 upload flow:
  1. Requests a pre-signed upload URL from the Kotlin backend.
  2. Uploads the file directly to MinIO.
  3. Notifies the backend that the upload completed (triggers tiling).
  4. Polls the backend for tiling status until completion or failure.

Required arguments:
  --file <path>            Path to the slide file to upload.

Optional arguments:
  --backend-url <url>      Backend base URL (default: http://localhost:8080).
  --file-name <name>       Override filename sent to backend (default: source filename).
  --content-type <type>    MIME type for upload (default: application/octet-stream).
  --dataset-name <name>    Friendly dataset name to send with the upload request.
  --poll-interval <sec>    Seconds between polling attempts (default: 5).
  --max-attempts <n>       Max number of polling attempts (default: 60).
  --help                   Show this message and exit.

Examples:
  ./scripts/sprint03/simulate-upload.sh --file backend/scripts/CMU-1.tiff
  ./scripts/sprint03/simulate-upload.sh --file data/sample.tif --dataset-name demo
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
DATASET_NAME=""
POLL_INTERVAL=5
MAX_ATTEMPTS=60

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
    --dataset-name)
      shift || abort "Missing value for --dataset-name"
      DATASET_NAME="$1"
      ;;
    --poll-interval)
      shift || abort "Missing value for --poll-interval"
      POLL_INTERVAL="$1"
      ;;
    --max-attempts)
      shift || abort "Missing value for --max-attempts"
      MAX_ATTEMPTS="$1"
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
  --arg datasetName "$DATASET_NAME" \
  '{fileName: $fileName, contentType: $contentType} + (if ($datasetName | length) > 0 then {datasetName: $datasetName} else {} end)')

echo "Requesting pre-signed URL from $INIT_ENDPOINT"
echo "Initiate payload: $REQUEST_BODY"
RESPONSE_JSON=$(curl -sS -f -X POST "$INIT_ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "$REQUEST_BODY")

echo "Backend response: $RESPONSE_JSON"

UPLOAD_URL=$(echo "$RESPONSE_JSON" | jq -r '.uploadUrl')
OBJECT_NAME=$(echo "$RESPONSE_JSON" | jq -r '.objectName')
IMAGE_ID=$(echo "$RESPONSE_JSON" | jq -r '.imageId // empty')
DATASET_NAME_RESPONSE=$(echo "$RESPONSE_JSON" | jq -r '.datasetName // empty')

[[ "$UPLOAD_URL" != "null" ]] || abort "Backend response missing uploadUrl"
[[ "$OBJECT_NAME" != "null" ]] || abort "Backend response missing objectName"
[[ -n "$IMAGE_ID" ]] || IMAGE_ID="${OBJECT_NAME%%/*}"

UPLOAD_ENDPOINT_INFO="Uploading '$FILE_PATH' to MinIO via pre-signed URL"
echo "$UPLOAD_ENDPOINT_INFO"

echo "-- Uploading with curl"
curl -f -X PUT "$UPLOAD_URL" \
  -H "Content-Type: $CONTENT_TYPE" \
  --upload-file "$FILE_PATH"

echo "Upload successful."
echo "Image ID: $IMAGE_ID"
if [[ -n "$DATASET_NAME_RESPONSE" ]]; then
  echo "Dataset name: $DATASET_NAME_RESPONSE"
fi
echo "Object name: $OBJECT_NAME"

COMPLETE_ENDPOINT="$BACKEND_URL/api/v1/uploads/complete"
COMPLETE_BODY=$(jq -n \
  --arg objectName "$OBJECT_NAME" \
  --arg imageId "$IMAGE_ID" \
  --arg datasetName "${DATASET_NAME_RESPONSE:-$DATASET_NAME}" \
  '{objectName: $objectName, imageId: $imageId} + (if ($datasetName | length) > 0 then {datasetName: $datasetName} else {} end)')

echo "Notifying backend upload completion at $COMPLETE_ENDPOINT"
echo "Completion payload: $COMPLETE_BODY"
COMPLETE_RESPONSE=$(curl -sS -f -X POST "$COMPLETE_ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "$COMPLETE_BODY")

echo "Backend completion response: $COMPLETE_RESPONSE"

STATUS_ENDPOINT="$BACKEND_URL/api/v1/tiles/$IMAGE_ID/status"
echo "Polling tiling status at $STATUS_ENDPOINT (interval=${POLL_INTERVAL}s, max=${MAX_ATTEMPTS} attempts)"

attempt=0
while (( attempt < MAX_ATTEMPTS )); do
  attempt=$((attempt + 1))
  echo "Polling attempt $attempt"

  STATUS_RESPONSE=$(curl -sS -f "$STATUS_ENDPOINT" || true)
  if [[ -z "$STATUS_RESPONSE" ]]; then
    echo "No status body returned (attempt $attempt)."
  else
    STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status // empty')
    MESSAGE=$(echo "$STATUS_RESPONSE" | jq -r '.message // empty')
    echo "Status response: $STATUS_RESPONSE"

    case "$STATUS" in
      completed)
        echo "Tiling completed successfully."
        [[ -n "$MESSAGE" ]] && echo "Message: $MESSAGE"
        exit 0
        ;;
      processing)
        echo "Tiling still in progress."
        [[ -n "$MESSAGE" ]] && echo "Message: $MESSAGE"
        ;;
      not_found)
        echo "Tiling status not found."
        [[ -n "$MESSAGE" ]] && echo "Message: $MESSAGE"
        echo "Exiting with failure."
        exit 2
        ;;
      *)
        echo "Unexpected status: ${STATUS:-<none>}"
        [[ -n "$MESSAGE" ]] && echo "Message: $MESSAGE"
        ;;
    esac
  fi

  echo "Sleeping for $POLL_INTERVAL seconds before next attempt..."
  sleep "$POLL_INTERVAL"
done

echo "Exceeded maximum polling attempts without completion."
exit 3
