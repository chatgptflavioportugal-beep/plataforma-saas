from fastapi import HTTPException


class ModuleSecurityError(HTTPException):
    """Classe base de todos os erros da biblioteca — sempre mapeada para uma resposta HTTP."""


class AuthenticationError(ModuleSecurityError):
    def __init__(self, detail: str):
        super().__init__(status_code=401, detail=detail)


class AuthorizationError(ModuleSecurityError):
    def __init__(self, detail: str):
        super().__init__(status_code=403, detail=detail)


class InvalidTokenError(AuthenticationError):
    """Token malformado, com assinatura invalida ou tokenType diferente de MODULE_ACCESS."""


class ExpiredTokenError(AuthenticationError):
    """Token com exp no passado."""


class PermissionDeniedError(AuthorizationError):
    """Token valido, mas sem a permissao exigida pela rota."""


class ModuleMismatchError(AuthorizationError):
    """Token valido, mas emitido para outro modulo (moduleSlug diferente do esperado)."""
