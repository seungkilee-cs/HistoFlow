# HistoFlow
Cancer detecting pathology scan AI tool, inspired by [Lunit](https://lunit.io/)

Here are the Phase 0 design and planning for the POC app.

## Technology

### DB

#### Primary Database: [PostgreSQL]() + [PostGIS]()
A reliable, open-source relational database with an extension for spatial data support. PostGIS indexes spatial attributes like cell coordinates, enabling the fast viewport-bound queries necessary for interactive zoom and pan. It is ideal for managing structured metadata like patient info, slide annotations, and running spatial queries on millions of cells efficiently.

#### Object Storage: [Amazon S3]() (or self-hosted alternatives)
Scalable and cost-efficient cloud object storage is used for the gigabyte-scale whole slide images, which are stored separately from the metadata database. The database stores references (file paths or URIs) to the image location in object storage to keep the database itself lean and fast.

#### Optional NoSQL Document Store: [MongoDB]()
This can be used to support flexible and evolving storage of complex annotation details or AI model outputs that do not easily conform to a fixed relational schema. For anything that handles this nature of data, there's likely no "one-size-fits-all" db solution.

### Backend
#### Primary Stack: [Kotlin](https://kotlinlang.org/) + [Spring Boot](https://spring.io/projects/spring-boot)

The Java ecosystem's stability and concurrency support is essential for distributed image processing of large files. We leverage the null safety of Kotlin, which is critical for medical device reliability, and its declarative functional programming capabilities for complex data processing.

#### Specialized ML Tasks: [Python](https://www.python.org/)
Python is used for complementary processing with the primary stack to handle GPU-accelerated computations for AI model inference, which Kotlin may not natively support. It is also used for other library integrations like OpenCV or scikit-image, and AI/ML pipeline components that require specialized libraries unavailable in the JVM ecosystem.

### Frontend

#### Core Framework: [ReactJS](https://react.dev/)	
The foundation for building the user interface. 

Note: Lunit's team has even developed custom solutions to better integrate other libraries with the React Lifecycle. This is interesting engineering, and we should investigate this in later iterations. This would probably mean a set of robust and efficient custom React renderer components to manage state handling and side effects. Let's brainstorm this as the POC wraps up.

#### High Res Image Visualization: [OpenSeadragon](https://openseadragon.github.io/)
A crucial library for viewing high-resolution zoomable images. It works by breaking down a large image into smaller tiles and only loading the ones currently in the user's viewport.

#### GPU Acceleration: [WebGL](https://www.khronos.org/webgl/)
A standard approach for performance-critical pixel manipulation tasks like Tissue Segmentation, where every pixel on the screen may need to be recolored. This offloads heavy computation from the CPU to the GPU for a smoother user experience. N

ote: This is part of the "make it fast" phase. Unless the OpenSeadragon visualizer is infeasibly slow, let's put this on the back burner for the POC.

## Architecture

The system is designed as a hybrid, distributed architecture to maximize scalability, reliability, and performance.

- The Frontend React application uses OpenSeadragon for tile-based large image visualization. It will use React's lifecycle and state management to efficiently render AI cell detection results as an overlay on the image viewer.
- The Backend Kotlin/Spring Boot API serves as the central orchestrator. It manages structured metadata, processes viewport queries using PostGIS spatial features, and serves the filtered data to the frontend.
- A Python microservice handles specialized tasks, including AI model inferencing and potentially preprocessing of image tiles. This leverages Python's strengths in machine learning libraries.
- Whole slide images reside in a dedicated, scalable object store like Amazon S3. They are served to the frontend either through a dedicated tile server or via presigned URLs.
- The entire system is containerized using Docker, and Kubernetes can be used to orchestrate the multicontainer deployment, ensuring that components are isolated and can be scaled independently.
- Infrastructure is managed as code using Terraform to automate cloud resource provisioning and deployment pipelines.

This architecture is modular, allowing for independent scaling of the metadata services, AI processing workloads, and the frontend visualization layers. This is essential for handling the large data sizes and computational demands of medical pathology scans.

## Development & Deployment

- Monorepo: A monorepo approach (using tools like [Nx]() or [Turborepo]()) enables shared UI components, types, and coordinated releases across the frontend and backend applications.
- CI/CD: CI/CD pipelines managed by a tool like GitHub Actions will automate builds, run tests, and handle deployments to staging and production environments.
- Monitoring: Logging and monitoring will be implemented using a stack like [Prometheus]() and [Grafana]() to ensure system health and track performance under heavy workloads.
- Process: We follow agile engineering practices with a focus on continuous improvement, comprehensive testing, and clear documentation.

## Wireframes

[Wireframes]()

## Design

[Design 00]()
