# HistoFlow System Design (ML): AI-Powered Histopathology Analysis

> Last Updated: 2025-09-28 (Sun)

- Image vector handling using python libraries
- ML-based cancer cell classification from python
- processing and relaying the analytics data to the Kotlin Spring Boot backend

## Python ML Service Design

Beyond the Image Processing, storage, and efficient backend handling via Kotlin and SPringboot, here is the ML model related features.

This part is the most "black box" of the whole part right now. The idea is the have FastAPI microservice layer on the python modules to communicate between Kotlin backend service logic.

We would try out different algorithms for the clasifier layer, including the common practices of Support Vector Machines (SVM), Convolutional Neural Networks (CNN), Random Forests (RF), Multi-Layer Perceptrons (MLP).

### Microservice Architecture

```python
# FastAPI ML Service Structure
├── main.py                    # FastAPI app entry point
├── models/
│   ├── cancer_classifier.py   # ML model wrapper
│   ├── preprocessing.py       # Image preprocessing
│   └── postprocessing.py      # Results formatting
├── services/
│   ├── analysis_service.py    # Core analysis logic
│   ├── model_loader.py        # Model management
│   └── image_service.py       # Image I/O operations
└── api/
    ├── analysis_routes.py     # Analysis endpoints
    └── health_routes.py       # Health checks
```

### ML Service API

Preliminary design of counterpart routes to the Kotlin Backend API

```python
# FastAPI Endpoints
POST /ml/v1/analyze
{
  "image_id": "uuid",
  "image_path": "s3://bucket/path",
  "analysis_type": "cancer_classification",
  "confidence_threshold": 0.85
}

GET /ml/v1/analysis/{analysis_id}/status
GET /ml/v1/analysis/{analysis_id}/results
```

### Analysis Pipeline

This will be not the actual model training but the logic for using the trained models to generate output for the classification and analysis.

```python
class CancerClassificationService:
    async def analyze_image(self, image_path: str) -> AnalysisResult:
        # 1. Load and preprocess image patches
        # 2. Run inference on trained model
        # 3. Apply confidence thresholding
        # 4. Generate heatmap/annotations
        # 5. Calculate statistics (cancer %, confidence scores)
        # 6. Return structured results
```
