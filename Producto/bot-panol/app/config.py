from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


DEFAULT_CORS_ALLOWED_ORIGINS = (
    "http://localhost:18081",
    "http://127.0.0.1:18081",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
)


class Settings(BaseSettings):
    GOOGLE_API_KEY: str = Field(default="")
    GEMINI_MODEL: str = Field(default="gemini-2.5-flash-lite")
    LLM_TIMEOUT_SECONDS: float = Field(default=20.0, gt=0, le=120)
    LLM_TOTAL_TIMEOUT_SECONDS: float = Field(default=75.0, gt=0, le=180)
    BACKEND_BASE_URL: str = Field(default="http://backend:8080")
    BACKEND_CLIENT_SECRET: str = Field(default="")
    BACKEND_TIMEOUT_SECONDS: float = Field(default=10.0, gt=0, le=120)
    BACKEND_RETRY_COUNT: int = Field(default=0, ge=0, le=3)
    BOT_SECRET_KEY: str = Field(default="")
    JWT_SECRET_KEY: str = Field(default="")
    JWT_ISSUER: str = Field(default="panol-backend")
    JWT_AUDIENCE: str = Field(default="bot-panol")
    JWT_LEEWAY_SECONDS: int = Field(default=30, ge=0, le=300)
    MAX_ITERATIONS: int = Field(default=10, ge=1, le=100)
    MAX_HISTORY_MESSAGES: int = Field(default=20, ge=1, le=100)
    METRICS_ENABLED: bool = Field(default=True)
    LOG_LEVEL: str = Field(default="INFO")
    CORS_ALLOWED_ORIGINS: str = Field(default="")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    def get_cors_allowed_origins(self) -> list[str]:
        origins = list(DEFAULT_CORS_ALLOWED_ORIGINS)
        configured_origins = [
            origin.strip()
            for origin in self.CORS_ALLOWED_ORIGINS.split(",")
            if origin.strip()
        ]
        origins.extend(configured_origins)
        return list(dict.fromkeys(origins))


settings = Settings()
