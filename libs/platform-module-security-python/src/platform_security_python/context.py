from dataclasses import dataclass, field
from typing import Any

from .exceptions import PermissionDeniedError


@dataclass
class ModuleContext:
    token_type: str
    user_id: str
    tenant_id: str
    module_id: str
    module_slug: str
    plan_name: str
    access_source: str
    permissions: list[str] = field(default_factory=list)
    limits: dict[str, Any] = field(default_factory=dict)
    permissions_version: int = 1
    issued_at: int = 0
    expires_at: int = 0

    def has_permission(self, key: str) -> bool:
        return key in self.permissions

    def require_permission(self, key: str) -> None:
        if not self.has_permission(key):
            raise PermissionDeniedError(
                f"Você não possui permissão para executar esta ação: {key}"
            )

    def get_limit(self, code: str, default=None):
        return self.limits.get(code, default)
