from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    # This tells pydantic to load variables from a .env file
    model_config = SettingsConfigDict(env_file='.env', env_file_encoding='utf-8')

    # MinIO Settings
    MINIO_ENDPOINT: str
    MINIO_ACCESS_KEY: str
    MINIO_SECRET_KEY: str
    MINIO_UPLOAD_BUCKET: str = "histoflow-tiles"
    MINIO_SECURE: bool = False

    # Worker Settings
    TEMP_STORAGE_PATH: str = "/tmp/histoflow_tiling"

# Create a single, importable instance of the settings
settings = Settings()