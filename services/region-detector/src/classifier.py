"""Classifier head — loads a trained sklearn model and predicts tumour probability.

The classifier is a lightweight scikit-learn model (typically ``LogisticRegression``)
that was trained on DINOv2 embeddings of pathology tiles (e.g. PCam dataset).
It is stored as a ``.pkl`` file created with ``joblib.dump()``.

Input:  embedding vector (1-D numpy array, 768-d for dinov2-base)
Output: ``ClassificationResult`` with label, probability, and class probabilities
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Dict

import joblib
import numpy as np

from .config import settings


@dataclass
class ClassificationResult:
    label: str  # "Tumor" or "Normal"
    tumor_probability: float  # 0.0 – 1.0
    probabilities: Dict[str, float]  # {"Normal": ..., "Tumor": ...}


class Classifier:
    """Wraps a sklearn classifier head loaded from a joblib file."""

    def __init__(self, model_path: str | None = None):
        self.model_path = model_path or settings.MODEL_PATH
        self._clf = None

    def load(self) -> None:
        """Load the model from disk.  Idempotent — only loads once."""
        if self._clf is not None:
            return
        path = Path(self.model_path)
        if not path.exists():
            raise FileNotFoundError(
                f"Classifier model not found at {path}.  "
                f"Train one first or mount the model file into the container."
            )
        print(f"[Classifier] Loading model from {path}")
        self._clf = joblib.load(path)

    def predict(
        self,
        embedding: np.ndarray,
        threshold: float | None = None,
    ) -> ClassificationResult:
        """Classify a single embedding vector.

        Parameters
        ----------
        embedding:
            1-D array of shape ``(embedding_dim,)``
        threshold:
            Decision threshold for the "Tumor" label.  Defaults to
            ``settings.CLASSIFICATION_THRESHOLD``.
        """
        self.load()
        threshold = threshold if threshold is not None else settings.CLASSIFICATION_THRESHOLD

        x = embedding.reshape(1, -1)
        probs = self._clf.predict_proba(x)[0]

        # sklearn class ordering: [Normal=0, Tumor=1]
        tumor_prob = float(probs[1])
        label = "Tumor" if tumor_prob >= threshold else "Normal"

        return ClassificationResult(
            label=label,
            tumor_probability=tumor_prob,
            probabilities={"Normal": float(probs[0]), "Tumor": tumor_prob},
        )

    def predict_batch(
        self,
        embeddings: np.ndarray,
        threshold: float | None = None,
    ) -> list[ClassificationResult]:
        """Classify a batch of embedding vectors at once."""
        self.load()
        threshold = threshold if threshold is not None else settings.CLASSIFICATION_THRESHOLD

        probs = self._clf.predict_proba(embeddings)  # (N, 2)
        results: list[ClassificationResult] = []
        for p in probs:
            tumor_prob = float(p[1])
            label = "Tumor" if tumor_prob >= threshold else "Normal"
            results.append(
                ClassificationResult(
                    label=label,
                    tumor_probability=tumor_prob,
                    probabilities={"Normal": float(p[0]), "Tumor": tumor_prob},
                )
            )
        return results
