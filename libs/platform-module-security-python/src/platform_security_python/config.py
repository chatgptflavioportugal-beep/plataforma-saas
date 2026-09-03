import os

from pydantic_settings import BaseSettings, SettingsConfigDict


class ModuleSecuritySettings(BaseSettings):
    MODULE_ACCESS_TOKEN_SECRET: str
    JWT_ALGORITHM: str = "HS256"
    JWT_ISSUER: str | None = None
    JWT_AUDIENCE: str | None = None
    CLOCK_SKEW: int = 0

    model_config = SettingsConfigDict(
        env_file=(".env.global", f".env.{os.getenv('AMBIENTE', 'dev')}"),
        extra="ignore",
    )


settings = ModuleSecuritySettings()
