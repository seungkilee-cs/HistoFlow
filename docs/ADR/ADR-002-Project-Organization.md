# ADR-002: Monorepo for Project Organization

Date: 2025-10-20
Status: Proposed

## Context

The HistoFlow project is composed of multiple, distinct components: a React+TypeScript frontend, a Kotlin+SpringBoot backend, and several Python microservices (tiling, classification, analysis). As we begin development, we must decide on a source control and repository strategy for organizing these components.
The decision is between housing each component in its own separate Git repository (multi-repo) or housing all components in a single, unified Git repository (monorepo).

## Decision

We will adopt a monorepo structure for the entire HistoFlow project. All source code for the frontend, backend, services, shared libraries, and documentation will reside in a single Git repository.
The proposed top-level directory structure will be:
```
histoflow/
├── frontend/
├── backend/
├── services/
│   ├── tiling/
│   ├── classification/
│   └── analysis/
├── docs/
└── libs/
    └── python-utils/
```
## Considered Options

### Option 1: Monorepo (Chosen)

A single Git repository containing all project code, organized into top-level directories for each application or service.

* Pros:
  * Simplified Dependency Management: A single place to manage versions and dependencies across the entire project, reducing versioning conflicts.
  * Enhanced Code Sharing and Reusability: Trivial to create and consume shared libraries (e.g., libs/python-utils for all Python services), which reduces code duplication and improves consistency.
  * Atomic Commits & Cross-Project Refactoring: Changes that span multiple services (e.g., updating an API contract in the backend and its client implementation in the frontend) can be made in a single, atomic commit. This simplifies large-scale refactoring.
  * Unified Tooling and CI/CD: We can establish a single, consistent set of tools for linting, testing, and building across the entire project.
  * Increased Visibility: Provides a holistic view of the entire system, making it easier for developers to collaborate and understand how different components interact.
* Cons:
  * Future Tooling Requirements: As the codebase grows, CI/CD pipelines can become slow. We will eventually need to adopt specialized monorepo tooling (e.g., Nx, Bazel, Turborepo) to enable smarter builds that only test/build the affected parts of the project.
  * Potential for Tight Coupling: If not managed with discipline, it can become easy to create undesirable dependencies between services.

### Option 2: Multi-repo

Each component (frontend, backend, tiling-service, etc.) would have its own separate Git repository.

* Pros:
  * Clear Ownership and Autonomy: Each repository has its own independent build, test, and deployment pipeline, giving teams complete autonomy.
  * Smaller Repository Size: Each repository is smaller and faster to clone.
* Cons:
  * Complex Dependency Management: Managing dependencies and shared code across multiple repositories is difficult. It often requires setting up private package registries and dealing with versioning challenges.
  * Difficult Cross-Project Changes: A single logical change might require creating coordinated pull requests across several repositories, making refactoring complex and error-prone.
  * Code Duplication: There is a higher tendency to duplicate common code (e.g., utility functions, data models) across repositories rather than creating and versioning a shared library.

## Rationale

For the current stage of our project, the advantages of a monorepo heavily outweigh the disadvantages. The ability to easily share code and perform atomic, cross-project changes will significantly accelerate our development velocity and improve code quality. It provides a more cohesive and simplified development experience for a team working closely together on all parts of the system.
We acknowledge the future need for specialized tooling as a manageable trade-off. By starting with a monorepo, we optimize for development speed and collaboration now, while keeping the path open to introduce advanced tooling as the project's scale demands it.
