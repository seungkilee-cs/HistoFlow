import os
from PIL import Image

# Simple image loader for tiles.
class ImageLoader:
    @staticmethod
    def load_image(path: str) -> Image.Image:
        if not os.path.exists(path):
            raise FileNotFoundError(f"Image not found: {path}")

        return Image.open(path).convert("RGB")