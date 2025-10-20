# **ADR-003: Upload First Pattern for Image Transfer**

**Status:** Proposed  
**Date:** 2025-10-20

## **Abstract**

This document outlines the decision to adopt an **Upload First Pattern** for handling large file ingestion. Instead of streaming large files through our backend API, users will upload them directly to a dedicated object storage (MinIO) using a secure, pre-signed URL. The backend will then trigger a background processing job with a reference to the stored file. This choice prioritizes system resilience and fault tolerance over the seemingly lower latency of a direct stream. This approach decouples the main API from heavy data transfer, prevents data loss on processing failures, and allows for robust job retries.

## **Context**

The HistoFlow system must accept large histology image files (up to 20GB) from a user's browser. These files must then be processed by a background service to generate DZI tiles. A key architectural decision is how to transfer this large file from the user to the processing service reliably.  
Two primary patterns were considered:

1. **Direct Stream Processing:** The frontend streams the file through the main backend API directly to the tiling service.  
2. **Upload-First Pattern:** The frontend uploads the file directly to a durable storage location (MinIO). The backend then triggers a background job, providing the file's location to the processing service.

## **Decision**

We will implement the **Upload-First Pattern** for handling all large file processing jobs.

### **Workflow Details**

1. **Initiation and URL Generation:** When a user initiates an upload, the frontend makes a request to the backend API. The backend, which holds the secure MinIO credentials, generates a special **pre-signed URL**. This URL is time-limited, specific to a single file path, and grants temporary PUT (upload) permission only. It acts as a one-time key that allows a specific user to upload a specific file without ever having access to our main storage credentials.  
2. **Direct Upload to Storage:** The backend returns this pre-signed URL to the frontend. The frontend then uses this URL to upload the large file directly to a temporary bucket in our object storage (MinIO), completely bypassing the backend API.  
3. **Job Trigger:** Once the upload to MinIO is complete, the frontend notifies the backend.  
4. **Asynchronous Job Hand-off:** The backend sends a job message to the tiling service. This message is a small JSON payload containing a pointer to the file's location in MinIO, not the file itself.  
5. **Processing:** The tiling service receives the job message. It then acts as a client to MinIO, first downloading the file, processing it to generate tiles, and finally uploading the resulting tiles to a permanent bucket.

### **Data Flow Diagram**

The data flow for this pattern is decoupled and asynchronous:  
(1) GET /presigned-url  
\+--------------+     \+-------------+  
| User Browser | \--\> | Backend API |  
\+--------------+     \+-------------+  
      |      ^  
      |      | (2) Returns URL  
      v      |  
(3) PUT Large File to URL  
      |  
      v  
\+---------------+     \+-------------+  
| MinIO Storage | \<-- | User Browser| (4) POST /upload-complete  
\+---------------+     \+-------------+  
                            |  
                            | (5) Send Job Message  
                            v  
                      \+----------------+  
                      | Tiling Service |  
                      \+----------------+  
                            |       ^  
                            |       | (7) Upload Tiles  
      (6) Download File     v       |  
                      \+---------------+  
                      | MinIO Storage |  
                      \+---------------+

**Note on Diagram Arrows (Steps 6 and 7):** The arrows between the Tiling Service and MinIO represent the direction of **data flow**. The Tiling Service is the actor that **initiates** both requests:

* For step (6), it makes a GET request to MinIO, and data flows *from* storage.  
* For step (7), it makes PUT requests to MinIO, and data flows *to* storage.

## **Consequences**

### **Positive:**

* **High Resilience:** The complete, original file is secured in durable storage before any processing begins. This prevents data loss if any subsequent step fails.  
* **Fault Tolerance:** If the tiling service fails during its operation, the job can be retried without requiring the user to re-upload the file. The original file remains safe in storage.  
* **System Decoupling:** The main backend API is not involved in the high-bandwidth data transfer. It remains lightweight and responsive for other user requests, handling only quick, stateless API calls.  
* **Improved User Experience:** The user's interaction is finished once the initial upload is complete. They do not have to maintain an open connection during the long-running processing phase.

### **Negative:**

* **Increased Latency:** This pattern introduces one additional data transfer step (the tiling service downloading the file from MinIO). This is an acceptable trade-off for the significant gains in system reliability and robustness.

## **Alternatives Considered**

### **Direct Stream Processing**

This alternative involves the frontend sending the file as a stream to an endpoint on the main backend. The backend would then pipe this stream directly to the tiling service. This appears more direct as it avoids the initial "save-then-read" step.  
The data flow for this pattern is a single, long-lived chain:  
\+--------------+  (File Stream)  \+-------------+  (File Stream)  \+----------------+  
| User Browser | \--------------\> | Backend API | \--------------\> | Tiling Service |  
\+--------------+                 \+-------------+                 \+----------------+

This alternative was rejected due to the following critical weaknesses:

* **Fragility:** The process requires a persistent, unbroken connection from the user's browser, through the backend, to the tiling service for the entire duration of the multi-minute job. A failure at any point in this chain (e.g., user network issue, backend restart) causes the entire process to fail irrecoverably.  
* **No Retry Mechanism:** If the processing fails midway, the original data stream is lost. The only way to recover is for the user to start the entire upload from the beginning.  
* **Backend Bottleneck:** The main backend API would be occupied managing a few long-lived, high-bandwidth connections, degrading its performance and availability for all other users.