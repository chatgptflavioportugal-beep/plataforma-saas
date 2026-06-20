from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse

from auth.module_auth import ModuleAuthContext, require_module_token
from services.pdf_service import merge_pdfs
import io
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/pdf", tags=["PDF"])

_require_pdf_token = require_module_token("pdf")


@router.post(
    "/merge",
    summary="Merge dois PDFs em um único arquivo",
)
async def merge_pdf(
    file_a: UploadFile = File(..., alias="file_a"),
    file_b: UploadFile = File(..., alias="file_b"),
    auth: ModuleAuthContext = Depends(_require_pdf_token),
) -> StreamingResponse:
    auth.require_permission("module.pdf.merge-pdf")

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

    logger.info(
        "PDF merge solicitado — tenant=%s user=%s plan=%s",
        auth.tenant_id, auth.user_id, auth.plan_name,
    )

    try:
        merged_bytes = merge_pdfs(content_a, content_b)
    except Exception as e:
        logger.error("Erro ao fazer merge de PDFs: %s", str(e))
        raise HTTPException(status_code=500, detail=f"Erro no merge: {str(e)}")

    logger.info(
        "PDF merge concluído — tenant=%s tamanho=%d bytes", auth.tenant_id, len(merged_bytes)
    )

    return StreamingResponse(
        io.BytesIO(merged_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": "attachment; filename=merged.pdf"},
    )
