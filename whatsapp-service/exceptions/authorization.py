from fastapi import HTTPException


class AuthorizationError(HTTPException):
    def __init__(self, detail: str):
        super().__init__(status_code=403, detail=detail)
