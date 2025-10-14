# Quick Start Guide

**한국어**: [QUICKSTART.ko.md](QUICKSTART.ko.md)

## First Time Setup

```bash
cd backend/scripts
./setup.sh
```

Wait for setup to complete, then restart your terminal (or source your shell profile).

## Every Time You Use the Script

```bash
cd backend/scripts

# 1. Activate virtual environment
source venv/bin/activate

# 2. Run script
python generate_test_tiles.py <your-image.jpg> <image-id>

# 3. Deactivate when done (optional)
deactivate
```

## Example

```bash
cd backend/scripts
source venv/bin/activate
python generate_test_tiles.py JPG_Test.jpg test-image-001
```

## Troubleshooting

### "vips: command not found"
```bash
brew install vips
```

### "pyenv: command not found"
```bash
brew install pyenv
# Then add to ~/.zshrc:
export PYENV_ROOT="$HOME/.pyenv"
export PATH="$PYENV_ROOT/bin:$PATH"
eval "$(pyenv init -)"
```

### "ModuleNotFoundError: No module named 'pyvips'"
```bash
source venv/bin/activate
pip install -r requirements.txt
```

### Virtual environment not activating
```bash
# Recreate it
rm -rf venv
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## Files Created

- `requirements.txt` - Python package dependencies
- `.python-version` - pyenv Python version (3.11.9)
- `setup.sh` - Automated setup script
- `venv/` - Virtual environment (created by setup.sh)
- `.gitignore` - Excludes venv and test images from git

## What Gets Installed

- **vips** (system): Image processing library
- **pyenv** (system): Python version manager
- **Python 3.11.9** (via pyenv): Specific Python version
- **pyvips** (Python): Python bindings for vips
- **minio** (Python): MinIO/S3 client library
