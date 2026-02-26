import argparse
import time
import json
from pathlib import Path
from typing import Optional
from PIL import Image
import joblib

from .minio_io import MinioConfig, download_to_temp, cleanup_temp
from .dinov2_embedder import DinoV2Embedder

def infer_one(src: str, minio_client, embedder: DinoV2Embedder, clf, threshold: float) -> dict:
    """Download, embed, and classify a single image. Cleans up temp files when done."""
    local_path: Optional[Path] = None
    temp_dir: Optional[Path] = None
    try:
        local_path = download_to_temp(src, minio_client)
        temp_dir = local_path.parent

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

        return {
            "image": src,
            "classification": {
                "label": label,
                "threshold": threshold,
                "probabilities": {
                    "Normal": float(probabilities[0]),
                    "Tumor": float(probabilities[1])
                }
            },
            "score": {
                "score": tumor_prob, # May be processed differently later as we add calculations
                "raw_score": tumor_prob
            },
            "runtime": {
                "inference_ms": inference_ms,
                "device": embedder.device
            }
        }
    finally:
        if temp_dir:
            cleanup_temp(temp_dir) # Clean up once processing is done


def predict_on_images(model_path: str, image_paths: list[str], *, minio_cfg: Optional[MinioConfig] = None, threshold: float = 0.5, save_jsonl: Optional[str] = None) -> list[dict]:

    # Load the trained classifier
    clf = joblib.load(model_path)
    embedder = DinoV2Embedder()

    # Create MinIO client once and reuse across all images
    minio_client = minio_cfg.client() if minio_cfg else None

    results: list[dict] = []
    failed: list[str] = []

    for src in image_paths:
        if not (src.startswith("s3://") or src.startswith("minio://")):
            print(f"Warning: skipping '{src}' — only s3:// or minio:// URIs are supported")
            continue
        if not minio_client:
            raise ValueError("MinIO config required for URI inputs")

        try:
            result = infer_one(src, minio_client, embedder, clf, threshold)
        # Try infer just once more in case of network blip
        except Exception as e:
            print(f"Warning: first attempt failed for '{src}' — {e}. Retrying...")
            try:
                result = infer_one(src, minio_client, embedder, clf, threshold)
            except Exception as e2:
                print(f"Warning: '{src}' failed after retry — {e2}")
                result = {"image": src, "error": True, "reason": str(e2)}
                failed.append(src)

        results.append(result)

    print(f"\nProcessed {len(results) - len(failed)} images successfully, {len(failed)} failed.\n")

    for r in results:
        if r.get("error"):
            print(f"Image: {Path(r['image']).name} — ERROR: {r['reason']}")
            continue
        p = r["classification"]["probabilities"]
        print(f"Image: {Path(r['image']).name}")
        print(f"  Label: {r['classification']['label']} (threshold={r['classification']['threshold']})")
        print(f"  Score: {r['score']['score']:.4f} | Raw: {r['score']['raw_score']:.4f}")
        print(f"  Probabilities: Normal={p['Normal']:.4f}, Tumor={p['Tumor']:.4f}")
        print(f"  Inference: {r['runtime']['inference_ms']:.1f} ms on {r['runtime']['device']}")

    if save_jsonl:
        with open(save_jsonl, "w") as f:
            for r in results:
                f.write(json.dumps(r) + "\n")
        print(f"\nResults saved to: {save_jsonl}")

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
