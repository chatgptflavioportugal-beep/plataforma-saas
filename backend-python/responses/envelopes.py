from typing import Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class SuccessResponse(BaseModel, Generic[T]):
    success: bool = True
    data: T


class ErrorResponse(BaseModel):
    success: bool = False
    detail: str


class ValidationResponse(BaseModel):
    success: bool = False
    detail: str
    errors: list[dict] = []
