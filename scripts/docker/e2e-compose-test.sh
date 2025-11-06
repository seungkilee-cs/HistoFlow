#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
BASE_COMPOSE="$REPO_ROOT/docker/docker-compose.base.yml"
DEV_COMPOSE="$REPO_ROOT/docker/docker-compose.dev.yml"
E2E_SCRIPT="$REPO_ROOT/scripts/sprint2/e2e-upload-and-tile.sh"

if [[ ! -f "$BASE_COMPOSE" ]]; then
  echo "Base compose file not found at $BASE_COMPOSE" >&2
  exit 1
fi

if [[ ! -f "$DEV_COMPOSE" ]]; then
  echo "Dev compose file not found at $DEV_COMPOSE" >&2
  exit 1
fi

if [[ ! -x "$E2E_SCRIPT" ]]; then
  echo "E2E script not executable at $E2E_SCRIPT" >&2
  exit 1
fi

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <slide-file> [additional e2e args...]" >&2
  exit 1
fi

SLIDE_FILE="$1"
shift

COMPOSE_PROJECT_NAME="histoflow-sprint2"
export COMPOSE_PROJECT_NAME

DOCKER_COMPOSE="docker compose -f $BASE_COMPOSE -f $DEV_COMPOSE"

cleanup() {
  $DOCKER_COMPOSE --profile dev down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Bringing up stack"
$DOCKER_COMPOSE --profile dev up -d --build

echo "Running end-to-end script against compose network"
$E2E_SCRIPT --file "$SLIDE_FILE" --backend-url http://localhost:8080 --tiling-url http://localhost:8000 "$@"

echo "End-to-end test completed."
