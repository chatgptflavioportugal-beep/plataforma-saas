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


@router.post(
    "/merge",
    response_model=PdfJobOut,
    status_code=200,
    summary="Faz o merge de dois PDFs e registra o job",
    description=(
        "Recebe dois arquivos PDF (`file_a`, `file_b`) via multipart/form-data e gera um "
        "único PDF resultante, concatenando as páginas de `file_a` seguidas das páginas de "
        "`file_b`. A operação é síncrona: o merge é processado e o job já é retornado no "
        "estado final (`completed` ou `failed`). Antes de processar, valida o limite diário "
        "de merges e o tamanho máximo de arquivo definidos nas claims de limite do "
        "ModuleAccessToken (`daily-merges`, `max-file-size`). O resultado fica disponível "
        "para download em `GET /pdf/jobs/{job_id}/download`. Este endpoint pertence ao "
        "contexto do módulo PDF do cliente; não realiza nenhuma operação administrativa."
    ),
    response_description="Job de merge criado, com o status final do processamento.",
    responses={
        200: {"description": "Merge realizado com sucesso (ou job marcado como falho, com `error_message` preenchido)."},
        400: {"description": "Arquivos ausentes/inválidos, PDFs corrompidos ou limite diário de merges atingido."},
        401: {"description": "ModuleAccessToken ausente, inválido, expirado ou emitido para outro módulo."},
        403: {"description": "Token válido, porém sem a permissão `pdf-merge`."},
        422: {"description": "Corpo da requisição não segue o formato multipart/form-data esperado."},
    },
)
async def merge_pdf(
    file_a: UploadFile = File(..., alias="file_a", description="Primeiro arquivo PDF; suas páginas entram primeiro no resultado."),
    file_b: UploadFile = File(..., alias="file_b", description="Segundo arquivo PDF; suas páginas são anexadas após as de `file_a`."),
    auth: ModuleContext = Depends(module_security("pdf", MODULE_PDF_MERGE)),
) -> PdfJobOut:
    return await merge_service.execute(file_a, file_b, auth)


@router.get(
    "/jobs",
    response_model=list[PdfJobOut],
    summary="Lista os jobs de merge do tenant",
    description=(
        "Operação exclusivamente de consulta. Retorna todos os jobs de merge (concluídos, "
        "com falha ou em qualquer outro estado registrado) pertencentes ao tenant identificado "
        "pelo ModuleAccessToken. Não modifica dados nem exige permissão específica além da "
        "posse de um token válido para o módulo `pdf`."
    ),
    response_description="Lista de jobs de merge do tenant, do mais recente ao mais antigo conforme o repositório.",
    responses={
        200: {"description": "Lista retornada com sucesso (pode ser vazia)."},
        401: {"description": "ModuleAccessToken ausente, inválido, expirado ou emitido para outro módulo."},
    },
)
async def list_jobs(
    auth: ModuleContext = Depends(module_security("pdf")),
) -> list[PdfJobOut]:
    jobs = await pdf_jobs_repository.list_jobs(auth.tenant_id)
    return [job_to_out(j) for j in jobs]


@router.get(
    "/jobs/{job_id}/download",
    summary="Baixa o PDF resultante de um merge concluído",
    description=(
        "Retorna o binário (`application/pdf`) do resultado de um job de merge previamente "
        "concluído, como anexo para download. O job precisa pertencer ao tenant do token e "
        "estar no status `completed`; jobs em outro status ou o arquivo físico ausente do "
        "storage resultam em erro. Operação exclusivamente de consulta, não altera o job."
    ),
    response_description="Arquivo PDF resultante do merge, como `attachment`.",
    responses={
        200: {"description": "Arquivo PDF retornado com sucesso.", "content": {"application/pdf": {}}},
        400: {"description": "Job encontrado, porém ainda não concluído."},
        401: {"description": "ModuleAccessToken ausente, inválido, expirado ou emitido para outro módulo."},
        404: {"description": "Job não encontrado para o tenant, ou arquivo do resultado ausente no storage."},
    },
)
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
