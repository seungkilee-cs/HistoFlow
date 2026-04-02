#!/usr/bin/env bash

histoflow_port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi

  nc -z localhost "$port" >/dev/null 2>&1
}

histoflow_assign_port() {
  local __result_var="$1"
  local label="$2"
  shift 2

  local port
  for port in "$@"; do
    if ! histoflow_port_in_use "$port"; then
      printf -v "$__result_var" '%s' "$port"
      export "$__result_var"
      return 0
    fi
  done

  echo "No available host ports found for ${label}. Tried: $*" >&2
  return 1
}

histoflow_resolve_runtime_ports() {
  local repo_root="$1"
  local runtime_dir="$repo_root/.omx/runtime"
  local runtime_env="$runtime_dir/ports.env"

  mkdir -p "$runtime_dir"

  histoflow_assign_port POSTGRES_HOST_PORT "PostgreSQL" 5432 5433 5434 15432
  histoflow_assign_port MINIO_HOST_PORT "MinIO API" 9000 9002 9003 19000
  histoflow_assign_port MINIO_CONSOLE_HOST_PORT "MinIO console" 9001 9004 9005 19001
  histoflow_assign_port BACKEND_HOST_PORT "Backend API" 8080 8081 8082 18080
  histoflow_assign_port TILING_HOST_PORT "Tiling service" 8000 8002 8003 18000
  histoflow_assign_port ANALYSIS_HOST_PORT "Analysis service" 8001 8004 8005 18001
  histoflow_assign_port FRONTEND_PORT "Frontend dev server" 5173 5174 5175 3000 3001

  export MINIO_PUBLIC_ENDPOINT="http://localhost:${MINIO_HOST_PORT}"
  export BACKEND_PUBLIC_URL="http://localhost:${BACKEND_HOST_PORT}"
  export TILING_PUBLIC_URL="http://localhost:${TILING_HOST_PORT}"
  export ANALYSIS_PUBLIC_URL="http://localhost:${ANALYSIS_HOST_PORT}"
  export FRONTEND_PUBLIC_URL="http://localhost:${FRONTEND_PORT}"
  export HISTOFLOW_RUNTIME_ENV_FILE="$runtime_env"

  cat >"$runtime_env" <<EOF
POSTGRES_HOST_PORT=${POSTGRES_HOST_PORT}
MINIO_HOST_PORT=${MINIO_HOST_PORT}
MINIO_CONSOLE_HOST_PORT=${MINIO_CONSOLE_HOST_PORT}
BACKEND_HOST_PORT=${BACKEND_HOST_PORT}
TILING_HOST_PORT=${TILING_HOST_PORT}
ANALYSIS_HOST_PORT=${ANALYSIS_HOST_PORT}
FRONTEND_PORT=${FRONTEND_PORT}
MINIO_PUBLIC_ENDPOINT=${MINIO_PUBLIC_ENDPOINT}
BACKEND_PUBLIC_URL=${BACKEND_PUBLIC_URL}
TILING_PUBLIC_URL=${TILING_PUBLIC_URL}
ANALYSIS_PUBLIC_URL=${ANALYSIS_PUBLIC_URL}
FRONTEND_PUBLIC_URL=${FRONTEND_PUBLIC_URL}
EOF
}
