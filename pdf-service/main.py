import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.openapi.utils import get_openapi
from fastapi.responses import JSONResponse

from app_logging.logger import configure_logging
from config.config import settings
from db.database import close_pool, init_pool
from health import routes as health_routes
from routes import pdf_routes
from responses.envelopes import ErrorResponse

configure_logging(settings.LOG_LEVEL, settings.LOG_FORMAT)

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
    title="SaaS Platform — PDF Service",
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
app.include_router(health_routes.router)


def custom_openapi():
    """Declara o esquema Bearer (ModuleAccessToken) no schema OpenAPI, para que
    o botão "Authorize" do Swagger UI permita informar o token e repassá-lo
    como header Authorization nas chamadas de teste. Não altera a validação
    em si (feita por platform_security.module_security), só a documentação.
    """
    if app.openapi_schema:
        return app.openapi_schema

    schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
    )
    schema.setdefault("components", {}).setdefault("securitySchemes", {})["moduleToken"] = {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
        "description": (
            "ModuleAccessToken emitido pelo auth-service "
            "(POST /api/v1/module-token/pdf). Enviar como "
            "'Authorization: Bearer <token>'."
        ),
    }
    for path, methods in schema.get("paths", {}).items():
        if path == "/health":
            continue
        for method, operation in methods.items():
            if method.lower() in ("get", "post", "put", "delete", "patch"):
                operation["security"] = [{"moduleToken": []}]

    app.openapi_schema = schema
    return app.openapi_schema


app.openapi = custom_openapi


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    body = ErrorResponse(
        code=_ERROR_CODE_BY_STATUS.get(exc.status_code, "REQUEST_ERROR"),
        message=str(exc.detail),
        timestamp=datetime.now(timezone.utc),
        trace_id=str(uuid.uuid4()),
    )
    return JSONResponse(status_code=exc.status_code, content=body.model_dump(mode="json"))
