# **ADR-004: Upload Completion Notification and Automatic Tiling Trigger**

**Status:** Proposed  
**Date:** 2025-10-29

## **Abstract**

This ADR defines the mechanism for notifying the backend when a frontend upload completes and automatically triggering the tiling microservice. It builds on ADR-003's upload-first pattern by closing the loop: once the user uploads a file to MinIO via presigned URL, the frontend signals completion to the backend, which then initiates the tiling job and eventually notifies the frontend of processing status.

## **Context**

Currently, the frontend uploads files directly to MinIO using presigned URLs obtained from `/api/v1/uploads/initiate`. After the upload completes, the frontend calls `POST /api/v1/uploads/complete` with `{ objectName }` @frontend/src/components/FileUpload.example.tsx#77-79. However, this endpoint does not exist in the backend @backend/src/main/kotlin/com/histoflow/backend/controller/UploadController.kt, so the notification fails silently.

We need to:
1. Implement the `/uploads/complete` endpoint to receive upload completion signals.
2. Automatically trigger the tiling microservice when an upload completes.
3. Provide a mechanism for the frontend to learn when tiling finishes (polling, webhooks, or WebSocket).

## **Current State Analysis**

### **Frontend Upload Flow**
The `FileUpload.example.tsx` component:
1. Requests a presigned URL from `POST /api/v1/uploads/initiate` with `{ fileName, contentType, datasetName? }`.
2. Receives `{ uploadUrl, objectName, imageId, datasetName }`.
3. Uploads the file directly to MinIO via `PUT uploadUrl`.
4. Calls `POST /api/v1/uploads/complete` with `{ objectName }` to notify the backend.
5. Sets `uploadStatus` to `"success"` and displays the `imageId`, `objectName`, and `datasetName`.

### **Backend Upload Controller**
The `UploadController` currently only implements:
- `POST /api/v1/uploads/initiate` — generates presigned URLs and returns metadata.

**Missing:** The `/uploads/complete` endpoint that the frontend expects.

### **Tiling Service**
The tiling microservice exposes `POST /jobs/tile-image` accepting:
```json
{
  "image_id": "string",
  "source_bucket": "string",
  "source_object_name": "string",
  "dataset_name": "string?"
}
```
It processes the job asynchronously and logs detailed timing/metadata.

## **Decision**

We will implement a **synchronous trigger with asynchronous processing** pattern:

1. **Backend receives completion signal:** Implement `POST /api/v1/uploads/complete` in `UploadController`.
2. **Backend triggers tiling immediately:** Upon receiving the completion signal, the backend synchronously calls the tiling service's `POST /jobs/tile-image` endpoint.
3. **Frontend polls for status:** The frontend periodically polls a new `GET /api/v1/tiles/{imageId}/status` endpoint to check tiling progress.

### **Detailed Workflow**

```
┌──────────────┐
│ User Browser │
└──────┬───────┘
       │ 1. POST /uploads/initiate
       │    { fileName, contentType, datasetName? }
       ▼
┌─────────────┐
│   Backend   │
└──────┬──────┘
       │ 2. Generate presigned URL
       │    Return { uploadUrl, objectName, imageId, datasetName }
       ▼
┌──────────────┐
│ User Browser │
└──────┬───────┘
       │ 3. PUT uploadUrl (direct to MinIO)
       │    [Large file transfer]
       ▼
┌─────────────┐
│    MinIO    │
└─────────────┘
       │
       │ 4. Upload complete
       ▼
┌──────────────┐
│ User Browser │
└──────┬───────┘
       │ 5. POST /uploads/complete
       │    { objectName, imageId, datasetName }
       ▼
┌─────────────┐
│   Backend   │──────┐
└─────────────┘      │ 6. POST /jobs/tile-image to tiling service
                     │    { image_id, source_bucket, source_object_name, dataset_name }
                     ▼
              ┌──────────────┐
              │Tiling Service│
              └──────┬───────┘
                     │ 7. Download, tile, upload
                     │    (async background task)
                     ▼
              ┌─────────────┐
              │    MinIO    │
              └─────────────┘
                     │
                     │ 8. Tiles ready
                     ▼
┌──────────────┐
│ User Browser │──────┐
└──────────────┘      │ 9. Poll GET /tiles/{imageId}/status
                      │    Returns { status: "processing" | "completed" | "failed" }
                      ▼
              ┌─────────────┐
              │   Backend   │
              └─────────────┘
```

## **Implementation Requirements**

### **1. Backend: Upload Completion Endpoint**

Create `POST /api/v1/uploads/complete` in `UploadController`:

```kotlin
data class CompleteUploadRequest(
    val objectName: String,
    val imageId: String? = null,
    val datasetName: String? = null
)

data class CompleteUploadResponse(
    val status: String,
    val imageId: String,
    val message: String
)

@PostMapping("/complete")
fun completeUpload(@RequestBody request: CompleteUploadRequest): ResponseEntity<CompleteUploadResponse> {
    try {
        // Extract imageId from objectName if not provided
        val imageId = request.imageId ?: request.objectName.substringBefore('/')
        val datasetName = request.datasetName
        
        // Trigger tiling service
        val tilingPayload = mapOf(
            "image_id" to imageId,
            "source_bucket" to "unprocessed-slides",
            "source_object_name" to request.objectName,
            "dataset_name" to datasetName
        )
        
        // Call tiling service (synchronous HTTP call)
        val tilingUrl = System.getenv("TILING_SERVICE_URL") ?: "http://tiling:8000"
        restTemplate.postForEntity(
            "$tilingUrl/jobs/tile-image",
            tilingPayload,
            String::class.java
        )
        
        // Store job status in database (optional, for polling)
        // jobRepository.save(TilingJob(imageId, "processing", Instant.now()))
        
        return ResponseEntity.ok(CompleteUploadResponse(
            status = "accepted",
            imageId = imageId,
            message = "Tiling job initiated"
        ))
    } catch (e: Exception) {
        println("Error triggering tiling job: ${e.message}")
        return ResponseEntity.status(500).body(CompleteUploadResponse(
            status = "error",
            imageId = "",
            message = "Failed to initiate tiling"
        ))
    }
}
```

