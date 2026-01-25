#!/usr/bin/env python3
"""
Download PatchCamelyon (PCam) dataset in HDF5 format.

The PCam dataset is a binary classification dataset of histopathology images.
- Training set: 262,144 images
- Test set: 32,768 images
- Image size: 96x96 pixels, RGB
- Labels: 0 (no tumor), 1 (tumor detected)

Dataset hosted at: https://github.com/basveeling/pcam
"""

import os
import urllib.request
from pathlib import Path
from tqdm import tqdm


class DownloadProgressBar(tqdm):
    """Progress bar for downloads."""
    def update_to(self, b=1, bsize=1, tsize=None):
        if tsize is not None:
            self.total = tsize
        self.update(b * bsize - self.n)


def download_file(url: str, output_path: str) -> None:
    """Download a file with progress bar."""
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    if output_path.exists():
        print(f"✓ {output_path.name} already exists, skipping...")
        return
    
    print(f"Downloading {output_path.name}...")
    with DownloadProgressBar(unit='B', unit_scale=True, miniters=1, desc=output_path.name) as t:
        urllib.request.urlretrieve(url, output_path, reporthook=t.update_to)
    print(f"✓ Downloaded to {output_path}")


def download_pcam_dataset(data_dir: str = "data/pcam") -> None:
    """Download the PCam HDF5 dataset files.
    
    Args:
        data_dir: Directory to save the dataset (relative to script location)
    """
    # Base URL for PCam dataset
    base_url = "https://zenodo.org/record/2546921/files"
    
    # Files to download (training and test sets, images and labels)
    files = {
        "camelyonpatch_level_2_split_train_x.h5.gz": f"{base_url}/camelyonpatch_level_2_split_train_x.h5.gz?download=1",
        "camelyonpatch_level_2_split_train_y.h5.gz": f"{base_url}/camelyonpatch_level_2_split_train_y.h5.gz?download=1",
        "camelyonpatch_level_2_split_test_x.h5.gz": f"{base_url}/camelyonpatch_level_2_split_test_x.h5.gz?download=1",
        "camelyonpatch_level_2_split_test_y.h5.gz": f"{base_url}/camelyonpatch_level_2_split_test_y.h5.gz?download=1",
    }
    
    # Get absolute path to data directory
    script_dir = Path(__file__).parent.parent
    data_path = script_dir / data_dir
    data_path.mkdir(parents=True, exist_ok=True)
    
    print("=" * 70)
    print("PatchCamelyon (PCam) Dataset Download")
    print("=" * 70)
    print(f"Dataset will be saved to: {data_path.absolute()}")
    print(f"Total files to download: {len(files)}")
    print(f"Approximate total size: ~7.5 GB")
    print("=" * 70)
    print()
    
    # Download each file
    for filename, url in files.items():
        output_file = data_path / filename
        download_file(url, output_file)
    
    print()
    print("=" * 70)
    print("Download Complete!")
    print("=" * 70)
    print()
    print("Next steps:")
    print("1. Extract the .gz files:")
    print(f"   cd {data_path.absolute()}")
    print("   gunzip *.gz")
    print()
    print("2. You should have these files:")
    print("   - camelyonpatch_level_2_split_train_x.h5")
    print("   - camelyonpatch_level_2_split_train_y.h5")
    print("   - camelyonpatch_level_2_split_test_x.h5")
    print("   - camelyonpatch_level_2_split_test_y.h5")
    print()
    print("3. Run training:")
    print("   python src/train.py")
    print("=" * 70)


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Download PCam dataset")
    parser.add_argument(
        "--data-dir",
        type=str,
        default="data/pcam",
        help="Directory to save dataset (default: data/pcam)"
    )
    
    args = parser.parse_args()
    
    try:
        download_pcam_dataset(args.data_dir)
    except KeyboardInterrupt:
        print("\nDownload interrupted by user")
    except Exception as e:
        print(f"\nError: {e}")
        print("Please check your internet connection and try again")
