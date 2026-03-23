#!/usr/bin/env bash
# dev.sh — spin up the full HistoFlow stack for manual frontend testing
#
# Usage:
#   bash dev.sh           # base stack  (upload + tiling + viewing)
#   bash dev.sh --ml      # full stack  (+ region-detector analysis service)
#   bash dev.sh --down    # stop everything

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$REPO_ROOT/docker"
FRONTEND_DIR="$REPO_ROOT/frontend"
TILING_ENV="$REPO_ROOT/services/tiling/.env"
TILING_ENV_EXAMPLE="$REPO_ROOT/services/tiling/.env.example"

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; DIM='\033[2m'; NC='\033[0m'

log()  { echo -e "${BOLD}${BLUE}▶${NC}  $*"; }
ok()   { echo -e "   ${GREEN}✓${NC}  $*"; }
warn() { echo -e "   ${YELLOW}⚠${NC}  $*"; }
err()  { echo -e "   ${RED}✗${NC}  $*" >&2; }
die()  { err "$*"; exit 1; }
sep()  { echo -e "${DIM}   ────────────────────────────────────${NC}"; }

# ── Flags ─────────────────────────────────────────────────────────────────────
WITH_ML=false
TEARDOWN=false
for arg in "$@"; do
  case $arg in
    --ml)       WITH_ML=true ;;
    --down)     TEARDOWN=true ;;
    --help|-h)
      echo "Usage: bash dev.sh [--ml] [--down]"
      echo ""
      echo "  (no flags)   Start base stack: postgres, minio, backend, tiling, frontend"
      echo "  --ml         Also start region-detector (AI analysis). First run is slow"
      echo "               (~3 min) as DINOv2 loads into memory."
      echo "  --down       Stop and remove all Docker services."
      exit 0
      ;;
    *) die "Unknown flag: $arg  (try --help)" ;;
  esac
done

# ── Banner ────────────────────────────────────────────────────────────────────
echo ""
echo -e "  ${BOLD}HistoFlow${NC} dev stack"
sep
if [ "$WITH_ML" = true ]; then
  echo -e "  Mode  ${GREEN}full (upload · tile · view · AI analysis)${NC}"
else
  echo -e "  Mode  ${YELLOW}base (upload · tile · view)${NC}  — pass ${BOLD}--ml${NC} for analysis"
fi
echo ""

# ── Teardown ──────────────────────────────────────────────────────────────────
if [ "$TEARDOWN" = true ]; then
  log "Stopping all services..."
  cd "$DOCKER_DIR"
  docker compose \
    -f docker-compose.base.yml \
    -f docker-compose.dev.yml \
    -f docker-compose.ml.yml \
    --profile dev --profile cpu \
    down 2>/dev/null || true
  ok "All Docker services stopped."
  exit 0
fi

# ── Prerequisites ─────────────────────────────────────────────────────────────
log "Checking prerequisites"

command -v docker  &>/dev/null || die "docker not found — install Docker Desktop."
docker compose version &>/dev/null 2>&1 || die "Docker Compose v2 not found."
command -v npm     &>/dev/null || die "npm not found — install Node.js."
command -v curl    &>/dev/null || die "curl not found."

ok "docker, npm, curl all present"

# ── Tiling service .env ───────────────────────────────────────────────────────
if [ ! -f "$TILING_ENV" ]; then
  log "Creating tiling service .env"
  cp "$TILING_ENV_EXAMPLE" "$TILING_ENV"
  ok "Created services/tiling/.env from example"
fi

# ── Frontend dependencies ─────────────────────────────────────────────────────
if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
  log "Installing frontend dependencies (first time only)..."
  cd "$FRONTEND_DIR"
  npm install --silent
  ok "npm install complete"
fi

# ── Docker services ───────────────────────────────────────────────────────────
log "Starting Docker services (building images if needed)..."
cd "$DOCKER_DIR"

COMPOSE_FILES=(
  -f docker-compose.base.yml
  -f docker-compose.dev.yml
)
PROFILES=(--profile dev)

if [ "$WITH_ML" = true ]; then
  COMPOSE_FILES+=(-f docker-compose.ml.yml)
  PROFILES+=(--profile cpu)
fi

docker compose "${COMPOSE_FILES[@]}" "${PROFILES[@]}" up -d --build 2>&1 \
  | grep -E "built|pulled|started|healthy|error|Error" \
  || true

