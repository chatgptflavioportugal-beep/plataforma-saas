from fastapi import Header, HTTPException
from config import settings


async def verify_internal_token(
    x_internal_token: str = Header(..., alias="X-Internal-Token"),
) -> None:
    if x_internal_token != settings.INTERNAL_TOKEN:
        raise HTTPException(status_code=401, detail="Token interno inválido")


async def get_tenant_id(
    x_tenant_id: str = Header(..., alias="X-Tenant-ID"),
) -> str:
    return x_tenant_id


async def get_user_id(
    x_user_id: str = Header(..., alias="X-User-ID"),
) -> str:
    return x_user_id
