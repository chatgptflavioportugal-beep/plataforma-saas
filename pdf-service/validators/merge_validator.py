import io

from fastapi import UploadFile
from pypdf import PdfReader

from exceptions.validation import ValidationError
from security.dependencies.current_user import CurrentUser
from permissions.constants import LIMITE_PDF_MAX_FILE_SIZE


def validate_request(file_a: UploadFile, file_b: UploadFile) -> None:
    if not file_a.content_type or "pdf" not in file_a.content_type:
        raise ValidationError("file_a deve ser PDF")
    if not file_b.content_type or "pdf" not in file_b.content_type:
        raise ValidationError("file_b deve ser PDF")


def validate_limits(auth: CurrentUser, content_a: bytes, content_b: bytes) -> None:
    max_size_mb = int(auth.get_limit(LIMITE_PDF_MAX_FILE_SIZE, default=50))
    max_bytes = max_size_mb * 1024 * 1024

    if len(content_a) > max_bytes or len(content_b) > max_bytes:
        raise ValidationError(f"Arquivo excede o limite permitido de {max_size_mb} MB")


def validate_files(content_a: bytes, content_b: bytes) -> None:
    for content, label in ((content_a, "file_a"), (content_b, "file_b")):
        if not content:
            raise ValidationError(f"{label} está vazio")
        try:
            PdfReader(io.BytesIO(content))
        except Exception:
            raise ValidationError(f"{label} não é um PDF válido")
