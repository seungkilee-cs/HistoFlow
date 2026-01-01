from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, List, Optional

from .minio_io import MinioConfig, download_to_temp
from .regression_model import SlideRegressor, ModelInfo


@dataclass
class InferenceParams:
    threshold: float = 0.5
    save_jsonl: Optional[Path] = None


def infer_images(
    model_path: str,
    images: Iterable[str],
    *,
    minio: Optional[MinioConfig] = None,
    params: Optional[InferenceParams] = None,
) -> List[dict]:
    params = params or InferenceParams()
    analyzer = SlideRegressor()
    analyzer.load_head(model_path)

    # Prepare JSONL writer if requested
    jsonl_fp = None
    if params.save_jsonl:
        Path(params.save_jsonl).parent.mkdir(parents=True, exist_ok=True)
        jsonl_fp = open(params.save_jsonl, "w", encoding="utf-8")

    results: List[dict] = []
    temp_dirs: List[Path] = []

    try:
        for src in images:
            local = src
            temp_dir: Optional[Path] = None
            if src.startswith("s3://") or src.startswith("minio://"):
                if not minio:
                    raise ValueError("MinIO config required for URI inputs")
                local_path = download_to_temp(src, minio)
                local = str(local_path)
                temp_dir = local_path.parent

            score, raw, ms = analyzer.predict_single_image(local)
            label, probs = analyzer.classify(score, threshold=params.threshold)

            image_id = Path(local).stem
            record = {
                "image": src,
                "image_id": image_id,
                "model": asdict(analyzer.model_info),
                "regression": {"score": float(score), "raw_score": float(raw)},
                "classification": {"label": label, "probabilities": probs, "threshold": params.threshold},
                "preprocessing": {"resize": 224, "normalization": "imagenet"},
                "runtime": {"device": analyzer.device, "inference_ms": ms},
            }

            results.append(record)
            if jsonl_fp:
                jsonl_fp.write(json.dumps(record) + "\n")

            if temp_dir:
                temp_dirs.append(temp_dir)

    finally:
        if jsonl_fp:
            jsonl_fp.close()
        # Best-effort cleanup for temp dirs
        for d in temp_dirs:
            try:
                for p in d.iterdir():
                    p.unlink(missing_ok=True)
                d.rmdir()
            except Exception:
                pass

    return results
