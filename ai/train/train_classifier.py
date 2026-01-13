import os
import numpy as np

from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, roc_auc_score

EMBEDDINGS_DIR = "ai/embedded_data"

def load_all_embeddings(emb_dir="ai/embedded_data"):
    x_list = []
    y_list = []

    # Get all embedding and label files
    for fname in sorted(os.listdir(emb_dir)):
        if fname.startswith("embeddings"):
            idx = fname.split("_")[-1].split(".")[0]
            x = np.load(os.path.join(emb_dir, f"embeddings_{idx}.npy"))
            y = np.load(os.path.join(emb_dir, f"labels_{idx}.npy"))

            x_list.append(x)
            y_list.append(y)

    # Concanate batches vertically
    x_all = np.vstack(x_list)   # (N, 768)
    y_all = np.concatenate(y_list)  # (N,)

    return x_all, y_all

def main():
    x, y = load_all_embeddings(EMBEDDINGS_DIR)

    print(f"Total samples: {len(y)}")
    print(f"Embedding shape: {x.shape}")

    x_train, x_val, y_train, y_val = train_test_split(
        x,
        y,
        # 20% for validation and 80% for training
        test_size=0.2,
        # This I am not sure but AI helped with this number
        random_state=42,
        stratify=y
    )

    clf = LogisticRegression(
        max_iter=1000,
        class_weight="balanced"
    )
    clf.fit(x_train, y_train)

    y_pred = clf.predict(x_val)
    y_prob = clf.predict_proba(x_val)[:, 1]
    acc = accuracy_score(y_val, y_pred)
    auc = roc_auc_score(y_val, y_prob)

    print("Results")
    print(f"Accuracy: {acc:.3f}")
    print(f"AUC: {auc:.3f}")


if __name__ == "__main__":
    main()