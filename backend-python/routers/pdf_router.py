import io
import logging
import os
import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from auth.module_auth import ModuleAuthContext, require_module_token
from config import settings
from db import pdf_jobs_repo
from services.pdf_service import merge_pdfs

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/pdf", tags=["PDF"])

_require_pdf_token = require_module_token("pdf")


class PdfJobOut(BaseModel):
    id: str
    tenant_id: str
    user_id: str
    status: str
    file_a_name: str
    file_b_name: str
    result_name: str | None
    error_message: str | None
    created_at: datetime
    updated_at: datetime


def _storage_dir(tenant_id: str) -> str:
    path = os.path.join(settings.PDF_STORAGE_PATH, tenant_id)
    os.makedirs(path, exist_ok=True)
    return path


def _job_to_out(job: dict) -> PdfJobOut:
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


@router.post("/merge", response_model=PdfJobOut, summary="Merge dois PDFs e registra o job")
async def merge_pdf(
    file_a: UploadFile = File(..., alias="file_a"),
    file_b: UploadFile = File(..., alias="file_b"),
    auth: ModuleAuthContext = Depends(_require_pdf_token),
) -> PdfJobOut:
    auth.require_permission("module.pdf.pdf-merge")

    if not file_a.content_type or "pdf" not in file_a.content_type:
        raise HTTPException(status_code=400, detail="file_a deve ser PDF")
    if not file_b.content_type or "pdf" not in file_b.content_type:
        raise HTTPException(status_code=400, detail="file_b deve ser PDF")

    max_size_mb = int(auth.get_limit("max_file_size_mb", default=50))
    max_bytes = max_size_mb * 1024 * 1024

    content_a = await file_a.read()
    content_b = await file_b.read()

    if len(content_a) > max_bytes or len(content_b) > max_bytes:
        raise HTTPException(
            status_code=400,
            detail=f"Arquivo excede o limite permitido de {max_size_mb} MB",
        )

    job_id = str(uuid.uuid4())
    storage_dir = _storage_dir(auth.tenant_id)
    path_a = os.path.join(storage_dir, f"{job_id}-a.pdf")
    path_b = os.path.join(storage_dir, f"{job_id}-b.pdf")

    with open(path_a, "wb") as f:
        f.write(content_a)
    with open(path_b, "wb") as f:
        f.write(content_b)

    job = await pdf_jobs_repo.create_job(
        job_id=job_id,
        tenant_id=auth.tenant_id,
        user_id=auth.user_id,
        file_a_name=file_a.filename or "file_a.pdf",
        file_b_name=file_b.filename or "file_b.pdf",
        file_a_path=path_a,
        file_b_path=path_b,
    )

    try:
        merged_bytes = merge_pdfs(content_a, content_b)
    except Exception as e:
        await pdf_jobs_repo.fail_job(str(job["id"]), str(e))
        logger.error("Erro ao fazer merge — job=%s: %s", job["id"], e)
        raise HTTPException(status_code=500, detail=f"Erro no merge: {e}")

    name_a = (file_a.filename or "file_a").removesuffix(".pdf")
    name_b = (file_b.filename or "file_b").removesuffix(".pdf")
    result_name = f"{name_a}_{name_b}_merged.pdf"
    result_path = os.path.join(storage_dir, f"{job_id}-merged.pdf")

    with open(result_path, "wb") as f:
        f.write(merged_bytes)

    job = await pdf_jobs_repo.complete_job(str(job["id"]), result_path, result_name)

    logger.info(
        "PDF merge concluído — tenant=%s user=%s plan=%s job=%s",
        auth.tenant_id, auth.user_id, auth.plan_name, job["id"],
    )

    return _job_to_out(job)


@router.get("/jobs", response_model=list[PdfJobOut], summary="Lista os merges do tenant")
async def list_jobs(
    auth: ModuleAuthContext = Depends(_require_pdf_token),
) -> list[PdfJobOut]:
    jobs = await pdf_jobs_repo.list_jobs(auth.tenant_id)
    return [_job_to_out(j) for j in jobs]


@router.get("/jobs/{job_id}/download", summary="Baixa o PDF resultante de um merge")
async def download_job(
    job_id: str,
    auth: ModuleAuthContext = Depends(_require_pdf_token),
) -> StreamingResponse:
    job = await pdf_jobs_repo.get_job(job_id, auth.tenant_id)

    if not job:
        raise HTTPException(status_code=404, detail="Job não encontrado")
    if job["status"] != "completed":
        raise HTTPException(status_code=400, detail="Job ainda não concluído")

    result_path = job.get("result_path")
    if not result_path or not os.path.exists(result_path):
        raise HTTPException(status_code=404, detail="Arquivo do resultado não encontrado")

    with open(result_path, "rb") as f:
        content = f.read()

    filename = job["result_name"] or "merged.pdf"

    return StreamingResponse(
        io.BytesIO(content),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )
