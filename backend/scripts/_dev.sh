#!/bin/bash

# Python 가상환경 활성화 스크립트
# 타일 생성 스크립트 쓸때 가상환경 켜기 귀찮아서 만듦

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE:-$0}")" && pwd)"
cd "$SCRIPT_DIR"

# Helper to exit/return depending on how the script was invoked
finish() {
    local code="$1"
    if [ "$SCRIPT_WAS_SOURCED" = "1" ]; then
        return "$code"
    else
        exit "$code"
    fi
}

# Detect whether the script is being sourced
SCRIPT_WAS_SOURCED="0"
if [ -n "${ZSH_VERSION-}" ]; then
    case $ZSH_EVAL_CONTEXT in
        *:file) SCRIPT_WAS_SOURCED="1" ;;
    esac
elif [ -n "${BASH_VERSION-}" ]; then
    if [ "${BASH_SOURCE[0]}" != "$0" ]; then
        SCRIPT_WAS_SOURCED="1"
    fi
fi

if [ "$SCRIPT_WAS_SOURCED" != "1" ]; then
    echo "Please source this script instead of executing it:"
    echo "  source ./dev.sh"
    exit 1
fi

# 가상환경 체크
if [ ! -d "venv" ]; then
    echo "가상환경이 없음. setup.sh 먼저 실행하세요:"
    echo "  ./setup.sh"
    finish 1
fi

# 가상환경 활성화
echo "Activating Python virtual environment..."
. venv/bin/activate

# 패키지 설치 체크
if ! python -c "import pyvips; import minio" 2>/dev/null; then
    echo "Installing missing packages..."
    python -m pip install -r requirements.txt
fi

echo ""
echo "Python environment ready!"
echo ""
echo "Usage:"
echo "  python generate_test_tiles.py <image_path> <image_id>"
echo ""
echo "Example:"
echo "  python generate_test_tiles.py JPG_Test.jpg test-image-001"
echo ""
echo "Available images:"
python - <<'PY'
import os

matches = []
for name in os.listdir('.'):
    if name.lower().endswith(('.jpg', '.jpeg', '.png', '.tiff', '.tif')):
        matches.append(name)

if matches:
    for name in sorted(matches):
        print(f"  {name}")
else:
    print("  (no images found)")
PY
echo ""
echo "To deactivate: deactivate"
echo ""
finish 0
