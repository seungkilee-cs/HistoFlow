#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SIM_SCRIPT="$SCRIPT_DIR/simulate-upload.sh"

show_usage() {
  cat <<'EOF'
Usage: sprint03/e2e-upload-and-tile.sh --file <path> [options]

Runs the full Sprint 3 end-to-end workflow:
  1. Start (or verify) Docker stack outside this script.
  2. Simulate frontend upload using sprint03/simulate-upload.sh.
  3. After tiling completes, optionally fetch the DZI descriptor and a sample tile.

Required arguments:
  --file <path>            Slide file to upload via the simulation script.

Optional arguments:
  --backend-url <url>      Backend base URL (default: http://localhost:8080).
  --content-type <type>    MIME type for upload (default: application/octet-stream).
  --dataset-name <name>    Dataset name metadata sent to backend.
  --poll-interval <sec>    Seconds between polling attempts (default: 5).
  --max-attempts <n>       Maximum polling attempts (default: 60).
  --output-dir <path>      Directory to store fetched DZI/tile (default: ./tmp/e2e-artifacts).
  --level <n>              Tile level to download for verification (default: 0).
  --coord <x_y>            Tile coordinate to download (default: 0_0).
  --skip-artifacts         Do not fetch DZI/tile after completion.
  --help                   Show this message and exit.

Examples:
  ./scripts/sprint03/e2e-upload-and-tile.sh --file backend/scripts/CMU-1.tiff \
      --dataset-name demo --poll-interval 3 --max-attempts 40
EOF
}

abort() {
  echo "Error: $1" >&2
  exit 1
}

[[ -x "$SIM_SCRIPT" ]] || abort "Simulation script not found or not executable at $SIM_SCRIPT"

BACKEND_URL="http://localhost:8080"
FILE_PATH=""
CONTENT_TYPE="application/octet-stream"
DATASET_NAME=""
POLL_INTERVAL=5
MAX_ATTEMPTS=60
OUTPUT_DIR="$REPO_ROOT/tmp/e2e-artifacts"
TILE_LEVEL=0
TILE_COORD="0_0"
FETCH_ARTIFACTS=1

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
    --output-dir)
      shift || abort "Missing value for --output-dir"
      OUTPUT_DIR="$1"
      ;;
    --level)
      shift || abort "Missing value for --level"
      TILE_LEVEL="$1"
      ;;
    --coord)
      shift || abort "Missing value for --coord"
      TILE_COORD="$1"
      ;;
    --skip-artifacts)
      FETCH_ARTIFACTS=0
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

SIM_ARGS=(
  "--file" "$FILE_PATH"
  "--backend-url" "$BACKEND_URL"
  "--content-type" "$CONTENT_TYPE"
  "--poll-interval" "$POLL_INTERVAL"
  "--max-attempts" "$MAX_ATTEMPTS"
)
[[ -n "$DATASET_NAME" ]] && SIM_ARGS+=("--dataset-name" "$DATASET_NAME")

echo "Running upload simulation: $SIM_SCRIPT ${SIM_ARGS[*]}"
SIM_OUTPUT="$($SIM_SCRIPT "${SIM_ARGS[@]}")"
SIM_EXIT=$?

echo "$SIM_OUTPUT"
[[ $SIM_EXIT -eq 0 ]] || abort "Simulation script exited with status $SIM_EXIT"

IMAGE_ID=$(echo "$SIM_OUTPUT" | awk -F ': ' '/^Image ID:/ {print $2}' | tail -n 1)
OBJECT_NAME=$(echo "$SIM_OUTPUT" | awk -F ': ' '/^Object name:/ {print $2}' | tail -n 1)

[[ -n "$IMAGE_ID" ]] || abort "Unable to parse image ID from simulation output"
[[ -n "$OBJECT_NAME" ]] || abort "Unable to parse object name from simulation output"

echo "Simulation completed. Parsed imageId=$IMAGE_ID, objectName=$OBJECT_NAME"

if [[ $FETCH_ARTIFACTS -eq 1 ]]; then
  mkdir -p "$OUTPUT_DIR"
  DZI_PATH="$OUTPUT_DIR/${IMAGE_ID}.dzi"
  TILE_PATH="$OUTPUT_DIR/${IMAGE_ID}_level${TILE_LEVEL}_${TILE_COORD}.jpg"

  echo "Fetching DZI descriptor to $DZI_PATH"
  curl -sS -f "$BACKEND_URL/api/v1/tiles/$IMAGE_ID/image.dzi" -o "$DZI_PATH"
  echo "Saved DZI descriptor $DZI_PATH"

  echo "Fetching sample tile (level=$TILE_LEVEL coord=$TILE_COORD) to $TILE_PATH"
  curl -sS -f "$BACKEND_URL/api/v1/tiles/$IMAGE_ID/image_files/$TILE_LEVEL/$TILE_COORD.jpg" -o "$TILE_PATH"
  echo "Saved tile $TILE_PATH"
else
  echo "Skipping artifact download as requested."
fi

echo "E2E workflow completed successfully."
