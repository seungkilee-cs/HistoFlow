#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
BASE_COMPOSE="$REPO_ROOT/docker/docker-compose.base.yml"
DEV_COMPOSE="$REPO_ROOT/docker/docker-compose.dev.yml"
RUNTIME_PORTS_LIB="$REPO_ROOT/scripts/lib/runtime-ports.sh"

if [[ ! -f "$BASE_COMPOSE" ]]; then
  echo "Base compose file not found at $BASE_COMPOSE" >&2
  exit 1
fi

if [[ ! -f "$DEV_COMPOSE" ]]; then
  echo "Dev compose file not found at $DEV_COMPOSE" >&2
  exit 1
fi

COMPOSE_PROJECT_NAME="histoflow-sprint2"
export COMPOSE_PROJECT_NAME

echo "Starting HistoFlow sprint2 stack"
# shellcheck disable=SC1090
source "$RUNTIME_PORTS_LIB"
histoflow_resolve_runtime_ports "$REPO_ROOT"

DOCKER_COMPOSE="docker compose --env-file $HISTOFLOW_RUNTIME_ENV_FILE -f $BASE_COMPOSE -f $DEV_COMPOSE"

$DOCKER_COMPOSE --profile dev up -d --build

echo "Stack started. Services: minio, backend, tiling"
echo "Backend: $BACKEND_PUBLIC_URL"
echo "Tiling: $TILING_PUBLIC_URL"
