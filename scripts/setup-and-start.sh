#!/usr/bin/env bash

# HistoFlow Unified Setup and Start Script
# This script sets up the dev environment, validates region-detector model wiring,
# starts the Docker stack, optionally runs region-detector tests, and launches frontend.

set -euo pipefail

# ── 1. Configuration ──────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/docker"
MODEL_PATH="$PROJECT_ROOT/services/justin-regression/models/dinov2_classifier.pkl"
TILING_ENV="$PROJECT_ROOT/services/tiling/.env"
TILING_ENV_EXAMPLE="$PROJECT_ROOT/services/tiling/.env.example"
REGION_TEST_PYTEST="$PROJECT_ROOT/services/region-detector/venv/bin/pytest"

echo "🚀 Starting HistoFlow Setup..."

# ── 2. Preflight checks ───────────────────────────────────────────────────────
if [ ! -f "$TILING_ENV" ]; then
  if [ -f "$TILING_ENV_EXAMPLE" ]; then
    echo "🧩 services/tiling/.env not found; bootstrapping from .env.example"
    cp "$TILING_ENV_EXAMPLE" "$TILING_ENV"
  else
    echo "❌ Missing $TILING_ENV and $TILING_ENV_EXAMPLE; cannot start tiling service."
    exit 1
  fi
fi

if [ ! -f "$MODEL_PATH" ]; then
  echo "❌ region-detector model missing at:"
  echo "   $MODEL_PATH"
  echo "Expected model file: dinov2_classifier.pkl"
  echo "Provide this file before continuing."
  exit 1
fi
echo "✅ region-detector model found: $MODEL_PATH"

# ── 2. Docker Stack ───────────────────────────────────────────────────────────
echo "🏗️  Starting Docker containers (MinIO, PostgreSQL, Tiling, Region-Detector)..."
cd "$DOCKER_DIR"
docker compose -f docker-compose.base.yml -f docker-compose.dev.yml -f docker-compose.ml.yml --profile cpu --profile dev up -d

# ── 3. Wait for Services ──────────────────────────────────────────────────────
wait_for_health() {
  local service_name="$1"
  local url="$2"
  local expected="${3:-}"
  local timeout_seconds="${4:-180}"
  local waited=0

  echo "⏳ Waiting for ${service_name} (${url})..."
  while true; do
    if [ -z "$expected" ]; then
      if curl -fsS "$url" >/dev/null; then
        break
      fi
    else
      if curl -fsS "$url" | grep -q "$expected"; then
        break
      fi
    fi

    sleep 2
    waited=$((waited + 2))
    if [ "$waited" -ge "$timeout_seconds" ]; then
      echo "❌ ${service_name} failed health check within ${timeout_seconds}s"
      exit 1
    fi
  done
  echo "✅ ${service_name} is healthy."
}

echo "🔎 Health runbook order: backend -> tiling -> region-detector -> minio"
wait_for_health "Backend" "http://localhost:8080/api/v1/health" "\"status\":\"UP\""
wait_for_health "Tiling" "http://localhost:8000/health" "\"status\":\"ok\""
wait_for_health "Region-Detector" "http://localhost:8001/health" "\"status\":\"ok\""
wait_for_health "MinIO" "http://localhost:9000/minio/health/ready"

# ── 4. Optional region-detector tests ────────────────────────────────────────
if [ "${RUN_REGION_TESTS:-1}" = "1" ]; then
  if [ -x "$REGION_TEST_PYTEST" ]; then
    echo "🧪 Running region-detector unit tests..."
    (
      cd "$PROJECT_ROOT/services/region-detector"
      "$REGION_TEST_PYTEST" tests -q
    )
    echo "✅ region-detector tests passed."
  else
    echo "⚠️  Skipping region-detector tests (pytest not found at $REGION_TEST_PYTEST)."
    echo "    Create venv and install dependencies in services/region-detector to enable this."
  fi
else
  echo "ℹ️  Skipping region-detector tests (RUN_REGION_TESTS=${RUN_REGION_TESTS:-0})."
fi

# ── 5. Setup Frontend ─────────────────────────────────────────────────────────
echo "🛠️  Setting up Frontend..."
cd "$PROJECT_ROOT/frontend"
if [ ! -d "node_modules" ]; then
    echo "📦 Installing frontend dependencies..."
    npm install
fi

# ── 6. Start Frontend ─────────────────────────────────────────────────────────
if [ "${START_FRONTEND:-1}" != "1" ]; then
  echo "ℹ️  START_FRONTEND=${START_FRONTEND}; skipping frontend dev server startup."
  exit 0
fi

echo "🌐 Starting Frontend dev server..."
echo "--------------------------------------------------------"
echo "HistoFlow is being served at: http://localhost:5173"
echo "Backend API (proxied): http://localhost:8080"
echo "MinIO Console: http://localhost:9001"
echo "--------------------------------------------------------"

npm run dev
