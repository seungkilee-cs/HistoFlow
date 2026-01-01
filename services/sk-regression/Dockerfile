# Multi-stage build: builder -> runtime
# Supports both GPU (CUDA) and CPU environments

# ========================================
# STAGE 1: Builder (install dependencies)
# ========================================
FROM pytorch/pytorch:2.4.0-cuda12.4-cudnn9-devel AS builder-cuda

# Install system dependencies
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    git \
    h5py \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libglib2.0-0 \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies from requirements.txt
WORKDIR /tmp
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ========================================
# STAGE 2: Runtime (CUDA version)
# ========================================
FROM pytorch/pytorch:2.4.0-cuda12.4-cudnn9-runtime AS runtime-cuda

# Install minimal system dependencies
RUN apt-get update && apt-get install -y \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# Copy Python environment from builder
COPY --from=builder-cuda /usr/local/lib/python3.10 /usr/local/lib/python3.10
COPY --from=builder-cuda /usr/local/bin /usr/local/bin

# ========================================
# CPU Runtime (fallback)
# ========================================
FROM pytorch/pytorch:2.4.0-cpu AS runtime-cpu

RUN apt-get update && apt-get install -y \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder-cuda /usr/local/lib/python3.10 /usr/local/lib/python3.10
COPY --from=builder-cuda /usr/local/bin /usr/local/bin

# ========================================
# Final Application Image (CUDA)
# ========================================
FROM runtime-cuda AS pathology-classifier

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/app/.local/bin:$PATH"

# Create non-root user
RUN useradd --create-home --shell /bin/bash appuser && \
    mkdir -p /app/data/pcam /app/models /app/src && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser
WORKDIR /app

# Copy application code
COPY --chown=appuser:appuser src/ ./src/
COPY --chown=appuser:appuser *.py ./
COPY --chown=appuser:appuser requirements.txt ./

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import torch; print('OK')" || exit 1

# Default command
CMD ["python", "train_classifier.py"]

# ========================================
# CPU-friendly builder and final image for multi-arch (incl. Apple Silicon)
# ========================================
FROM python:3.12-slim AS builder-cpu

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

FROM python:3.12-slim AS pathology-classifier-cpu

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/app/.local/bin:$PATH"

RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user and workspace
RUN useradd --create-home --shell /bin/bash appuser && \
    mkdir -p /app/data/pcam /app/models /app/src && \
    chown -R appuser:appuser /app

USER appuser
WORKDIR /app

# Copy installed site-packages from builder
USER root
RUN python -c "import sys; print(sys.version)"
COPY --from=builder-cpu /usr/local/lib/python3.12 /usr/local/lib/python3.12
COPY --from=builder-cpu /usr/local/bin /usr/local/bin

# Copy application code
USER appuser
COPY --chown=appuser:appuser src/ ./src/
COPY --chown=appuser:appuser *.py ./
COPY --chown=appuser:appuser requirements.txt ./

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import torch; print('OK')" || exit 1

CMD ["python", "train_classifier.py"]
