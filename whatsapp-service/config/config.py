import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    LOG_LEVEL: str = "INFO"
    # "json" (padrão — Google Cloud Logging) ou "text" (legível, uso local fora de container)
    LOG_FORMAT: str = "json"
    CORS_ORIGINS: str = "http://localhost:5100,http://localhost:5102"
    DATABASE_URL: str

    class Config:
        APP_ENV = os.getenv("AMBIENTE", "dev")
        env_file = (".env.global", f".env.{APP_ENV}")


settings = Settings()
