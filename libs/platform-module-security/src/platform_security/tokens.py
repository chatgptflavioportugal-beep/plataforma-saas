import logging

import jwt

from .config import settings
from .context import ModuleContext
from .exceptions import AuthenticationError, ExpiredTokenError, InvalidTokenError, ModuleMismatchError

logger = logging.getLogger(__name__)


def decode_module_token(token: str, expected_module_slug: str) -> ModuleContext:
    """
    Decodifica e valida um ModuleAccessToken (JWT HS256 emitido pelo auth-service),
    e retorna o ModuleContext correspondente. Unica funcao da plataforma autorizada a
    chamar jwt.decode() sobre esse token — nenhum modulo deve reimplementar isso.
    """
    if not token:
        logger.warning("ModuleAccessToken ausente")
        raise AuthenticationError("Token de acesso do módulo não informado.")

    try:
        payload = jwt.decode(
            token,
            settings.MODULE_ACCESS_TOKEN_SECRET,
            algorithms=[settings.JWT_ALGORITHM],
            leeway=settings.CLOCK_SKEW,
        )
    except jwt.ExpiredSignatureError:
        logger.warning("ModuleAccessToken expirado")
        raise ExpiredTokenError("Token de acesso do módulo expirado.")
    except jwt.InvalidTokenError as exc:
        logger.warning("ModuleAccessToken inválido — erro=%s", str(exc))
        raise InvalidTokenError("Token de acesso do módulo inválido.")

    if payload.get("tokenType") != "MODULE_ACCESS":
        logger.warning(
            "ModuleAccessToken com tokenType inesperado — tokenType=%s",
            payload.get("tokenType"),
        )
        raise InvalidTokenError("Token de acesso do módulo inválido.")

    if payload.get("moduleSlug") != expected_module_slug:
        logger.warning(
            "ModuleAccessToken não pertence ao módulo esperado — esperado=%s recebido=%s",
            expected_module_slug,
            payload.get("moduleSlug"),
        )
        raise ModuleMismatchError("Token não pertence a este módulo.")

    logger.info(
        "ModuleAccessToken válido — moduleSlug=%s tenantId=%s",
        expected_module_slug,
        payload.get("tenantId"),
    )

    return ModuleContext(
        token_type=payload.get("tokenType", ""),
        user_id=payload.get("sub", ""),
        tenant_id=payload.get("tenantId", ""),
        module_id=payload.get("moduleId", ""),
        module_slug=payload.get("moduleSlug", ""),
        plan_name=payload.get("planName", ""),
        access_source=payload.get("accessSource", ""),
        permissions=payload.get("permissions", []),
        limits=payload.get("limits", {}),
        permissions_version=payload.get("permissionsVersion", 1),
        issued_at=payload.get("iat", 0),
        expires_at=payload.get("exp", 0),
    )