**Dependencies:**
- Inject `RestTemplate` or use Spring's `WebClient` for HTTP calls to the tiling service.
- Configure `TILING_SERVICE_URL` environment variable (default: `http://tiling:8000` in Docker).

### **2. Backend: Status Polling Endpoint**

Create `GET /api/v1/tiles/{imageId}/status`:

```kotlin
data class TilingStatusResponse(
    val imageId: String,
    val status: String, // "processing", "completed", "failed"
    val message: String? = null
)

@GetMapping("/{imageId}/status")
fun getTilingStatus(@PathVariable imageId: String): ResponseEntity<TilingStatusResponse> {
    // Check if DZI descriptor exists in MinIO
    val dziExists = try {
        s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(minioProps.buckets.tiles)
                .key("$imageId/image.dzi")
                .build()
        )
        true
    } catch (e: NoSuchKeyException) {
        false
    }
    
    return if (dziExists) {
        ResponseEntity.ok(TilingStatusResponse(
            imageId = imageId,
            status = "completed",
            message = "Tiles are ready"
        ))
    } else {
        ResponseEntity.ok(TilingStatusResponse(
            imageId = imageId,
            status = "processing",
            message = "Tiling in progress"
        ))
    }
}
```

**Note:** This is a simple implementation that checks for tile existence. For production, consider storing job status in a database or using the tiling service's metadata.json.

### **3. Frontend: Update Upload Component**

Modify `FileUpload.example.tsx` to:
1. Pass `imageId` and `datasetName` to `/uploads/complete`.
2. Poll `/tiles/{imageId}/status` after upload completes.
3. Display tiling progress to the user.

```typescript
// After MinIO upload completes
await axios.post("/api/v1/uploads/complete", {
  objectName,
  imageId,
  datasetName: resolvedName
});

setUploadStatus("success");

// Start polling for tiling status
const pollInterval = setInterval(async () => {
  try {
    const statusRes = await axios.get(`/api/v1/tiles/${imageId}/status`);
    if (statusRes.data.status === "completed") {
      clearInterval(pollInterval);
      setTilingStatus("completed");
      console.log("Tiling complete!");
    } else if (statusRes.data.status === "failed") {
      clearInterval(pollInterval);
      setTilingStatus("failed");
    }
  } catch (error) {
    console.error("Error polling tiling status:", error);
  }
}, 5000); // Poll every 5 seconds
```

## **Alternative Strategies**

### **Option A: Webhook Callback (Push-based)**
The tiling service calls a backend webhook when processing completes. The backend then notifies the frontend via WebSocket or stores the status for polling.

**Pros:**
- Immediate notification, no polling overhead.
- Scalable for many concurrent jobs.

**Cons:**
- Requires WebSocket infrastructure or long-polling.
- More complex to implement and test.

### **Option B: Message Queue (Event-driven)**
Use a message broker (RabbitMQ, Kafka) to decouple upload completion from tiling trigger. The backend publishes an "upload complete" event, and the tiling service subscribes to it.

**Pros:**
- Highly decoupled and scalable.
- Natural fit for microservices architecture.

**Cons:**
- Adds infrastructure complexity (message broker).
- Overkill for current scale.

### **Option C: MinIO Event Notifications**
Configure MinIO to send S3 event notifications (e.g., `s3:ObjectCreated:Put`) to a webhook or queue when objects are uploaded to `unprocessed-slides`.

**Pros:**
- No frontend involvement in triggering tiling.
- Fully automated and decoupled.

**Cons:**
- Requires MinIO event configuration.
- Harder to pass custom metadata (datasetName) through events.

## **Chosen Strategy: Synchronous Trigger + Polling**

We chose **synchronous trigger with polling** because:
1. **Simplicity:** No additional infrastructure (message queues, WebSockets).
2. **Immediate feedback:** The backend confirms tiling job acceptance instantly.
3. **Sufficient for current scale:** Polling every 5 seconds is acceptable for a small number of concurrent users.
4. **Easy to upgrade:** Can migrate to webhooks/WebSockets later without changing the upload flow.

## **Consequences**

### **Positive**
- **Complete workflow:** Frontend → Backend → Tiling → Frontend status update.
- **User visibility:** Users see when tiling starts and completes.
- **Resilient:** If tiling fails, the backend can retry or log errors.
- **Simple to implement:** Minimal changes to existing codebase.

### **Negative**
- **Polling overhead:** Frontend makes periodic requests even when tiling is slow.
- **Backend dependency:** Tiling service must be reachable from backend (Docker network or service discovery).
- **No real-time updates:** 5-second polling delay before frontend sees completion.

### **Future Improvements**
1. Replace polling with WebSocket for real-time updates.
2. Store tiling job status in a database for better tracking.
3. Add retry logic if tiling service is unavailable.
4. Implement MinIO event notifications for fully automated triggering.

## **Related ADRs**
- **ADR-003:** Upload-First Pattern (defines the presigned URL workflow).
- **ADR-001:** Tiling Microservice (defines the tiling service API).

## **References**
- Frontend upload component: `frontend/src/components/FileUpload.example.tsx`
- Backend upload controller: `backend/src/main/kotlin/com/histoflow/backend/controller/UploadController.kt`
- Tiling service API: `services/tiling/src/main.py`
