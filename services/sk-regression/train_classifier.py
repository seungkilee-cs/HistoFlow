#!/usr/bin/env python

import sys
from pathlib import Path

# Add the project root to the path to allow importing `src` as a package
sys.path.insert(0, str(Path(__file__).resolve().parent))

from src.train import train_quick_model

if __name__ == "__main__":
    train_quick_model()
