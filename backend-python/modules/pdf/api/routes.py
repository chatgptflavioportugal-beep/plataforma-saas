import io
import logging
import os
import uuid

from fastapi import APIRouter, Depends, File, UploadFile
from fastapi.responses import StreamingResponse

from exceptions.business import BusinessRuleError
from exceptions.validation import ValidationError
from modules.pdf.permissions.constants import PDF_MERGE
from modules.pdf.repository import pdf_jobs_repository
from modules.pdf.schemas.pdf_job import PdfJobOut, job_to_out
from modules.pdf.services.merge_service import merge_pdfs
from security.dependencies.current_user import CurrentUser
from security.permissions.decorators import require_permission
from services.storage.local_storage import (
    file_exists,
    read_file,
    save_file,
    storage_dir_for_tenant,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/pdf", tags=["PDF"])


@router.post("/merge", response_model=PdfJobOut, summary="Merge dois PDFs e registra o job")
async def merge_pdf(
    file_a: UploadFile = File(..., alias="file_a"),
    file_b: UploadFile = File(..., alias="file_b"),
    auth: CurrentUser = Depends(require_permission("pdf", PDF_MERGE)),
) -> PdfJobOut:
    if not file_a.content_type or "pdf" not in file_a.content_type:
        raise ValidationError("file_a deve ser PDF")
    if not file_b.content_type or "pdf" not in file_b.content_type:
        raise ValidationError("file_b deve ser PDF")

    max_size_mb = int(auth.get_limit("max_file_size_mb", default=50))
    max_bytes = max_size_mb * 1024 * 1024

    content_a = await file_a.read()
    content_b = await file_b.read()

    if len(content_a) > max_bytes or len(content_b) > max_bytes:
        raise ValidationError(f"Arquivo excede o limite permitido de {max_size_mb} MB")

    job_id = str(uuid.uuid4())
    storage_dir = storage_dir_for_tenant(auth.tenant_id)
    path_a = os.path.join(storage_dir, f"{job_id}-a.pdf")
    path_b = os.path.join(storage_dir, f"{job_id}-b.pdf")

    save_file(path_a, content_a)
    save_file(path_b, content_b)

    job = await pdf_jobs_repository.create_job(
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
        await pdf_jobs_repository.fail_job(str(job["id"]), str(e))
        logger.error("Erro ao fazer merge — job=%s: %s", job["id"], e)
        raise BusinessRuleError(f"Erro no merge: {e}")

    name_a = (file_a.filename or "file_a").removesuffix(".pdf")
    name_b = (file_b.filename or "file_b").removesuffix(".pdf")
    result_name = f"{name_a}_{name_b}_merged.pdf"
    result_path = os.path.join(storage_dir, f"{job_id}-merged.pdf")

    save_file(result_path, merged_bytes)

    job = await pdf_jobs_repository.complete_job(str(job["id"]), result_path, result_name)

    logger.info(
        "PDF merge concluído — tenant=%s user=%s plan=%s job=%s",
        auth.tenant_id, auth.user_id, auth.plan_name, job["id"],
    )

    return job_to_out(job)


@router.get("/jobs", response_model=list[PdfJobOut], summary="Lista os merges do tenant")
async def list_jobs(
    auth: CurrentUser = Depends(require_permission("pdf")),
) -> list[PdfJobOut]:
    jobs = await pdf_jobs_repository.list_jobs(auth.tenant_id)
    return [job_to_out(j) for j in jobs]


@router.get("/jobs/{job_id}/download", summary="Baixa o PDF resultante de um merge")
async def download_job(
    job_id: str,
    auth: CurrentUser = Depends(require_permission("pdf")),
) -> StreamingResponse:
    job = await pdf_jobs_repository.get_job(job_id, auth.tenant_id)

    if not job:
        raise BusinessRuleError("Job não encontrado", status_code=404)
    if job["status"] != "completed":
        raise ValidationError("Job ainda não concluído")

    result_path = job.get("result_path")
    if not result_path or not file_exists(result_path):
        raise BusinessRuleError("Arquivo do resultado não encontrado", status_code=404)

    content = read_file(result_path)
    filename = job["result_name"] or "merged.pdf"

    return StreamingResponse(
        io.BytesIO(content),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )
