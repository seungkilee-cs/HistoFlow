# justin-regression

A histology patch classifier that determines whether a tissue patch is **Tumor** or **Normal**. It uses DINOv2 as a feature extractor and supports two interchangeable classification heads: Logistic Regression and SVM.

---

## How It Works

Classification is a two-stage pipeline:

```
Input image (96x96 patch)
        ↓
DINOv2 feature extractor
        ↓
768-dimensional embedding vector
        ↓
Classification head (Logistic Regression or SVM)
        ↓
Tumor / Normal + probability score
```

**Stage 1 — Embedding:** The image is fed through DINOv2, a large Vision Transformer pretrained by Meta. The model's CLS token output is used as a compact 768-number representation of the image's content.

**Stage 2 — Classification:** The embedding is passed to sklearn classifier that was trained on top of DINOv2 embeddings from the PatchCamelyon (PCam) dataset.

---

## Dataset

Training uses the [PatchCamelyon](https://github.com/basveeling/pcam) dataset, showing 96x96 pixel patches cropped from whole slide histology images, each labeled Tumor or Normal. The full dataset contains 262,144 training samples, and we used 10,000 of them for experimentation as of now.

---

## Feature Extractor: DINOv2 vs ResNet

The other service in this repo (`sk-regression`) uses **ResNet18** as its feature extractor. This service uses **DINOv2**. They represent two different approaches to the same problem.

### ResNet18 
- **Convolutional Neural Network (CNN)**
- Trained with supervised learning on ImageNet. It learned features by being explicitly told what class each image belongs to
- Extracts local spatial features by sliding convolutional filters across the image
- Fast and well-understood. A strong baseline for image classification tasks

### DINOv2 
- **Vision Transformer (ViT)**
- Trained with self-supervised learning, so no human labels were used during pre-training. It learned by predicting its own outputs across different views of the same image
- Processes the image as a sequence of patches (like words in a sentence) and uses attention to relate them globally
- The CLS token, or a special summary token, is used as the embedding, capturing the image's global semantics
- Produces richer, more transferable representations, especially for specialized domains like medical imaging that look nothing like everyday photos

### Why both?
Using different feature extractors lets us compare approaches. ResNet is a proven CNN baseline. DINOv2's self-supervised pretraining means it was never biased toward ImageNet categories, which can make its features generalise better to histology images. In practice, DINOv2 embeddings tend to produce better downstream classification accuracy for medical imaging tasks.

### DINOv2 and whole slide images
An advantage of DINOv2 over ResNet is how Vision Transformers (ViT) handle image size. ResNet CNNs are designed around a fixed input size — to analyse a whole slide you are forced to chop it into tiles and classify each one independently, with no tile aware of its neighbours.

DINOv2 has no hardcoded assumption about image size. It works by splitting whatever image it receives into a grid of small patches and running attention across all of them. This means it can naturally scale to much larger image regions. More importantly, the attention weights the model produces can be visualised directly as a heatmap. The regions the model paid attention to tend to correspond to where tumour tissue actually is, without needing to classify tile by tile.

This makes DINOv2 a stronger long-term foundation for the heatmap feature in HistoFlow. The current implementation classifies each tile independently, but the architecture leaves open the possibility of feeding larger slide regions directly to DINOv2 and using its attention maps to drive the heatmap, rather than assembling it from individual tile predictions.

---

## Classification Head: Logistic Regression vs SVM

Both classifiers sit on top of the DINOv2 embeddings and are trained with the same data. The only difference is how they draw the decision boundary between Tumor and Normal in the 768-dimensional embedding space.

### Logistic Regression
- Fits a linear decision boundary
- Fast to train, easy to interpret
- Works well when the two classes are already well-separated in embedding space — which DINOv2 often achieves
- Saved to `models/dinov2_classifier.pkl`

### SVM with RBF Kernel (Support Vector Machine)
- Fits a non-linear decision boundary using the Radial Basis Function (RBF) kernel
- Finds the hyperplane that maximises the margin between the two classes
- Can capture more complex patterns that a linear boundary would miss
- Slower to train than logistic regression but can achieve better accuracy on harder separations
- Saved to `models/dinov2_svm.pkl`

### Which to use?
Check the validation metrics printed after training. If AUC-ROC scores are similar, logistic regression is preferable for its simplicity. If the SVM scores noticeably higher, use that. Both models expose the same `predict_proba()` interface so inference code does not need to change — just point `--model` at whichever `.pkl` you want.

---

## Training

Both scripts load from the PCam HDF5 files in `data/pcam/`, generate DINOv2 embeddings in batches, train on an 80/20 split, and save the model to `models/`.

```bash
cd services/justin-regression

# Train logistic regression
python src/train.py

# Train SVM
python src/train_svm.py
```

---

## Inference

```bash
python -m src.main \
  --model models/dinov2_svm.pkl \
  --images minio://bucket/patch.png \
  --minio-endpoint localhost:9000 \
  --minio-access-key minioadmin \
  --minio-secret-key minioadmin
```

Pass `--save-jsonl results.jsonl` to persist output to a file.

### Output schema

```json
{
  "image": "minio://bucket/patch.png",
  "classification": {
    "label": "Tumor",
    "threshold": 0.5,
    "probabilities": {
      "Normal": 0.23,
      "Tumor": 0.77
    }
  },
  "score": {
    "score": 0.77,
    "raw_score": 0.77
  },
  "runtime": {
    "inference_ms": 412.3,
    "device": "cpu"
  }
}
```

If a tile fails after one retry, it is still included in the output with an error flag rather than silently dropped:

```json
{
  "image": "minio://bucket/patch.png",
  "error": true,
  "reason": "..."
}
```

---

## Models

| File | Classifier | Val Accuracy | Val AUC-ROC |
|------|-----------|-------------|-------------|
| `models/dinov2_classifier.pkl` | Logistic Regression | — | — |
| `models/dinov2_svm.pkl` | SVM (RBF) | 90.05% | 96.13% |
