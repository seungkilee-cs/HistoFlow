import argparse
import time
import json
from pathlib import Path
from typing import Optional, List
from PIL import Image
import joblib

from .minio_io import MinioConfig, download_to_temp, cleanup_temp
from .dinov2_embedder import DinoV2Embedder

def predict_on_images(model_path: str, image_paths: list[str], *, minio_cfg: Optional[MinioConfig] = None, threshold: float = 0.5, save_jsonl: Optional[str] = None) -> list[dict]:

    # Load the trained classifier
    clf = joblib.load(model_path)
    embedder = DinoV2Embedder()

    results: List[dict] = []

    for src in image_paths:
        local_path: Optional[Path] = None
        temp_dir: Optional[Path] = None
        try:
            if src.startswith("s3://") or src.startswith("minio://"):
                if not minio_cfg: # Images must come from MinIO
                    raise ValueError("MinIO config required for URI inputs")
                local_path = download_to_temp(src, minio_cfg)
                temp_dir = local_path.parent
            else:
                print(f"Warning: skipping '{src}' — only s3:// or minio:// URIs are supported")                                                                                                                                                              
                continue

            t0 = time.perf_counter()
            img = Image.open(local_path).convert("RGB")
            embedding = embedder.embed_image(img)

            # Reshape embedding for sklearn (needs 2D array: [1, 768])
            embedding_2d = embedding.reshape(1, -1)

            # Get probabilities [prob_normal, prob_tumor]
            probabilities = clf.predict_proba(embedding_2d)[0]
            # Get time taken for embedding + prediction
            inference_ms = (time.perf_counter() - t0) * 1000

            tumor_prob = float(probabilities[1])
            label = "Tumor" if tumor_prob >= threshold else "Normal"

            result = {
                "image": src,
                "classification": {
                    "label": label,
                    "threshold": threshold,
                    "probabilities": {
                        "Normal": float(probabilities[0]),
                        "Tumor": float(probabilities[1])
                    }
                },
                "regression": {
                    "score": tumor_prob,
                    "raw_score": tumor_prob
                },
                "runtime": {
                    "inference_ms": inference_ms,
                    "device": embedder.device
                }
            }

            results.append(result)
        finally:
            if temp_dir:
                cleanup_temp(temp_dir)

    print(f"\nProcessed {len(results)} images.\n")

    for r in results:
        p = r["classification"]["probabilities"]
        print(f"Image: {Path(r['image']).name}")
        print(f"  Label: {r['classification']['label']} (threshold={r['classification']['threshold']})")
        print(f"  Score: {r['regression']['score']:.4f} | Raw: {r['regression']['raw_score']:.4f}")
        print(f"  Probabilities: Normal={p['Normal']:.4f}, Tumor={p['Tumor']:.4f}")
        print(f"  Inference: {r['runtime']['inference_ms']:.1f} ms on {r['runtime']['device']}")

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
    # Connecting to minio if all parameters are provided
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