ok "Docker services started"

# ── Health checks ─────────────────────────────────────────────────────────────
wait_http() {
  # wait_http <label> <url> <timeout_seconds>
  local label="$1" url="$2" timeout="${3:-60}"
  local deadline=$(( SECONDS + timeout ))
  printf "   Waiting for %-22s" "$label"
  while [ $SECONDS -lt $deadline ]; do
    if curl -sf --max-time 2 "$url" -o /dev/null 2>/dev/null; then
      echo -e "  ${GREEN}ready${NC}"
      return 0
    fi
    printf "."
    sleep 3
  done
  echo -e "  ${RED}timed out${NC}"
  return 1
}

wait_port() {
  # wait_port <label> <host> <port> <timeout_seconds>
  local label="$1" host="$2" port="$3" timeout="${4:-60}"
  local deadline=$(( SECONDS + timeout ))
  printf "   Waiting for %-22s" "$label"
  while [ $SECONDS -lt $deadline ]; do
    if nc -z "$host" "$port" 2>/dev/null; then
      echo -e "  ${GREEN}ready${NC}"
      return 0
    fi
    printf "."
    sleep 3
  done
  echo -e "  ${RED}timed out${NC}"
  return 1
}

echo ""
log "Waiting for services"

wait_port "PostgreSQL :5432"    localhost 5432  40
wait_http  "MinIO :9000"        "http://localhost:9000/minio/health/live"  40
wait_port  "Backend :8080"      localhost 8080  60
wait_http  "Tiling svc :8000"   "http://localhost:8000/health"             40

if [ "$WITH_ML" = true ]; then
  echo ""
  warn "Region-detector is loading DINOv2 — this takes ~2-3 min on first start."
  wait_http "Region detector :8001" "http://localhost:8001/health" 240
fi

# ── MinIO bucket check ────────────────────────────────────────────────────────
echo ""
log "Verifying MinIO buckets"
# Both buckets are auto-created on first use by the tiling service and backend,
# but we can pre-create them to avoid any race on first upload.
if curl -sf "http://localhost:9000/minio/health/live" -o /dev/null 2>/dev/null; then
  for bucket in histoflow-tiles unprocessed-slides; do
    # S3 HEAD bucket returns 200 if exists, 404 if not.
    # Creating via PUT requires auth signature, so we just note status.
    STATUS=$(curl -o /dev/null -sw "%{http_code}" \
      -u minioadmin:minioadmin \
      "http://localhost:9000/$bucket" 2>/dev/null || echo "000")
    if [ "$STATUS" = "200" ] || [ "$STATUS" = "301" ]; then
      ok "Bucket '$bucket' exists"
    else
      warn "Bucket '$bucket' not yet created (will be auto-created on first upload)"
    fi
  done
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
sep
echo ""
echo -e "  ${BOLD}Services running:${NC}"
echo -e "   Frontend      ${GREEN}http://localhost:5173${NC}   (starting now)"
echo -e "   Backend API   ${BLUE}http://localhost:8080${NC}"
echo -e "   MinIO console ${BLUE}http://localhost:9001${NC}   (admin / minioadmin:minioadmin)"
echo -e "   Tiling svc    ${BLUE}http://localhost:8000/health${NC}"
if [ "$WITH_ML" = true ]; then
  echo -e "   Analysis svc  ${BLUE}http://localhost:8001/health${NC}"
fi
echo ""
echo -e "  ${DIM}Press Ctrl+C to stop the frontend.${NC}"
echo -e "  ${DIM}Docker services keep running. Stop them with:  bash dev.sh --down${NC}"
echo ""
sep
echo ""

# ── Open browser (best-effort) ────────────────────────────────────────────────
(
  sleep 4
  if command -v open &>/dev/null; then          # macOS
    open "http://localhost:5173"
  elif command -v xdg-open &>/dev/null; then    # Linux
    xdg-open "http://localhost:5173"
  fi
) &

# ── Cleanup message on exit ───────────────────────────────────────────────────
cleanup() {
  echo ""
  echo -e "\n  ${BOLD}Frontend stopped.${NC}"
  echo -e "  Docker services are still running in the background."
  echo -e "  To stop them:  ${BOLD}bash dev.sh --down${NC}"
  echo ""
}
trap cleanup EXIT

# ── Start frontend dev server (foreground) ────────────────────────────────────
cd "$FRONTEND_DIR"
npm run dev
