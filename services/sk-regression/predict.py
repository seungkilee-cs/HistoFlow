import sys

sys.path.append("src")

import argparse
import json
from pathlib import Path
from typing import Optional

from pipeline import infer_images, InferenceParams
from minio_io import MinioConfig


def predict_on_images(model_path: str, image_paths: list[str], *, minio_cfg: Optional[MinioConfig] = None, threshold: float = 0.5, save_jsonl: Optional[str] = None) -> list[dict]:
    params = InferenceParams(threshold=threshold, save_jsonl=Path(save_jsonl) if save_jsonl else None)
    results = infer_images(model_path, image_paths, minio=minio_cfg, params=params)

    print(f"Loaded model from: {model_path}")
    print(f"Processed {len(image_paths)} images.\n")

    for r in results:
        p = r["classification"]["probabilities"]
        print(f"Image: {Path(r['image']).name}")
        print(f"  Label: {r['classification']['label']} (threshold={r['classification']['threshold']})")
        print(f"  Score: {r['regression']['score']:.4f} | Raw: {r['regression']['raw_score']:.4f}")
        print(f"  Probabilities: Normal={p['Normal']:.4f}, Tumor={p['Tumor']:.4f}")
        print(f"  Inference: {r['runtime']['inference_ms']:.1f} ms on {r['runtime']['device']}")
        print()

    return results


def main():
    """Command-line interface for predictions"""
    parser = argparse.ArgumentParser(description="Predict on pathology images (local paths or MinIO URIs)")
    parser.add_argument("--model", type=str, required=True, help="Path to trained head (.pkl file)")
    parser.add_argument("--images", nargs="+", required=True, help="Paths or s3/minio URIs to image files")
    parser.add_argument("--threshold", type=float, default=0.5, help="Threshold for classification label")
    parser.add_argument("--save-jsonl", type=str, help="Optional path to save JSONL results")

    # MinIO options
    parser.add_argument("--minio-endpoint", type=str, help="MinIO endpoint, e.g., localhost:9000")
    parser.add_argument("--minio-access-key", type=str, help="MinIO access key")
    parser.add_argument("--minio-secret-key", type=str, help="MinIO secret key")
    parser.add_argument("--minio-secure", action="store_true", help="Use HTTPS for MinIO")

    args = parser.parse_args()

    minio_cfg = None
    if args.minio_endpoint and args.minio_access_key and args.minio_secret_key:
        minio_cfg = MinioConfig(
            endpoint=args.minio_endpoint,
            access_key=args.minio_access_key,
            secret_key=args.minio_secret_key,
            secure=bool(args.minio_secure),
        )

    results = predict_on_images(
        args.model,
        args.images,
        minio_cfg=minio_cfg,
        threshold=args.threshold,
        save_jsonl=args.save_jsonl,
    )

    # Also print JSON to stdout for automation
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
