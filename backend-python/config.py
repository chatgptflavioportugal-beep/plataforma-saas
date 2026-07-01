from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    INTERNAL_TOKEN: str = ""
    MODULE_ACCESS_TOKEN_SECRET: str
    LOG_LEVEL: str = "INFO"
    MAX_PDF_SIZE_MB: int = 50
    CORS_ORIGINS: str = "http://localhost:3000"
    DATABASE_URL: str
    PDF_STORAGE_PATH: str = "/tmp/saas-pdf"

    class Config:
        env_file = ("../.env", ".env")


settings = Settings()
