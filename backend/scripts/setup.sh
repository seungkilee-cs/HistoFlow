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

# 2. requirements를 불러올 pyenv 설치 확인
if ! command -v pyenv &> /dev/null; then
    echo "pyenv not found. Installing..."
    brew install pyenv
    
    echo ""
    echo "Add these lines to your shell profile (~/.zshrc or ~/.bash_profile):"
    echo '  export PYENV_ROOT="$HOME/.pyenv"'
    echo '  export PATH="$PYENV_ROOT/bin:$PATH"'
    echo '  eval "$(pyenv init -)"'
    echo ""
    echo "Then restart your shell and run this script again."
    exit 1
fi

# 3. Python 3.11.9 설치
PYTHON_VERSION="3.11.9"
if ! pyenv versions | grep -q "$PYTHON_VERSION"; then
    echo "Installing Python $PYTHON_VERSION..."
    pyenv install $PYTHON_VERSION
else
    echo "Python $PYTHON_VERSION already installed"
fi

# 4. 로컬 Python 버전 설정 (backend/scripts 디렉토리 내의 파이썬 버전임)
pyenv local $PYTHON_VERSION
echo "Set local Python version to $PYTHON_VERSION"

# 5. 가상환경 생성
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python -m venv venv
fi

# 6. 가상환경 활성화 및 패키지 설치
echo "Installing Python packages..."
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt

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
