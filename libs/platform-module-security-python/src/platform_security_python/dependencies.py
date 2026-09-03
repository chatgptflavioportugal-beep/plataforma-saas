from typing import Callable

from fastapi import Header

from .context import ModuleContext
from .exceptions import AuthenticationError
from .tokens import decode_module_token


def module_security(module_slug: str, permission: str | None = None) -> Callable[..., ModuleContext]:
    """
    Fábrica de dependency FastAPI: valida o ModuleAccessToken do módulo `module_slug`
    e, se `permission` for informado, garante que o usuário a possui. Tudo vem das
    claims do próprio token — nenhuma rota precisa consultar outro serviço.

    Uso:
        auth: ModuleContext = Depends(module_security("pdf", "pdf-merge"))
    """

    async def dependency(
        authorization: str | None = Header(None, alias="Authorization"),
    ) -> ModuleContext:
        if not authorization or not authorization.startswith("Bearer "):
            raise AuthenticationError("Token de acesso do módulo não informado.")
        token = authorization[len("Bearer "):]
        context = decode_module_token(token, module_slug)
        if permission:
            context.require_permission(permission)
        return context

    return dependency


# Alias mantido para compatibilidade com o nome usado em pdf-service/whatsapp-service
# antes da extração desta biblioteca.
require_permission = module_security
