from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    GOOGLE_API_KEY: str = Field(default="")
    GEMINI_MODEL: str = Field(default="gemini-2.5-flash-lite")
    LLM_TIMEOUT_SECONDS: float = Field(default=20.0, gt=0, le=120)
    BACKEND_BASE_URL: str = Field(default="http://backend:8080")
    BACKEND_TIMEOUT_SECONDS: float = Field(default=10.0, gt=0, le=120)
    BACKEND_RETRY_COUNT: int = Field(default=0, ge=0, le=3)
    BOT_SECRET_KEY: str = Field(default="")
    JWT_SECRET_KEY: str = Field(default="")
    JWT_ISSUER: str = Field(default="panol-backend")
    JWT_AUDIENCE: str = Field(default="")
    JWT_LEEWAY_SECONDS: int = Field(default=30, ge=0, le=300)
    MAX_ITERATIONS: int = Field(default=10, ge=1, le=100)
    MAX_HISTORY_MESSAGES: int = Field(default=20, ge=1, le=100)
    METRICS_ENABLED: bool = Field(default=True)
    LOG_LEVEL: str = Field(default="INFO")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
