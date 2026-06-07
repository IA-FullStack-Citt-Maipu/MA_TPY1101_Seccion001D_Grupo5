from fastapi import FastAPI

from app.api.v1.chat import router as chat_router
from app.api.v1.health import router as health_router
from app.api.v1.metrics import router as metrics_router
from app.config import settings
from app.middleware.request_context import RequestContextMiddleware
from app.observability.logger import configure_logging


def create_app() -> FastAPI:
    configure_logging(settings.LOG_LEVEL)
    application = FastAPI(
        title="bot-panol",
        version="0.1.0",
    )
    application.add_middleware(RequestContextMiddleware)
    application.include_router(health_router)
    application.include_router(metrics_router)
    application.include_router(chat_router, prefix="/api/v1")
    return application


app = create_app()
