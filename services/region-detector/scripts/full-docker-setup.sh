#!/usr/bin/env bash

# Region-detector wrapper for full HistoFlow docker setup.
# This delegates to the canonical project bootstrap script.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
BOOTSTRAP_SCRIPT="${PROJECT_ROOT}/scripts/setup-and-start.sh"

if [ ! -x "${BOOTSTRAP_SCRIPT}" ]; then
  echo "❌ Missing bootstrap script: ${BOOTSTRAP_SCRIPT}"
  exit 1
fi

echo "▶ Running full stack setup via ${BOOTSTRAP_SCRIPT}"
echo "  START_FRONTEND=${START_FRONTEND:-0}"
echo "  RUN_REGION_TESTS=${RUN_REGION_TESTS:-1}"

START_FRONTEND="${START_FRONTEND:-0}" \
RUN_REGION_TESTS="${RUN_REGION_TESTS:-1}" \
"${BOOTSTRAP_SCRIPT}"
