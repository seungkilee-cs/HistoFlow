import os
import numpy as np
from datasets import load_dataset
from ai.embed.dinov2_embedder import DinoV2Embedder

OUTPUT_DIR = "ai/embedded_data"
os.makedirs(OUTPUT_DIR, exist_ok=True)

BATCH_SIZE = 128
MAX_SAMPLES = 5000 # TODO USE THIS FOR QUICK TESTING

dataset = load_dataset(
    "1aurent/PatchCamelyon",
    split="train",
    streaming=True # Does not download the dataset locally
)

embedder = DinoV2Embedder()

x_batch = []
y_batch = []

batch_idx = 0
sample_count = 0

for sample in dataset:
    image = sample["image"]
    label = sample["label"]

    embedding = embedder.embed_image(image)

    x_batch.append(embedding)
    y_batch.append(label)

    sample_count += 1

    if sample_count >= MAX_SAMPLES:
        break

    # Save if batch is full
    if len(x_batch) == BATCH_SIZE:
        # Use stack in case batches are different lengths. For machine learning we need shape to be (N,D) exactly witout any D being different
        x_array = np.stack(x_batch)
        y_array = np.array(y_batch)

        np.save(f"{OUTPUT_DIR}/embeddings_{batch_idx:03d}.npy", x_array)
        np.save(f"{OUTPUT_DIR}/labels_{batch_idx:03d}.npy", y_array)

        print(f"[SAVED] Batch {batch_idx} ({len(x_batch)} samples)")

        # Clear batch and increment index
        x_batch = []
        y_batch = []
        batch_idx += 1
    
# Save if reached no more samples
if x_batch:
    print(sample["image"])
    print(x_batch)
    x_array = np.stack(x_batch)
    y_array = np.array(y_batch)

    np.save(f"{OUTPUT_DIR}/embeddings_{batch_idx:03d}.npy", x_array)
    np.save(f"{OUTPUT_DIR}/labels_{batch_idx:03d}.npy", y_array)

    print(f"[SAVED] Final batch {batch_idx} ({len(x_batch)} samples)")
