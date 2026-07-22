"""
Convenções de chave usadas quando o cache Redis for ativado. Centralizar aqui
evita colisão de namespace entre serviços que compartilham a mesma instância
Redis no futuro.
"""


def usage_cache_key(tenant_id: str, module_slug: str, metric_code: str, period: str) -> str:
    return f"usage:{tenant_id}:{module_slug}:{metric_code}:{period}"


def session_cache_key(user_id: str) -> str:
    return f"session:{user_id}"


def module_token_cache_key(user_id: str, module_slug: str) -> str:
    return f"module-token:{user_id}:{module_slug}"
