from typing import Callable

from fastapi import Header

from exceptions.authentication import AuthenticationError
from security.dependencies.current_user import CurrentUser
from security.tokens.module_token import validate_module_access_token


def require_permission(module_slug: str, permission: str | None = None) -> Callable[..., CurrentUser]:
    """
    Fábrica de dependency FastAPI: valida o ModuleAccessToken do módulo `module_slug`
    e, se `permission` for informado, garante que o usuário a possui. Tudo vem das
    claims do próprio token — nenhuma rota precisa consultar outro serviço.

    Uso:
        auth: CurrentUser = Depends(require_permission("pdf", "pdf-merge"))
    """
    async def dependency(
        authorization: str = Header(..., alias="Authorization"),
    ) -> CurrentUser:
        if not authorization.startswith("Bearer "):
            raise AuthenticationError("Token de acesso do módulo não informado.")
        token = authorization[len("Bearer "):]
        user = validate_module_access_token(token, module_slug)
        if permission:
            user.require_permission(permission)
        return user

    return dependency
