#!/bin/bash

# Python 가상환경 활성화 스크립트
# 타일 생성 스크립트 쓸때 가상환경 켜기 귀찮아서 만듦

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 가상환경 체크
if [ ! -d "venv" ]; then
    echo "가상환경이 없음. setup.sh 먼저 실행하세요:"
    echo "  ./setup.sh"
    exit 1
fi

# 가상환경 활성화
echo "Activating Python virtual environment..."
source venv/bin/activate

# 패키지 설치 체크
if ! python -c "import pyvips; import minio" 2>/dev/null; then
    echo "Installing missing packages..."
    pip install -r requirements.txt
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
ls -1 *.jpg *.jpeg *.png *.tiff *.tif 2>/dev/null || echo "  (no images found)"
echo ""
echo "To deactivate: deactivate"
echo ""

# 새 쉘 시작 (가상환경 활성화된 상태로)
exec $SHELL
