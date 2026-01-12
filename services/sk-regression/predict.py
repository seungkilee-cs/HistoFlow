#!/usr/bin/env python

import sys
from pathlib import Path

# Add the parent directory of `src` to the path to allow importing `src` as a package
sys.path.insert(0, str(Path(__file__).resolve().parent))

from src.main import main

if __name__ == "__main__":
    main()
