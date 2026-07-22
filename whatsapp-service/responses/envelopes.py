from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class SuccessResponse(BaseModel, Generic[T]):
    success: bool = True
    data: T


class ErrorResponse(BaseModel):
    """Payload de erro padrão — mesmo contrato usado pelo Usage/Admin Service (Java)."""
    success: bool = False
    code: str
    message: str
    details: str | None = None
    timestamp: datetime
    trace_id: str


class ValidationResponse(BaseModel):
    success: bool = False
    detail: str
    errors: list[dict] = []
