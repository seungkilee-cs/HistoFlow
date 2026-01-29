import h5py
import numpy as np
from PIL import Image
from dinov2_embedder import DinoV2Embedder
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, roc_auc_score
import joblib
from pathlib import Path


def train_classifier():
    """Train a DINOv2-based classifier on PCam dataset"""
    
    # Paths to the HDF5 files
    train_images_path = "data/pcam/camelyonpatch_level_2_split_train_x.h5"
    train_labels_path = "data/pcam/camelyonpatch_level_2_split_train_y.h5"
    
    # Configuration
    NUM_SAMPLES = 10000  # Use 10k samples for faster training (full dataset has 262k)
    BATCH_SIZE = 32      # Process 32 images at a time for embeddings
    
    print("=" * 60)
    print("TRAINING DINOV2 CLASSIFIER")
    print("=" * 60)
    print(f"Using {NUM_SAMPLES} samples for training")
    
    # Open HDF5 files
    print("\nOpening HDF5 files...")
    img_file = h5py.File(train_images_path, 'r')
    label_file = h5py.File(train_labels_path, 'r')
    
    images = img_file['x'] # Shape: (N, 96, 96, 3)
    labels = label_file['y']
    
    print(f"Total samples available: {images.shape[0]:,}")
    
    # Initialize DINOv2 embedder
    print("\nInitializing DINOv2 embedder...")
    embedder = DinoV2Embedder()
    
    # Collect embeddings and labels
    print(f"\nGenerating embeddings for {NUM_SAMPLES} samples...")
    all_embeddings = []
    all_labels = []
    
    # Process images in batches
    for i in range(0, NUM_SAMPLES, BATCH_SIZE):
        batch_end = min(i + BATCH_SIZE, NUM_SAMPLES)
        batch_images = []
        
        # Load batch of images
        for j in range(i, batch_end):
            img_array = images[j]
            pil_img = Image.fromarray(img_array, 'RGB')
            batch_images.append(pil_img)
            
            # Get label either 0 or 1
            label_value = int(labels[j].squeeze())
            all_labels.append(label_value)
        
        # Generate embeddings for batch
        batch_embeddings = embedder.embed_images(batch_images, batch_size=len(batch_images))
        all_embeddings.append(batch_embeddings)
        
        if (i + BATCH_SIZE) % 1000 == 0:
            print(f"  Processed {i + BATCH_SIZE}/{NUM_SAMPLES} samples...")
    
    # Convert to numpy arrays
    X = np.vstack(all_embeddings)  # Shape: (NUM_SAMPLES, 768)
    y = np.array(all_labels)       # Shape: (NUM_SAMPLES,)
    
    print(f"\n✓ Generated embeddings!")
    print(f"  X shape: {X.shape}")
    print(f"  y shape: {y.shape}")
    print(f"  Class distribution: Normal={np.sum(y==0)}, Tumor={np.sum(y==1)}")
    
    # Split into train and validation sets
    print("\nSplitting data (80% train, 20% validation)...")

    X_train, X_val, y_train, y_val = train_test_split(
        X,
        y,
        test_size=0.2,      # 20% for validation
        random_state=42,    # For reproducibility
        stratify=y          # Keep same class distribution in both splits
    )
    
    print(f"  Training samples: {len(y_train)}")
    print(f"  Validation samples: {len(y_val)}")
    
    # Train logistic regression classifier
    print("\nTraining Logistic Regression classifier...")
    clf = LogisticRegression(
        max_iter=1000,
        class_weight="balanced",  # Handle class imbalance
        random_state=42
    )
    clf.fit(X_train, y_train)
    
    # Evaluate on validation set
    print("\nEvaluating model...")
    y_pred = clf.predict(X_val)
    y_prob = clf.predict_proba(X_val)[:, 1]
    
    acc = accuracy_score(y_val, y_pred)
    auc = roc_auc_score(y_val, y_prob)
    
    # Save the model
    model_path = Path("models/dinov2_classifier.pkl")
    model_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(clf, model_path)
    
    # Print results
    print("\n" + "=" * 60)
    print("TRAINING COMPLETE")
    print("=" * 60)
    print(f"Validation Accuracy: {acc:.4f}")
    print(f"Validation AUC-ROC: {auc:.4f}")
    print(f"\nModel saved to: {model_path}")
    print("=" * 60)
    
    # Close HDF5 files
    img_file.close()
    label_file.close()
    
    return clf

if __name__ == "__main__":
    train_classifier()
