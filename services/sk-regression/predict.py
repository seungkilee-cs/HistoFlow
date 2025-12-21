import sys

sys.path.append("src")

import argparse
from pathlib import Path

from pathology_classifier import PathologyClassifier


def predict_on_images(model_path: str, image_paths: list[str]) -> list[dict]:
    """
    Make predictions on a list of images

    Args:
        model_path (str): Path to saved model
        image_paths (list): List of image file paths
    """
    # Initialize classifier and load model
    classifier = PathologyClassifier()
    classifier.load_model(model_path)

    print(f"Loaded model from: {model_path}")
    print(f"Processing {len(image_paths)} images...\n")

    # Predict on each image
    results = []
    for img_path in image_paths:
        prediction, probability = classifier.predict_single_image(img_path)

        class_name = "Tumor" if prediction == 1 else "Normal"
        confidence = probability[1] if prediction == 1 else probability[0]

        results.append(
            {"image": img_path, "prediction": class_name, "confidence": confidence}
        )

        print(f"Image: {Path(img_path).name}")
        print(f"  Prediction: {class_name}")
        print(f"  Confidence: {confidence:.4f}")
        print(
            f"  Probabilities: Normal={probability[0]:.4f}, Tumor={probability[1]:.4f}\n"
        )

    return results


def main():
    """Command-line interface for predictions"""
    parser = argparse.ArgumentParser(description="Predict on pathology images")
    parser.add_argument(
        "--model", type=str, required=True, help="Path to trained model (.pkl file)"
    )
    parser.add_argument(
        "--images", nargs="+", required=True, help="Paths to image files"
    )

    args = parser.parse_args()

    predict_on_images(args.model, args.images)


if __name__ == "__main__":
    main()
