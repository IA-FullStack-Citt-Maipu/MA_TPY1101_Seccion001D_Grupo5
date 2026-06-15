from fastapi import APIRouter
from fastapi.responses import Response

from app.config import settings
from app.observability.metrics import metrics_content_type, render_metrics_payload


router = APIRouter(tags=["metrics"])


@router.get("/metrics")
def metrics() -> Response:
    if not settings.METRICS_ENABLED:
        return Response(status_code=404)

    payload = render_metrics_payload()
    return Response(content=payload, media_type=metrics_content_type())
