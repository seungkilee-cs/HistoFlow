import sys

sys.path.append("src")

import torch
from pathology_classifier import PathologyClassifier, PCamH5Dataset
from torch.utils.data import Subset


def train_quick_model():
    """Train a classifier with defaults"""

    # Docker logging setup
    import os

    if os.environ.get("DOCKER_ENV"):
        import logging

        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s - %(levelname)s - %(message)s",
            handlers=[
                logging.FileHandler("/app/logs/training.log"),
                logging.StreamHandler(),
            ],
        )
        logger = logging.getLogger(__name__)

        # Configuration
        FEATURE_METHOD = "resnet18"  # Faster than resnet50 for prototyping
        DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
        USE_SUBSET = True  # Set False to use full dataset
        SUBSET_SIZE_TRAIN = 10000  # Use 10k training samples for quick iteration
        SUBSET_SIZE_TEST = 2000  # Use 2k test samples

        print(f"Using device: {DEVICE}")
        print(f"Feature extraction method: {FEATURE_METHOD}")

        # Initialize classifier
        classifier = PathologyClassifier(feature_method=FEATURE_METHOD, device=DEVICE)

        # Prepare transforms
        transform_train = classifier.prepare_transforms(augment=True)
        transform_test = classifier.prepare_transforms(augment=False)

        # Load datasets
        print("\nLoading PatchCamelyon datasets...")
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
            class_weight="balanced",  # Handle class imbalance
        )

        # Evaluate
        test_metrics = classifier.evaluate(test_dataset)

        # Save model
        model_path = f"models/pathology_lr_{FEATURE_METHOD}.pkl"
        classifier.save_model(model_path)

        print("\n" + "=" * 60)
        print("TRAINING COMPLETE")
        print("=" * 60)
        print(f"Train Accuracy: {train_metrics['accuracy']:.4f}")
        print(f"Train AUC-ROC: {train_metrics['auc_roc']:.4f}")
        print(f"Test Accuracy: {test_metrics['accuracy']:.4f}")
        print(f"Test AUC-ROC: {test_metrics['auc_roc']:.4f}")
        print(f"\nModel saved to: {model_path}")


if __name__ == "__main__":
    train_quick_model()
