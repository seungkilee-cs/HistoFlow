import os

# Create directory if it doesn't exist.
def create_dir(path: str):
    os.makedirs(path, exist_ok=True)
    return path