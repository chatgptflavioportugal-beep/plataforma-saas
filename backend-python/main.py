import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from config import settings
from routers import pdf_router

logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)

app = FastAPI(
    title="SaaS Platform — Backend Python",
    description="Serviço de processamento especializado. Acesso restrito ao Quarkus via X-Internal-Token.",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[],
    allow_methods=["POST"],
    allow_headers=["X-Internal-Token", "X-Tenant-ID", "X-User-ID", "Content-Type"],
)

app.include_router(pdf_router.router)


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "UP", "service": "backend-python"}
