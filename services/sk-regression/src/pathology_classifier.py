import torch
import h5py
from torch.utils.data import Dataset
from PIL import Image
import numpy as np
from tqdm import tqdm
import joblib
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    roc_auc_score,
    accuracy_score,
    average_precision_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix,
)

# Assuming the SlideRegressor is the intended feature extractor
from .regression_model import SlideRegressor


class PCamH5Dataset(Dataset):
    """PyTorch Dataset for the PCam HDF5 files."""

    def __init__(self, x_path, y_path, transform=None):
        self.x_file = h5py.File(x_path, "r")
        self.y_file = h5py.File(y_path, "r")
        self.x_data = self.x_file["x"]
        self.y_data = self.y_file["y"]
        self.transform = transform

    def __len__(self):
        return self.x_data.shape[0]

    def __getitem__(self, idx):
        # HDF5 data is typically (N, C, H, W) or (N, H, W, C)
        # PyTorch expects (C, H, W). PIL Image expects (H, W, C).
        img_array = self.x_data[idx]
        img = Image.fromarray(img_array.astype("uint8"), "RGB")

        label = torch.from_numpy(self.y_data[idx].astype("float32")).squeeze()

        if self.transform:
            img = self.transform(img)

        return img, label

    def close(self):
        self.x_file.close()
        self.y_file.close()


class PathologyClassifier:
    """
    A classifier that uses a SlideRegressor for feature extraction and
    trains a scikit-learn head.
    """

    def __init__(self, feature_method="resnet18", device="cpu"):
        self.feature_extractor = SlideRegressor(feature_method, device)
        self.device = device
        self.model = None

    def prepare_transforms(self, augment=False):
        # We can leverage the default transforms from SlideRegressor
        # and optionally add augmentations.
        if augment:
            # You can add more augmentations here if needed
            return self.feature_extractor.transforms
        return self.feature_extractor.transforms

    def _extract_features_from_dataset(self, dataset):
        """Helper to extract features from a full dataset."""
        all_features = []
        all_labels = []

        # Use a DataLoader for batching and performance
        dataloader = torch.utils.data.DataLoader(
            dataset, batch_size=32, shuffle=False, num_workers=2
        )

        with torch.no_grad():
            for images, labels in tqdm(dataloader, desc="Extracting features"):
                # The SlideRegressor's feature extractor expects a single image,
                # but we can adapt it for batches. Let's process one by one for simplicity,
                # but a batched version would be faster.
                for i in range(images.shape[0]):
                    # The transform is already applied by the dataset, so we just need to extract
                    img_tensor = images[i].unsqueeze(0).to(self.device)
                    feats = self.feature_extractor.feature_extractor(img_tensor)
                    feats = feats.flatten(1).cpu().numpy()
                    all_features.append(feats)
                all_labels.extend(labels.cpu().numpy())

        return np.vstack(all_features), np.array(all_labels).ravel()

    def train(self, train_dataset, **kwargs):
        print("Extracting training features...")
        X_train, y_train = self._extract_features_from_dataset(train_dataset)

        print(f"Training Logistic Regression head on {len(y_train)} samples...")
        # Use provided kwargs for LogisticRegression
        self.model = LogisticRegression(**kwargs)
        self.model.fit(X_train, y_train)

        print("Training complete.")

        # Return training metrics
        train_probs = self.model.predict_proba(X_train)[:, 1]

        return self._metrics_from_probs(y_train, train_probs, threshold=0.5)

    def _metrics_from_probs(self, y_true, y_prob, threshold=0.5):
        y_pred = (y_prob >= threshold).astype(int)
        tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()
        return {
            "accuracy": accuracy_score(y_true, y_pred),
            "auc_roc": roc_auc_score(y_true, y_prob),
            "auc_pr": average_precision_score(y_true, y_prob),
            "precision": precision_score(y_true, y_pred, zero_division=0),
            "recall": recall_score(y_true, y_pred, zero_division=0),
            "f1": f1_score(y_true, y_pred, zero_division=0),
            "threshold": float(threshold),
            "confusion_matrix": {
                "tn": int(tn),
                "fp": int(fp),
                "fn": int(fn),
                "tp": int(tp),
            },
        }

    def _best_f1_threshold(self, y_true, y_prob):
        thresholds = np.linspace(0.05, 0.95, 91)
        best_metrics = None
        best_f1 = -1.0
        for threshold in thresholds:
            metrics = self._metrics_from_probs(y_true, y_prob, threshold=threshold)
            if metrics["f1"] > best_f1:
                best_f1 = metrics["f1"]
                best_metrics = metrics
        return best_metrics

    def _target_sensitivity_threshold(self, y_true, y_prob, target_sensitivity):
        thresholds = np.linspace(0.05, 0.95, 91)
        candidates = []
        for threshold in thresholds:
            metrics = self._metrics_from_probs(y_true, y_prob, threshold=threshold)
            if metrics["recall"] >= target_sensitivity:
                candidates.append(metrics)

        if not candidates:
            return None

        candidates.sort(key=lambda m: (m["precision"], m["f1"]), reverse=True)
        return candidates[0]

    def evaluate(self, test_dataset, threshold=0.5, target_sensitivity=None):
        if not self.model:
            raise RuntimeError("Model has not been trained yet. Call train() first.")

        print("Extracting test features...")
        X_test, y_test = self._extract_features_from_dataset(test_dataset)

        print("Evaluating model...")
        test_probs = self.model.predict_proba(X_test)[:, 1]
        primary_metrics = self._metrics_from_probs(
            y_test, test_probs, threshold=threshold
        )
        best_f1_metrics = self._best_f1_threshold(y_test, test_probs)
        target_sensitivity_metrics = None
        if target_sensitivity is not None:
            target_sensitivity_metrics = self._target_sensitivity_threshold(
                y_test,
                test_probs,
                target_sensitivity,
            )

        result = dict(primary_metrics)
        result["threshold_analysis"] = {
            "best_f1": best_f1_metrics,
            "target_sensitivity": target_sensitivity_metrics,
            "target_sensitivity_value": target_sensitivity,
        }
        return result

    def save_model(self, path):
        if not self.model:
            raise RuntimeError("No model to save. Call train() first.")
        print(f"Saving model to {path}")
        joblib.dump(self.model, path)
