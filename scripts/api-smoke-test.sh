#!/bin/bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

API_BASE_URL=${API_BASE_URL:-"http://localhost:8080"}
IMAGE_ID=${IMAGE_ID:-"test-image-001"}

print_separator() {
  printf '\n==========================================\n'
}

echo "HistoFlow API Smoke Test"
print_separator

declare -a endpoints=(
  "GET ${API_BASE_URL}/actuator/health optional"
  "GET ${API_BASE_URL}/api/v1/tiles/${IMAGE_ID}/image.dzi required"
  "GET ${API_BASE_URL}/api/v1/tiles/${IMAGE_ID}/image_files/0/0_0.jpg required"
)

status=0

for entry in "${endpoints[@]}"; do
  method="${entry%% *}"
  rest="${entry#* }"
  url="${rest%% *}"
  requirement="${rest##* }"

  echo "Request: ${method} ${url}"
  http_code=$(curl --silent --show-error --output /dev/null --write-out "%{http_code}" --request "${method}" "${url}" || echo "000")

  if [[ "${requirement}" == "optional" && "${http_code}" == "404" ]]; then
    echo "  ⚠️ Optional endpoint missing (status ${http_code})"
  elif [[ "${http_code}" =~ ^2[0-9][0-9]$ ]]; then
    echo "  ✅ Success (${http_code})"
  else
    echo "  ❌ Failed (${http_code})"
    status=1
  fi
  print_separator
done

if [[ ${status} -eq 0 ]]; then
  echo "All required API checks passed."
else
  echo "One or more required API checks failed." >&2
fi

exit ${status}
