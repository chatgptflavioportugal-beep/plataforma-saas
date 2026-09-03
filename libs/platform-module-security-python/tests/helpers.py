import time
import uuid

import jwt

SECRET = "test-module-access-token-secret"
TENANT_ID = str(uuid.uuid4())
USER_ID = str(uuid.uuid4())


def make_module_token(
    *,
    module_slug: str = "pdf",
    permissions: list[str] | None = None,
    limits: dict | None = None,
    token_type: str = "MODULE_ACCESS",
    exp_delta: int = 300,
    secret: str = SECRET,
    **extra_claims,
) -> str:
    now = int(time.time())
    payload = {
        "tokenType": token_type,
        "sub": USER_ID,
        "tenantId": TENANT_ID,
        "moduleId": str(uuid.uuid4()),
        "moduleSlug": module_slug,
        "planName": "pro",
        "accessSource": "plan",
        "permissions": permissions or [],
        "limits": limits or {},
        "permissionsVersion": 1,
        "iat": now,
        "exp": now + exp_delta,
    }
    payload.update(extra_claims)
    return jwt.encode(payload, secret, algorithm="HS256")
