import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    MODULE_ACCESS_TOKEN_SECRET: str
    LOG_LEVEL: str = "INFO"
    CORS_ORIGINS: str = "http://localhost:3000"
    DATABASE_URL: str

    class Config:
        APP_ENV = os.getenv("AMBIENTE", "dev")
        env_file = (".env.global", f".env.{APP_ENV}")


settings = Settings()
