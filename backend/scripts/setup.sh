#!/bin/bash

# 파이썬 스크립트 짜기 귀찮으셨죠? 그러실줄 알고 준비했습니다.

set -e

echo "Setting up tile generation environment..."

# 1. 타일링을 위한 vips 설치 확인
if ! command -v vips &> /dev/null; then
    echo "Installing vips..."
    brew install vips
else
    echo "vips already installed: $(vips --version | head -1)"
fi

# 2. Python 선택 (SSL 지원 필수)
find_python_with_ssl() {
    for candidate in "$@"; do
        if command -v "$candidate" >/dev/null 2>&1; then
            local resolved
            resolved="$(command -v "$candidate")"
            if "$resolved" -c "import ssl" >/dev/null 2>&1; then
                PYTHON_BIN="$resolved"
                return 0
            else
                echo "Found $resolved but SSL module missing"
            fi
        fi
    done
    return 1
}

echo "Searching for Python interpreter with SSL support..."
PYTHON_BIN=""

if find_python_with_ssl python3 python python3.12; then
    echo "Using existing Python: $PYTHON_BIN"
else
    echo "No suitable Python found. Installing Homebrew python@3.12..."
    PYTHON_FORMULA="python@3.12"
    if ! brew ls --versions $PYTHON_FORMULA > /dev/null; then
        brew install $PYTHON_FORMULA
    else
        echo "$PYTHON_FORMULA already installed"
    fi

    PYTHON_PREFIX="$(brew --prefix $PYTHON_FORMULA)"
    if [ -x "$PYTHON_PREFIX/libexec/bin/python3.12" ]; then
        PYTHON_BIN="$PYTHON_PREFIX/libexec/bin/python3.12"
    elif [ -x "$PYTHON_PREFIX/bin/python3.12" ]; then
        PYTHON_BIN="$PYTHON_PREFIX/bin/python3.12"
    elif [ -x "$PYTHON_PREFIX/bin/python3" ]; then
        PYTHON_BIN="$PYTHON_PREFIX/bin/python3"
    else
        echo "Error: Could not locate python3 binary in Homebrew prefix ($PYTHON_PREFIX)"
        exit 1
    fi

    if ! "$PYTHON_BIN" -c "import ssl" >/dev/null 2>&1; then
        echo "Error: Homebrew python still missing SSL. Try: brew reinstall $PYTHON_FORMULA"
        exit 1
    fi

    echo "Using Homebrew Python: $PYTHON_BIN"
fi

# 5. 가상환경 생성
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    "$PYTHON_BIN" -m venv venv
fi

# 6. 가상환경 활성화 및 패키지 설치
echo "Installing Python packages..."
source venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

echo ""
echo "Setup complete!"
echo ""
echo "To use the script:"
echo "  1. Activate virtual environment: source venv/bin/activate"
echo "  2. Run script: python generate_test_tiles.py <image> <id>"
echo ""
echo "Example:"
echo "  source venv/bin/activate"
echo "  python generate_test_tiles.py test.jpg test-image-001"
