from typing import Callable

from fastapi import Header

from exceptions.authentication import AuthenticationError
from security.dependencies.current_user import CurrentUser
from security.tokens.module_token import validate_module_access_token

MODULE_SLUG = "pdf"


def require_permission(permission: str | None = None) -> Callable[..., CurrentUser]:
    """
    Fábrica de dependency FastAPI: valida o ModuleAccessToken do módulo `pdf`
    e, se `permission` for informado, garante que o usuário a possui. Tudo vem das
    claims do próprio token — nenhuma rota precisa consultar outro serviço.

    Uso:
        auth: CurrentUser = Depends(require_permission("pdf-merge"))
    """
    async def dependency(
        authorization: str = Header(..., alias="Authorization"),
    ) -> CurrentUser:
        if not authorization.startswith("Bearer "):
            raise AuthenticationError("Token de acesso do módulo não informado.")
        token = authorization[len("Bearer "):]
        user = validate_module_access_token(token, MODULE_SLUG)
        if permission:
            user.require_permission(permission)
        return user

    return dependency


def require_limit(code: str) -> Callable[..., CurrentUser]:
    """
    Fábrica de dependency FastAPI para rotas que só precisam validar o token e
    expor um limite (sem exigir uma permissão específica). O valor configurado
    do limite (`auth.get_limit(code)`) vem exclusivamente do JWT; a comparação
    contra o valor real da operação (tamanho de arquivo, contagem do dia etc.)
    é feita pelo próprio validador/serviço da rota.

    Uso:
        auth: CurrentUser = Depends(require_limit("max-file-size"))
    """
    return require_permission(None)
