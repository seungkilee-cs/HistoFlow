from __future__ import annotations

import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple

import numpy as np
import torch
import torchvision.transforms as T
from PIL import Image
from torchvision.models import resnet18, ResNet18_Weights
import joblib


@dataclass
class ModelInfo:
    name: str = "slide_regressor"
    feature_backbone: str = "resnet18"
    version: str = "unknown"


class SlideRegressor:
    """Feature extractor + sklearn regression head.

    - Feature extractor: torchvision resnet18 (global avg pool features)
    - Head: scikit-learn regressor saved via joblib
    """

    def __init__(self, feature_method: str = "resnet18", device: Optional[str] = None):
        if device is None:
            device = "cuda" if torch.cuda.is_available() else "cpu"
        self.device = device
        self.model_info = ModelInfo(feature_backbone=feature_method)
        self._init_backbone(feature_method)
        self.regressor = None
        self.transforms = self._default_transforms()

    def _init_backbone(self, feature_method: str) -> None:
        if feature_method != "resnet18":
            raise ValueError("Only resnet18 is supported in this reference implementation")
        weights = ResNet18_Weights.DEFAULT
        backbone = resnet18(weights=weights)
        # Drop the FC layer; keep everything up to global avg pool
        self.feature_extractor = torch.nn.Sequential(*list(backbone.children())[:-1])
        self.feature_extractor.eval().to(self.device)

    def _default_transforms(self) -> T.Compose:
        weights = ResNet18_Weights.DEFAULT
        return weights.transforms()

    def load_head(self, model_path: str) -> None:
        self.regressor = joblib.load(model_path)
        self.model_info.version = Path(model_path).stem

    @torch.no_grad()
    def _extract_features(self, img: Image.Image) -> np.ndarray:
        x = self.transforms(img).unsqueeze(0).to(self.device)
        feats = self.feature_extractor(x)  # [1, 512, 1, 1]
        feats = feats.flatten(1).cpu().numpy()  # [1, 512]
        return feats

    def predict_single_image(self, image_path: str) -> Tuple[float, float, float]:
        """Return (score, raw_score, inference_ms).

        If you trained a bounded regressor (0..1), 'score' equals 'raw_score'.
        If raw is unbounded, you can apply a logistic transform to get [0,1].
        """
        t0 = time.perf_counter()
        img = Image.open(image_path).convert("RGB")
        feats = self._extract_features(img)
        raw = float(self.regressor.predict(feats)[0])
        score = raw
        dt_ms = (time.perf_counter() - t0) * 1000
        return score, raw, dt_ms

    def classify(self, score: float, threshold: float = 0.5) -> Tuple[str, dict]:
        label = "Tumor" if score >= threshold else "Normal"
        probs = {"Normal": 1.0 - float(score), "Tumor": float(score)}
        return label, probs
