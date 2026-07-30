import io

from fastapi import APIRouter, Depends, File, UploadFile
from fastapi.responses import StreamingResponse

from exceptions.business import BusinessRuleError
from exceptions.validation import ValidationError
from permissions.constants import MODULE_PDF_MERGE
from platform_security import ModuleContext, module_security
from repository import pdf_jobs_repository
from schemas.pdf_job import PdfJobOut, job_to_out
from services.merge import merge_service
from storage.local_storage import file_exists, read_file

router = APIRouter(prefix="/pdf", tags=["PDF"])


@router.post("/merge", response_model=PdfJobOut, summary="Merge dois PDFs e registra o job")
async def merge_pdf(
    file_a: UploadFile = File(..., alias="file_a"),
    file_b: UploadFile = File(..., alias="file_b"),
    auth: ModuleContext = Depends(module_security("pdf", MODULE_PDF_MERGE)),
) -> PdfJobOut:
    return await merge_service.execute(file_a, file_b, auth)


@router.get("/jobs", response_model=list[PdfJobOut], summary="Lista os merges do tenant")
async def list_jobs(
    auth: ModuleContext = Depends(module_security("pdf")),
) -> list[PdfJobOut]:
    jobs = await pdf_jobs_repository.list_jobs(auth.tenant_id)
    return [job_to_out(j) for j in jobs]


@router.get("/jobs/{job_id}/download", summary="Baixa o PDF resultante de um merge")
async def download_job(
    job_id: str,
    auth: ModuleContext = Depends(module_security("pdf")),
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
