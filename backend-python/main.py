from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app_logging.logger import configure_logging
from config.config import settings
from db.database import close_pool, init_pool
from modules.pdf.api import routes as pdf_routes

configure_logging(settings.LOG_LEVEL)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_pool()
    yield
    await close_pool()


app = FastAPI(
    title="SaaS Platform — Backend Python",
    description="Serviço de processamento PDF. Aceita exclusivamente ModuleAccessToken.",
    version="2.0.0",
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

app.include_router(pdf_routes.router)


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "UP", "service": "backend-python"}
