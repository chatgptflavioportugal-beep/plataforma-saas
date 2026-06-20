from dataclasses import dataclass, field
from typing import Callable

import jwt
from fastapi import Depends, Header, HTTPException

from config import settings


@dataclass
class ModuleAuthContext:
    token_type: str
    user_id: str
    tenant_id: str
    module_id: str
    module_slug: str
    plan_name: str
    access_source: str
    permissions: list[str] = field(default_factory=list)
    limits: list[dict] = field(default_factory=list)
    permissions_version: int = 1
    issued_at: int = 0
    expires_at: int = 0

    def has_permission(self, key: str) -> bool:
        return key in self.permissions

    def require_permission(self, key: str) -> None:
        if not self.has_permission(key):
            raise HTTPException(
                status_code=403,
                detail=f"Você não possui permissão para executar esta ação: {key}",
            )

    def get_limit(self, code: str, default=None):
        for limit in self.limits:
            if limit.get("key") == code:
                return limit.get("value", default)
        return default


def validate_module_access_token(token: str, expected_module_slug: str) -> ModuleAuthContext:
    if not token:
        raise HTTPException(status_code=401, detail="Token de acesso do módulo não informado.")

    try:
        payload = jwt.decode(
            token,
            settings.MODULE_ACCESS_TOKEN_SECRET,
            algorithms=["HS256"],
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token de acesso do módulo expirado.")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Token de acesso do módulo inválido.")

    if payload.get("tokenType") != "MODULE_ACCESS":
        raise HTTPException(status_code=401, detail="Token de acesso do módulo inválido.")

    if payload.get("moduleSlug") != expected_module_slug:
        raise HTTPException(status_code=403, detail="Token não pertence a este módulo.")

    return ModuleAuthContext(
        token_type=payload.get("tokenType", ""),
        user_id=payload.get("sub", ""),
        tenant_id=payload.get("tenantId", ""),
        module_id=payload.get("moduleId", ""),
        module_slug=payload.get("moduleSlug", ""),
        plan_name=payload.get("planName", ""),
        access_source=payload.get("accessSource", ""),
        permissions=payload.get("permissions", []),
        limits=payload.get("limits", []),
        permissions_version=payload.get("permissionsVersion", 1),
        issued_at=payload.get("iat", 0),
        expires_at=payload.get("exp", 0),
    )


def require_module_token(expected_slug: str) -> Callable[..., ModuleAuthContext]:
    """
    Fábrica de dependency FastAPI que valida o ModuleAccessToken para um slug específico.

    Uso:
        auth: ModuleAuthContext = Depends(require_module_token("pdf"))
    """
    async def dependency(
        authorization: str = Header(..., alias="Authorization"),
    ) -> ModuleAuthContext:
        if not authorization.startswith("Bearer "):
            raise HTTPException(
                status_code=401, detail="Token de acesso do módulo não informado."
            )
        token = authorization[len("Bearer "):]
        return validate_module_access_token(token, expected_slug)

    return dependency
