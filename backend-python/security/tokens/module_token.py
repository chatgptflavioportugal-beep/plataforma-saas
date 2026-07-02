import logging

import jwt

from config.config import settings
from exceptions.authentication import AuthenticationError
from exceptions.authorization import AuthorizationError
from security.dependencies.current_user import CurrentUser

logger = logging.getLogger(__name__)


def validate_module_access_token(token: str, expected_module_slug: str) -> CurrentUser:
    if not token:
        raise AuthenticationError("Token de acesso do módulo não informado.")

    try:
        payload = jwt.decode(
            token,
            settings.MODULE_ACCESS_TOKEN_SECRET,
            algorithms=["HS256"],
        )
    except jwt.ExpiredSignatureError:
        logger.warning("ModuleAccessToken expirado")
        raise AuthenticationError("Token de acesso do módulo expirado.")
    except jwt.InvalidTokenError as e:
        logger.warning("ModuleAccessToken inválido — secret_prefix=%s... erro=%s",
                       settings.MODULE_ACCESS_TOKEN_SECRET[:6], str(e))
        raise AuthenticationError("Token de acesso do módulo inválido.")

    if payload.get("tokenType") != "MODULE_ACCESS":
        raise AuthenticationError("Token de acesso do módulo inválido.")

    if payload.get("moduleSlug") != expected_module_slug:
        raise AuthorizationError("Token não pertence a este módulo.")

    return CurrentUser(
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
