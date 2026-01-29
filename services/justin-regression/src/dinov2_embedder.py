import torch
from PIL import Image
import numpy as np
from transformers import AutoImageProcessor, AutoModel
from typing import List

class DinoV2Embedder:
    # Loads DINOv2 model and generates embeddings for image tiles.

    def __init__(self, model_name: str = "facebook/dinov2-base"):
        self.device = "cuda" if torch.cuda.is_available() else "cpu"

        print(f"[DINOv2] Loading model: {model_name}")
        # Resize and normalize image for DINOv2
        self.processor = AutoImageProcessor.from_pretrained(model_name)
        self.model = AutoModel.from_pretrained(model_name).to(self.device)
        self.model.eval() # Dropout, etc. disabled for inference
        # Alternative .train() would enable dropout for uncertainty estimation

    def embed_image(self, image: Image.Image) -> np.ndarray:
        # Accepts a PIL Image and returns a PyTorch tensor 
        inputs = self.processor(images=image, return_tensors="pt").to(self.device)

        with torch.no_grad():
            outputs = self.model(**inputs)

        # DINOv2: outputs.last_hidden_state[:,0] is the CLS token
        # CLS token is the representative vector for the entire image
        embedding = outputs.last_hidden_state[:, 0].cpu().numpy().flatten() # return numpy array
        
        return embedding

    # Handling images in a batch for efficiency
    # Accepts a list of images and returns an array of embeddings.
    # Processes images in batches for speed.
    # return (num_images, embedding_dim)
    def embed_images(self, images: List[Image.Image], batch_size: int = 16):
        """
        Accepts a list of PIL images and returns an array of embeddings.
        Processes images in batches for speed.
        Returns shape: (num_images, embedding_dim)
        """
        all_embeddings = []

        for i in range(0, len(images), batch_size):
            batch = images[i: i + batch_size]

            # Preprocess batch. DINO processor supports batch input
            inputs = self.processor(images=batch, return_tensors="pt").to(self.device)

            with torch.no_grad():
                outputs = self.model(**inputs)

            # CLS token from each image in batch to shape 
            batch_embeddings = outputs.last_hidden_state[:, 0].cpu().numpy()

            all_embeddings.append(batch_embeddings)

        # Combine all batches into single tensor
        all_embeddings = np.vstack(all_embeddings)

        return all_embeddings
