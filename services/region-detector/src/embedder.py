"""DINOv2 feature embedder.

Wraps ``facebook/dinov2-base`` from Hugging Face and produces a fixed-size
embedding vector (768-d) for each input tile image.  Supports both single
and batched embedding for GPU efficiency.

The model is loaded **once** at import time (module-level singleton) so
consecutive calls reuse the same weights.
"""

from __future__ import annotations

from typing import List

import numpy as np
import torch
from PIL import Image
from transformers import AutoImageProcessor, AutoModel

from .config import settings


class Embedder:
    """Thin wrapper around a DINOv2 model for feature extraction."""

    def __init__(self, model_name: str | None = None):
        self.model_name = model_name or settings.BACKBONE
        self.device = "cuda" if torch.cuda.is_available() else "cpu"

        print(f"[Embedder] Loading {self.model_name} on {self.device}")
        self.processor = AutoImageProcessor.from_pretrained(self.model_name)
        self.model = AutoModel.from_pretrained(self.model_name).to(self.device)
        self.model.eval()

    @property
    def embedding_dim(self) -> int:
        """Dimensionality of the output embedding (768 for dinov2-base)."""
        return self.model.config.hidden_size

    # ── Single image ──────────────────────────────────────────────────

    @torch.no_grad()
    def embed(self, image: Image.Image) -> np.ndarray:
        """Return a 1-D numpy array of shape ``(embedding_dim,)``."""
        inputs = self.processor(images=image, return_tensors="pt").to(self.device)
        outputs = self.model(**inputs)
        # CLS token = representative vector for the entire image
        cls = outputs.last_hidden_state[:, 0]
        return cls.cpu().numpy().flatten()

    # ── Batched ───────────────────────────────────────────────────────

    @torch.no_grad()
    def embed_batch(
        self, images: List[Image.Image], batch_size: int = 16
    ) -> np.ndarray:
        """Return array of shape ``(len(images), embedding_dim)``."""
        all_embs: list[np.ndarray] = []
        for i in range(0, len(images), batch_size):
            batch = images[i : i + batch_size]
            inputs = self.processor(images=batch, return_tensors="pt").to(self.device)
            outputs = self.model(**inputs)
            embs = outputs.last_hidden_state[:, 0].cpu().numpy()
            all_embs.append(embs)
        return np.vstack(all_embs)
