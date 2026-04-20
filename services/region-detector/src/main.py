"""FastAPI application — HTTP interface for the region-detector service.

Endpoints
---------
POST /jobs/analyze      Submit a new region-detection job (runs in background)
GET  /jobs/{id}/status  Poll job progress
GET  /jobs/{id}/results Get full results (tile predictions + summary + heatmap)
GET  /health            Health-check
"""

from __future__ import annotations

import json
import threading
import traceback
import uuid
from enum import Enum
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib import error, request

from fastapi import BackgroundTasks, FastAPI, HTTPException
from pydantic import BaseModel

from .config import settings
from .minio_io import download_json
from .pipeline import preload_models, run_analysis

# ── App ───────────────────────────────────────────────────────────────────────

app = FastAPI(
    title="HistoFlow Region Detector",
    description="Tile-level tumour probability analysis with heatmap overlay generation.",
    version="0.2.0",
)


@app.on_event("startup")
async def startup_event() -> None:
    """Pre-load ML models so the first analysis job starts immediately."""
    print("[startup] Pre-loading ML models into memory…")
    preload_models()
    print("[startup] Models ready.")


# ── In-memory job store ───────────────────────────────────────────────────────


class JobStatus(str, Enum):
    ACCEPTED = "accepted"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class JobState:
    def __init__(
        self,
        job_id: str,
        image_id: str,
        tile_level: int,
        threshold: float,
        tissue_threshold: float | None,
    ):
        self.job_id = job_id
        self.image_id = image_id
        self.tile_level = tile_level
        self.threshold = threshold
        self.tissue_threshold = tissue_threshold
        self.status: JobStatus = JobStatus.ACCEPTED
        self.tiles_processed: int = 0
        self.total_tiles: int = 0
        self.message: str = "Queued"
        self.summary_key: Optional[str] = None
        self.results_key: Optional[str] = None
        self.heatmap_key: Optional[str] = None
        self.error: Optional[str] = None
        self._lock = threading.Lock()

    def update_progress(
        self,
        done: int,
        total: int,
        msg: str,
        tile_level: int | None = None,
    ) -> None:
        with self._lock:
            self.tiles_processed = done
            self.total_tiles = total
            self.message = msg
            if tile_level is not None:
                self.tile_level = tile_level
            if self.status == JobStatus.ACCEPTED:
                self.status = JobStatus.PROCESSING
            _notify_job_event(
                job_id=self.job_id,
                payload={
                    "status": "PROCESSING",
                    "image_id": self.image_id,
                    "tile_level": self.tile_level,
                    "threshold": self.threshold,
                    "tissue_threshold": self.tissue_threshold,
                    "tiles_processed": self.tiles_processed,
                    "total_tiles": self.total_tiles,
                    "message": self.message,
                },
            )

    def to_status_dict(self) -> Dict[str, Any]:
        with self._lock:
            return {
                "status": self.status.value,
                "image_id": self.image_id,
                "tile_level": self.tile_level,
                "tiles_processed": self.tiles_processed,
                "total_tiles": self.total_tiles,
                "message": self.message,
            }


_jobs: Dict[str, JobState] = {}


# ── Request / Response models ─────────────────────────────────────────────────


class AnalyzeRequest(BaseModel):
    job_id: Optional[str] = None
    image_id: str
    tile_level: Optional[int] = None
    threshold: Optional[float] = None
    tissue_threshold: Optional[float] = None
    batch_size: int = 16
    model_name: Optional[str] = None


class AnalyzeResponse(BaseModel):
    job_id: str
    status: str
    message: str


# ── Background worker ─────────────────────────────────────────────────────────


