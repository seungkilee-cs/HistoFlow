from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Centralised, env-driven configuration for the region-detector service."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    # ── MinIO ──────────────────────────────────────────────────────────
    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str = "minioadmin"
    MINIO_SECRET_KEY: str = "minioadmin"
    MINIO_SECURE: bool = False
    TILES_BUCKET: str = "histoflow-tiles"

    # ── Model ──────────────────────────────────────────────────────────
    MODEL_PATH: str = "models/dinov2_classifier.pkl"
    BACKBONE: str = "facebook/dinov2-base"

    # ── Analysis defaults ──────────────────────────────────────────────
    DEFAULT_TILE_LEVEL: int = 12
    TISSUE_THRESHOLD: float = 0.15
    CLASSIFICATION_THRESHOLD: float = 0.5

    # ── Worker ─────────────────────────────────────────────────────────
    TEMP_DIR: str = "/tmp/region_detector"

    # ── Concurrency ────────────────────────────────────────────────────
    # Number of parallel threads used to download tiles from MinIO.
    DOWNLOAD_WORKERS: int = 16


settings = Settings()
