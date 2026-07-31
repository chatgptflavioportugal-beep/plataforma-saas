import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    LOG_LEVEL: str = "INFO"
    CORS_ORIGINS: str = "http://localhost:5100,http://localhost:5101"
    DATABASE_URL: str
    PDF_STORAGE_PATH: str = "/tmp/saas-pdf"

    class Config:
        APP_ENV = os.getenv("AMBIENTE", "dev")
        env_file = (".env.global", f".env.{APP_ENV}")


settings = Settings()
