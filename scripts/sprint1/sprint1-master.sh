#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="${ROOT_DIR}/scripts"
BACKEND_SCRIPTS_DIR="${ROOT_DIR}/backend/scripts"
IMAGE_PATH_DEFAULT="${BACKEND_SCRIPTS_DIR}/JPG_Test.jpg"
IMAGE_ID_DEFAULT="test-image-001"

IMAGE_PATH="${1:-$IMAGE_PATH_DEFAULT}"
IMAGE_ID="${2:-$IMAGE_ID_DEFAULT}"

log_section() {
  printf '\n==========================================\n'
  printf '%s\n' "$1"
  printf '==========================================\n\n'
}

check_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

log_section "Sprint 1 • Tile Serving Master Run"

echo "Project root: ${ROOT_DIR}"
echo "Image path:  ${IMAGE_PATH}"
echo "Image ID:    ${IMAGE_ID}"

echo "\nPre-flight checks..."
check_command osascript
check_command minio
check_command npm
check_command java
check_command python3

log_section "Step 1 • Launching Dev Services"
"${SCRIPT_DIR}/dev-start.sh" --auto

echo "\nServices started."

log_section "Step 2 • Regenerating Tiles"
(
  cd "${BACKEND_SCRIPTS_DIR}"
  ./full-regenerate.sh "${IMAGE_PATH}" "${IMAGE_ID}"
)

echo "\nTile regeneration complete."

log_section "Step 3 • API Smoke Test"
IMAGE_ID="${IMAGE_ID}" "${SCRIPT_DIR}/api-smoke-test.sh"

echo "\nAPI smoke test complete."

log_section "Step 4 • Tile Viewer"
printf 'Frontend running at http://localhost:3000/tile-viewer\n'
printf 'Open in your browser to verify zoom/pan.\n'
