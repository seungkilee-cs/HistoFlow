# ADR-001: Microservice for Image Tiling

Date: 2025-10-20  
Status: Proposed

## Context

The HistoFlow platform must process extremely large whole-slide images (WSI), often exceeding 20GB. A core requirement is to convert these images into a tiled format (DZI) for efficient viewing in the frontend via OpenSeadragon. This tiling process is computationally intensive, consuming significant CPU and memory resources.  
Our primary backend API is built with Kotlin and Spring Boot. The fundamental architectural question is whether this intensive image processing logic should be integrated directly into the Kotlin application or be handled by a separate, specialized service.

## Decision

We will implement all WSI tiling and processing logic in a dedicated, standalone Python microservice.  
The main Kotlin backend will act as an orchestrator. It will handle the initial file upload handshake (e.g., generating pre-signed URLs for direct-to-storage upload), manage metadata in the database, and trigger the Python service to begin processing via a network call (e.g., REST API or a message queue). The Python service will then perform the tiling and upload the results directly to object storage (MinIO/S3).

## Considered Options

### Option 1: Dedicated Python Service (Chosen)

A separate microservice written in Python, using libraries like pyvips and minio. It would expose an internal API to be called by the Kotlin backend to start a processing job.

* Pros:  
  * Superior Ecosystem: Python has a strong ecosystem for not only the tiling task, but overall scientific and medical image processing (pyvips, OpenSlide, scikit-image, etc.). This accelerates development and provides more robust tooling.  
  * Resource Isolation: The intense CPU and memory load of tiling is isolated from the main API. A demanding tiling job will not slow down or crash the user-facing backend, ensuring API stability and responsiveness.  
  * Independent and Efficient Scalability: The resource profile of the main API (mostly I/O-bound) is fundamentally different from the tiling service (CPU and memory-bound). By separating them, we can "right-size" the infrastructure for each. The API can run on many small, cheap instances, while the tiling service can run on a few powerful, memory-optimized instances. This prevents paying for expensive, oversized monolithic instances and is significantly more cost-effective.  
  * Architectural Consistency: This aligns with our plan to use Python for ML services (classification, analysis). It establishes a clear pattern: Kotlin for orchestration and business logic, Python for heavy computation and data science.  
* Cons:  
  * Increased Architectural Complexity: Introduces an additional service to build, deploy, and monitor.  
  * Operational Overhead: Requires a separate CI/CD pipeline, containerization (Docker), and inter-service communication management.

### Option 2: Integrated Kotlin/Java Library

Integrate a Java library that wraps libvips (e.g., libvips-java) directly into our Spring Boot application. The tiling logic would exist within a service bean inside the main backend.

* Pros:  
  * Architectural Simplicity: A single codebase and a single deployable artifact. Easier to manage initially.  
  * No Network Overhead: The logic is a local function call, avoiding inter-service network latency.  
* Cons:  
  * Resource Contention: A large tiling job could consume all the server's resources, making the entire HistoFlow API unresponsive or causing it to fail with OutOfMemoryErrors. This is a significant risk to system stability.  
  * Weaker Ecosystem: Java wrappers for C libraries can be less ergonomic, lag behind in features, and have smaller support communities compared to their first-class Python counterparts.  
  * Inefficient Monolithic Scaling: To handle a higher load of tiling jobs, we would be forced to scale horizontally by creating complete, identical copies of the entire backend application. Each new instance would carry the full overhead of the API, business logic, and database connections, even if we only need more computational power for tiling. This leads to significant resource waste and higher operational costs.

## Rationale

The stability and scalability of our core API are paramount. The risk of resource contention from Option 2 presents an unacceptable threat to the user experience.  
Furthermore, leveraging the best-in-class Python imaging ecosystem allows us to build a more powerful and maintainable processing pipeline. The benefits of resource isolation, independent and cost-effective scaling, and using the right tool for the job far outweigh the manageable increase in operational complexity. This decision establishes a robust and scalable foundation for all future computational services.