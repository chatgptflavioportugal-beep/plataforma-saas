from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from dependencies import get_tenant_id, get_user_id, verify_internal_token
from services.pdf_service import merge_pdfs
import io
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/pdf", tags=["PDF"])


@router.post(
    "/merge",
    summary="Merge dois PDFs em um único arquivo",
    dependencies=[Depends(verify_internal_token)],
)
async def merge_pdf(
    file_a: UploadFile = File(..., alias="file_a"),
    file_b: UploadFile = File(..., alias="file_b"),
    tenant_id: str = Depends(get_tenant_id),
    user_id: str = Depends(get_user_id),
) -> StreamingResponse:
    if not file_a.content_type or "pdf" not in file_a.content_type:
        raise HTTPException(status_code=400, detail="file_a deve ser PDF")
    if not file_b.content_type or "pdf" not in file_b.content_type:
        raise HTTPException(status_code=400, detail="file_b deve ser PDF")

    logger.info("PDF merge solicitado — tenant=%s user=%s", tenant_id, user_id)

    content_a = await file_a.read()
    content_b = await file_b.read()

    try:
        merged_bytes = merge_pdfs(content_a, content_b)
    except Exception as e:
        logger.error("Erro ao fazer merge de PDFs: %s", str(e))
        raise HTTPException(status_code=500, detail=f"Erro no merge: {str(e)}")

    logger.info(
        "PDF merge concluído — tenant=%s tamanho=%d bytes", tenant_id, len(merged_bytes)
    )

    return StreamingResponse(
        io.BytesIO(merged_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": "attachment; filename=merged.pdf"},
    )
