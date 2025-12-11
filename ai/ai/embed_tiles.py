import os
import numpy as np

from ai.embed.dinov2_embedder import DinoV2Embedder
from ai.data.image_loader import ImageLoader
from ai.utils.paths import create_dir


TILES_DIR = "tiles/"         
OUTPUT_DIR = "embeddings/"   

# Turn all tiles in TILES_DIR into DINOv2 embeddings saved in OUTPUT_DIR
def embed_all_tiles():
    create_dir(OUTPUT_DIR)

    embedder = DinoV2Embedder()

    for filename in os.listdir(TILES_DIR):
        if not filename.lower().endswith((".png", ".jpg", ".jpeg")):
            continue

        tile_path = os.path.join(TILES_DIR, filename)
        image = ImageLoader.load_image(tile_path)

        embedding = embedder.embed_image(image)

        outpath = os.path.join(OUTPUT_DIR, filename.replace(".png", ".npy"))
        np.save(outpath, embedding)

        print(f"[OK] Embedded {filename} → {outpath}")


if __name__ == "__main__":
    embed_all_tiles()
