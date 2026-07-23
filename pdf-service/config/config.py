import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    MODULE_ACCESS_TOKEN_SECRET: str
    LOG_LEVEL: str = "INFO"
    MAX_PDF_SIZE_MB: int = 50
    CORS_ORIGINS: str = "http://localhost:3000"
    DATABASE_URL: str
    PDF_STORAGE_PATH: str = "/tmp/saas-pdf"

    class Config:
        APP_ENV = os.getenv("AMBIENTE", "dev")
        env_file = (".env.global", f".env.{APP_ENV}")


settings = Settings()
