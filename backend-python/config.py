from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    INTERNAL_TOKEN: str = ""
    MODULE_ACCESS_TOKEN_SECRET: str
    LOG_LEVEL: str = "INFO"
    MAX_PDF_SIZE_MB: int = 50

    class Config:
        env_file = ".env"


settings = Settings()
