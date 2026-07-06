import logging
import os
import uuid
from pathlib import Path

from fastapi import UploadFile

from exceptions.business import BusinessRuleError
from modules.pdf.repository import pdf_jobs_repository
from modules.pdf.schemas.pdf_job import PdfJobOut
from modules.pdf.services.merge.merge_processor import process_merge
from modules.pdf.services.merge.merge_response import build_response
from modules.pdf.services.merge.merge_validator import (
    validate_files,
    validate_limits,
    validate_request,
)
from security.dependencies.current_user import CurrentUser
from services.storage.local_storage import save_file, storage_dir_for_tenant
from utils.text import normalize_filename

logger = logging.getLogger(__name__)


async def execute(file_a: UploadFile, file_b: UploadFile, auth: CurrentUser) -> PdfJobOut:
    validate_request(file_a, file_b)

    content_a = await file_a.read()
    content_b = await file_b.read()

    validate_limits(auth, content_a, content_b)
    validate_files(content_a, content_b)

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
        merged_bytes = process_merge(content_a, content_b)
    except Exception as e:
        await pdf_jobs_repository.fail_job(str(job["id"]), str(e))
        logger.error("Erro ao fazer merge — job=%s: %s", job["id"], e)
        raise BusinessRuleError(f"Erro no merge: {e}")

    name_a = Path(file_a.filename or "file_a.pdf").stem
    name_b = Path(file_b.filename or "file_b.pdf").stem
    result_name = normalize_filename(f"{name_a}_{name_b}_merged.pdf")
    result_path = os.path.join(storage_dir, f"{job_id}-merged.pdf")

    save_file(result_path, merged_bytes)

    job = await pdf_jobs_repository.complete_job(str(job["id"]), result_path, result_name)

    logger.info(
        "PDF merge concluído — tenant=%s user=%s plan=%s job=%s",
        auth.tenant_id, auth.user_id, auth.plan_name, job["id"],
    )

    return build_response(job)
