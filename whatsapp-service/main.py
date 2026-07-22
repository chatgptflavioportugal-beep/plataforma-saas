from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app_logging.logger import configure_logging
from config.config import settings
from db.database import close_pool, init_pool
from modules.whatsapp.api import routes as whatsapp_routes

configure_logging(settings.LOG_LEVEL)


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


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "UP", "service": "whatsapp-service"}
