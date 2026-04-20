# HistoFlow

Cancer detection AI platform, inspired by [Lunit](https://www.lunit.io/).

## Quick Start

### Development Environment (All-in-One)

```bash
# Base stack — upload, tile, and view slides
./dev.sh

# Full stack — includes AI analysis (region-detector + model selection)
./dev.sh --ml

# Stop everything
./dev.sh --down
```

> **Note:** The cancer analysis feature (heatmap generation, classifier model selection) requires the `--ml` flag. Without it, the region-detector service does not start and the "Run Cancer Analysis" button will not produce results. First start with `--ml` is slow (~2-3 min) as the DINOv2 model loads into memory.

### Manual Setup

- Backend: [Manual Setup Guide](./docs/setup/backend/manual.kot.md)
- Docker: [In Progress]()
- Tile Generation: [Backend Scripts](./backend/scripts/README.md)

## Design

### Backend

Kotlin Spring Boot Backend Logic and API Desing [Here](./docs/design/backend.md)

### ML

Machine Learning Analytics Logic and API Design [Here](./docs/design/ml.md)

### Frontend
