from datetime import datetime

from pydantic import BaseModel

from enums.pdf_job_status import PdfJobStatus


class PdfJobOut(BaseModel):
    id: str
    tenant_id: str
    user_id: str
    status: PdfJobStatus
    file_a_name: str
    file_b_name: str
    result_name: str | None
    error_message: str | None
    created_at: datetime
    updated_at: datetime


def job_to_out(job: dict) -> PdfJobOut:
    return PdfJobOut(
        id=str(job["id"]),
        tenant_id=str(job["tenant_id"]),
        user_id=str(job["user_id"]),
        status=job["status"],
        file_a_name=job["file_a_name"],
        file_b_name=job["file_b_name"],
        result_name=job["result_name"],
        error_message=job["error_message"],
        created_at=job["created_at"],
        updated_at=job["updated_at"],
    )
