import torch
import os
import logging
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
        print("Please ensure the PCam HDF5 files are in services/sk-regression/data/pcam/")
        return # Exit gracefully

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
