import torch
import os
import logging
import json
from pathlib import Path
from .pathology_classifier import PathologyClassifier, PCamH5Dataset
from torch.utils.data import Subset


def train_quick_model():
    """Train a classifier with defaults"""

    # Docker logging setup
    if os.environ.get("DOCKER_ENV"):
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s - %(levelname)s - %(message)s",
            handlers=[
                logging.FileHandler("/app/logs/training.log"),
                logging.StreamHandler(),
            ],
        )
        # Note: using logger is good practice, but this function doesn't use it.
        # logger = logging.getLogger(__name__)

    # Configuration
    FEATURE_METHOD = "resnet18"  # Faster than resnet50 for prototyping
    DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
    USE_SUBSET = True  # Set False to use full dataset
    SUBSET_SIZE_TRAIN = 50000
    SUBSET_SIZE_TEST = 10000
    DEFAULT_THRESHOLD = 0.5
    TARGET_SENSITIVITY = 0.9

    print(f"Using device: {DEVICE}")
    print(f"Feature extraction method: {FEATURE_METHOD}")

    # Initialize classifier
    classifier = PathologyClassifier(feature_method=FEATURE_METHOD, device=DEVICE)

    # Prepare transforms
    transform_train = classifier.prepare_transforms(augment=True)
    transform_test = classifier.prepare_transforms(augment=False)

    # Load datasets
    print("\nLoading PatchCamelyon datasets...")
    try:
        train_dataset = PCamH5Dataset(
            "data/pcam/camelyonpatch_level_2_split_train_x.h5",
            "data/pcam/camelyonpatch_level_2_split_train_y.h5",
            transform=transform_train,
        )

        test_dataset = PCamH5Dataset(
            "data/pcam/camelyonpatch_level_2_split_test_x.h5",
            "data/pcam/camelyonpatch_level_2_split_test_y.h5",
            transform=transform_test,
        )
    except FileNotFoundError as e:
        print(f"ERROR: Dataset file not found: {e.filename}")
        print(
            "Please ensure the PCam HDF5 files are in services/sk-regression/data/pcam/"
        )
        return  # Exit gracefully

    # Use subset for faster iteration
    if USE_SUBSET:
        print(
            f"\nUsing subset: {SUBSET_SIZE_TRAIN} train, {SUBSET_SIZE_TEST} test samples"
        )
        train_dataset = Subset(train_dataset, range(SUBSET_SIZE_TRAIN))
        test_dataset = Subset(test_dataset, range(SUBSET_SIZE_TEST))

    # Train
    train_metrics = classifier.train(
        train_dataset,
        C=1.0,  # Regularization parameter
        max_iter=1000,
        class_weight="balanced",
    )

    # Evaluate
    test_metrics = classifier.evaluate(
        test_dataset,
        threshold=DEFAULT_THRESHOLD,
        target_sensitivity=TARGET_SENSITIVITY,
    )

    # Save model
    model_path = f"models/pathology_lr_{FEATURE_METHOD}.pkl"
    classifier.save_model(model_path)

    metrics_path = Path(f"models/pathology_lr_{FEATURE_METHOD}_metrics.json")
    metrics_payload = {
        "config": {
            "feature_method": FEATURE_METHOD,
            "device": DEVICE,
            "use_subset": USE_SUBSET,
            "subset_size_train": SUBSET_SIZE_TRAIN,
            "subset_size_test": SUBSET_SIZE_TEST,
            "default_threshold": DEFAULT_THRESHOLD,
            "target_sensitivity": TARGET_SENSITIVITY,
        },
        "train_metrics": train_metrics,
        "test_metrics": test_metrics,
    }
    metrics_path.parent.mkdir(parents=True, exist_ok=True)
    metrics_path.write_text(json.dumps(metrics_payload, indent=2), encoding="utf-8")

    print("\n" + "=" * 60)
    print("TRAINING COMPLETE")
    print("=" * 60)
    print(f"Train Accuracy: {train_metrics['accuracy']:.4f}")
    print(f"Train AUC-ROC: {train_metrics['auc_roc']:.4f}")
    print(f"Train AUC-PR: {train_metrics['auc_pr']:.4f}")
    print(f"Train F1 @ {train_metrics['threshold']:.2f}: {train_metrics['f1']:.4f}")
    print(
        f"Train Recall @ {train_metrics['threshold']:.2f}: {train_metrics['recall']:.4f}"
    )
    print(f"Test Accuracy: {test_metrics['accuracy']:.4f}")
    print(f"Test AUC-ROC: {test_metrics['auc_roc']:.4f}")
    print(f"Test AUC-PR: {test_metrics['auc_pr']:.4f}")
    print(f"Test F1 @ {test_metrics['threshold']:.2f}: {test_metrics['f1']:.4f}")
    print(
        f"Test Recall @ {test_metrics['threshold']:.2f}: {test_metrics['recall']:.4f}"
    )

    best_f1 = test_metrics["threshold_analysis"]["best_f1"]
    print(
        f"Suggested threshold (best F1): {best_f1['threshold']:.2f} "
        f"| F1={best_f1['f1']:.4f} | Recall={best_f1['recall']:.4f}"
    )

    target_sensitivity = test_metrics["threshold_analysis"]["target_sensitivity"]
    if target_sensitivity:
        print(
            f"Suggested threshold (recall >= {TARGET_SENSITIVITY:.2f}): "
            f"{target_sensitivity['threshold']:.2f} "
            f"| Precision={target_sensitivity['precision']:.4f} "
            f"| Recall={target_sensitivity['recall']:.4f}"
        )
    else:
        print(
            f"No threshold in [0.05, 0.95] reached recall >= {TARGET_SENSITIVITY:.2f}"
        )

    print(f"\nModel saved to: {model_path}")
    print(f"Metrics saved to: {metrics_path}")
