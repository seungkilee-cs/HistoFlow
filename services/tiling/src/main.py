from fastapi import FastAPI, BackgroundTasks, HTTPException
from pydantic import BaseModel

from .tiling_service import TilingService

# Create the FastAPI app
app = FastAPI(title="HistoFlow Tiling Service")

# Create a single, reusable instance of our service
tiling_service = TilingService()

# Define the data we expect to receive in a job request
class TilingJob(BaseModel):
    image_id: str
    source_bucket: str
    source_object_name: str # e.g., "unprocessed/image_id/my-file.svs"

@app.post("/jobs/tile-image")
async def create_tiling_job(job: TilingJob, background_tasks: BackgroundTasks):
    """
    This endpoint accepts a tiling job. It will respond IMMEDIATELY
    and run the long tiling process in the background.
    """
    print(f"Accepted job for image_id: {job.image_id}")
    
    # Add the long-running task to be executed after the response is sent
    background_tasks.add_task(
        tiling_service.process_image,
        image_id=job.image_id,
        source_object_name=job.source_object_name,
        source_bucket=job.source_bucket
    )
    
    # Respond immediately to the caller (your Kotlin backend)
    return {"message": "Tiling job accepted and started in the background.", "job": job}

@app.get("/health")
def health_check():
    """A simple endpoint to check if the service is running."""
    return {"status": "ok"}