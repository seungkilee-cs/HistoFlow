import os
import numpy as np

OUTPUT_DIR = "ai/embedded_data"

os.makedirs(OUTPUT_DIR, exist_ok=True)

x = np.load(f"{OUTPUT_DIR}/embeddings_000.npy")
y = np.load(f"{OUTPUT_DIR}/labels_000.npy")


print(x.shape)
print(y.shape)

print(x[0][:10])
print(y[:10])
