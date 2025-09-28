# HistoFlow System Design (Backend): AI-Powered Histopathology Analysis

> Last Updated: 2025-09-28 (Sun)

- large medical image uploads and handling, relaying of the parsed/analytics data.
- Communication with ML-based cancer cell classification from Python

## Core Features and User Workflow

### Primary Features

1. Large Image Upload and Processing - Handle gigapixel histopathology images (typically 100MB-2GB)
2. ML Classification - Distinguish cancer cells from normal cells using ML models
3. Interactive Image Viewing - Deep zoom navigation with OpenSeaDragon
4. Annotation and Results Overlay - Visual indicators of detected cancer regions
5. Analysis History - Track previous analyses and results
6. Export and Reporting - Generate diagnostic reports

### User Workflow

1. Upload histopathology image → 2. View upload progress → 3. Image processing and tiling → 4. ML analysis execution → 5. Interactive results viewing → 6. Export findings

## High-Level Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Frontend      │    │    Backend       │    │   ML Service    │
│  React + TS +   │◄──►│ Kotlin Spring    │◄──►│    Python       │
│  OpenSeaDragon  │    │     Boot         │    │   FastAPI       │
│  (Image Upload) │    │(Image processing)│    │   (ML Models)   │
│  (Image Zoom)   │    │ (Storage/Relay)  │    │   (tile process)│
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │   File Storage   │
                       │ (AWS S3/MinIO)   │
                       │ (In Production)  │
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │    Database      │
                       │   PostgreSQL     │
                       └──────────────────┘
```

## Backend Design (Kotlin Spring Boot)

### Core Services Architecture

```kotlin
// Domain Structure
├── controller/
│   ├── ImageController.kt          // upload, retrieve operations
│   ├── AnalysisController.kt       // ML analysis management
│   └── ReportController.kt         // results and reporting
├── service/
│   ├── ImageProcessingService.kt   // image tiling, optimization
│   ├── AnalysisService.kt          // ML workflow orchestration
│   ├── FileStorageService.kt       // S3/MinIO integration (For Production)
│   └── NotificationService.kt      // WebSocket updates
├── repository/
│   ├── ImageRepository.kt
│   ├── AnalysisRepository.kt
│   └── UserRepository.kt
├── model/
│   ├── Image.kt
│   ├── Analysis.kt
│   └── AnalysisResult.kt
└── config/
    ├── StorageConfig.kt
    ├── WebSocketConfig.kt
    └── SecurityConfig.kt
```

### Key Backend Components

#### 1. Image Upload and Processing Service

After getting the large image file from the frontend, there should be a service that handles logic of image processing.

```kotlin
@Service
class ImageProcessingService {
    fun processLargeImage(file: MultipartFile): ImageProcessingResult {
        // 1. Validate file (format, size, medical metadata)
        // 2. Generate unique ID and storage path
        // 3. Stream upload to object storage
        // 4. Create image tiles for OpenSeaDragon
        // 5. Extract metadata (dimensions, magnification)
        // 6. Queue for ML classification
    }
}
```

#### 2. ML Classification Orchestration

After processing the image file, there should be some way to communicate with Python ML models.

Note: Later, this may evolve beyond simple classification of cancer cells vs normal tissues, so the overall analytics should be modular and flexible.

```kotlin
@Service
class AnalysisService {
    fun initiateAnalysis(imageId: String): Analysis {
        // 1. Call Python ML service via (by HTTP or gRPC)
        // 2. Monitor analysis progress -> display logic needs further investigation
        // 3. Handle results and store findings into the target (S3 bucket in the production)
        // 4. Send real-time updates to the frontend via WebSocket
    }
}
```

#### 3. REST API Design

Some basic API design for the routes, can and will change.

```kotlin
// Image Management
POST   /api/v1/images/upload          // Chunked upload support
GET    /api/v1/images/{id}            // Image metadata
GET    /api/v1/images/{id}/tiles/{z}/{x}/{y} // image tile serving
DELETE /api/v1/images/{id}

// Analysis Management
POST   /api/v1/analysis/start/{imageId}  // Trigger ML analysis
GET    /api/v1/analysis/{id}/status      // Analysis progress
GET    /api/v1/analysis/{id}/results     // Classification results
POST   /api/v1/analysis/{id}/export      // Export report

// Real-time Updates
WebSocket /ws/analysis/{analysisId}      // Progress updates
```
