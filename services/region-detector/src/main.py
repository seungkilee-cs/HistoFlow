"""FastAPI application — HTTP interface for the region-detector service.

Endpoints
---------
POST /jobs/analyze      Submit a new region-detection job (runs in background)
GET  /jobs/{id}/status  Poll job progress
GET  /jobs/{id}/results Get full results (tile predictions + summary + heatmap)
GET  /health            Health-check
"""

from __future__ import annotations

import threading
import traceback
import uuid
from dataclasses import asdict
from enum import Enum
from typing import Any, Dict, List, Optional

from fastapi import BackgroundTasks, FastAPI, HTTPException
from pydantic import BaseModel

from .config import settings
from .pipeline import AnalysisResult, preload_models, run_analysis

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
    def __init__(self, image_id: str, tile_level: int, threshold: float):
        self.image_id = image_id
        self.tile_level = tile_level
        self.threshold = threshold
        self.status: JobStatus = JobStatus.ACCEPTED
        self.tiles_processed: int = 0
        self.total_tiles: int = 0
        self.message: str = "Queued"
        self.result: Optional[Dict[str, Any]] = None
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
    image_id: str
    tile_level: Optional[int] = None
    threshold: Optional[float] = None
    tissue_threshold: Optional[float] = None
    batch_size: int = 16


class AnalyzeResponse(BaseModel):
    job_id: str
    status: str
    message: str


# ── Background worker ─────────────────────────────────────────────────────────


def _run_job(job_id: str, req: AnalyzeRequest) -> None:
    state = _jobs[job_id]
    try:
        result = run_analysis(
            image_id=req.image_id,
            tile_level=req.tile_level,
            threshold=req.threshold,
            tissue_threshold=req.tissue_threshold,
            batch_size=req.batch_size,
            progress_cb=state.update_progress,
        )

        tile_dicts = [asdict(tp) for tp in result.tile_predictions]
        state.result = {
            "image_id": result.image_id,
            "tile_level": result.tile_level,
            "dzi": result.dzi,
            "summary": asdict(result.summary),
            "heatmap_key": result.heatmap_key,
            "timings": result.timings,
            "tile_predictions": tile_dicts,
        }
        state.tile_level = result.tile_level
        state.status = JobStatus.COMPLETED
        state.message = "Analysis complete"

    except Exception as exc:
        traceback.print_exc()
        state.status = JobStatus.FAILED
        state.error = str(exc)
        state.message = f"Failed: {exc}"


# ── Endpoints ─────────────────────────────────────────────────────────────────


@app.post("/jobs/analyze", response_model=AnalyzeResponse)
async def submit_analysis(req: AnalyzeRequest, background_tasks: BackgroundTasks):
    """Submit a region-detection job. Returns immediately."""
    job_id = str(uuid.uuid4())
    state = JobState(
        image_id=req.image_id,
        tile_level=req.tile_level or settings.DEFAULT_TILE_LEVEL,
        threshold=req.threshold or 0.5,
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

    return state.result


@app.get("/health")
def health():
    return {"status": "ok", "service": "region-detector"}
