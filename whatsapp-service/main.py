import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app_logging.logger import configure_logging
from config.config import settings
from db.database import close_pool, init_pool
from modules.whatsapp.api import routes as whatsapp_routes
from responses.envelopes import ErrorResponse

configure_logging(settings.LOG_LEVEL)

_ERROR_CODE_BY_STATUS = {
    400: "BAD_REQUEST",
    401: "UNAUTHORIZED",
    403: "FORBIDDEN",
    404: "NOT_FOUND",
}


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_pool()
    yield
    await close_pool()


app = FastAPI(
    title="SaaS Platform — WhatsApp Service",
    description="Serviço de mensageria WhatsApp. Aceita exclusivamente ModuleAccessToken.",
    version="0.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in settings.CORS_ORIGINS.split(",")],
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)

app.include_router(whatsapp_routes.router)


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    body = ErrorResponse(
        code=_ERROR_CODE_BY_STATUS.get(exc.status_code, "REQUEST_ERROR"),
        message=str(exc.detail),
        timestamp=datetime.now(timezone.utc),
        trace_id=str(uuid.uuid4()),
    )
    return JSONResponse(status_code=exc.status_code, content=body.model_dump(mode="json"))


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "UP", "service": "whatsapp-service"}