def _run_job(job_id: str, req: AnalyzeRequest) -> None:
    state = _jobs[job_id]
    try:
        model_path = None
        if req.model_name:
            model_path = str(Path(settings.MODEL_PATH).parent / req.model_name)
        result = run_analysis(
            job_id=job_id,
            image_id=req.image_id,
            tile_level=req.tile_level,
            threshold=req.threshold,
            tissue_threshold=req.tissue_threshold,
            batch_size=req.batch_size,
            progress_cb=state.update_progress,
            model_path=model_path,
        )

        state.tile_level = result.tile_level
        state.summary_key = result.summary_key
        state.results_key = result.results_key
        state.heatmap_key = result.heatmap_key
        state.status = JobStatus.COMPLETED
        state.message = "Analysis complete"
        _notify_job_event(
            job_id=job_id,
            payload={
                "status": "COMPLETED",
                "image_id": result.image_id,
                "tile_level": result.tile_level,
                "tiles_processed": state.total_tiles,
                "total_tiles": state.total_tiles,
                "message": state.message,
                "heatmap_key": result.heatmap_key,
                "summary_key": result.summary_key,
                "results_key": result.results_key,
                "tumor_area_percentage": result.summary.tumor_area_percentage,
                "aggregate_score": result.summary.aggregate_score,
                "max_score": result.summary.max_score,
            },
        )

    except Exception as exc:
        traceback.print_exc()
        state.status = JobStatus.FAILED
        state.error = str(exc)
        state.message = f"Failed: {exc}"
        _notify_job_event(
            job_id=job_id,
            payload={
                "status": "FAILED",
                "image_id": state.image_id,
                "tile_level": state.tile_level,
                "tiles_processed": state.tiles_processed,
                "total_tiles": state.total_tiles,
                "message": state.message,
                "error_message": state.error,
            },
        )


# ── Endpoints ─────────────────────────────────────────────────────────────────


@app.post("/jobs/analyze", response_model=AnalyzeResponse)
async def submit_analysis(req: AnalyzeRequest, background_tasks: BackgroundTasks):
    """Submit a region-detection job. Returns immediately."""
    job_id = req.job_id or str(uuid.uuid4())
    state = JobState(
        job_id=job_id,
        image_id=req.image_id,
        tile_level=req.tile_level or settings.DEFAULT_TILE_LEVEL,
        threshold=req.threshold or 0.5,
        tissue_threshold=req.tissue_threshold,
    )
    _jobs[job_id] = state
    background_tasks.add_task(_run_job, job_id, req)
    return AnalyzeResponse(
        job_id=job_id,
        status="accepted",
        message="Region detection job accepted and started in the background.",
    )


@app.get("/jobs/{job_id}/status")
async def get_status(job_id: str):
    """Poll the progress of a running analysis job."""
    state = _jobs.get(job_id)
    if state is None:
        raise HTTPException(status_code=404, detail=f"Job {job_id} not found")
    return state.to_status_dict()


@app.get("/jobs/{job_id}/results")
async def get_results(job_id: str):
    """Retrieve full results once the job is complete."""
    state = _jobs.get(job_id)
    if state is None:
        raise HTTPException(status_code=404, detail=f"Job {job_id} not found")

    if state.status == JobStatus.FAILED:
        raise HTTPException(status_code=500, detail=state.error)

    if state.status != JobStatus.COMPLETED:
        raise HTTPException(
            status_code=202,
            detail={
                "status": state.status.value,
                "message": "Analysis is still in progress. Poll /status to track.",
                "tiles_processed": state.tiles_processed,
                "total_tiles": state.total_tiles,
            },
        )

    if not state.summary_key:
        raise HTTPException(status_code=500, detail="Analysis summary artifact missing")

    summary = download_json(state.summary_key)
    predictions = download_json(state.results_key) if state.results_key else []
    return {
        **summary,
        "summary_key": state.summary_key,
        "results_key": state.results_key,
        "tile_predictions": predictions,
    }


@app.get("/health")
def health():
    return {"status": "ok", "service": "region-detector"}


@app.get("/models")
def list_models():
    """List available .pkl classifier heads in the models directory."""
    models_dir = Path(settings.MODEL_PATH).parent
    pkls = sorted(p.name for p in models_dir.glob("*.pkl")) if models_dir.exists() else []
    return {"models": pkls}


def _notify_job_event(job_id: str, payload: Dict[str, Any]) -> None:
    if not settings.BACKEND_INTERNAL_BASE_URL:
        return

    endpoint = (
        f"{settings.BACKEND_INTERNAL_BASE_URL.rstrip('/')}"
        f"/api/v1/internal/analysis/jobs/{job_id}/events"
    )
    req = request.Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=10) as response:
            if response.status >= 400:
                print(f"[analysis] Backend notify failed for {job_id}: HTTP {response.status}")
    except error.URLError as exc:
        print(f"[analysis] Backend notify failed for {job_id}: {exc}")
