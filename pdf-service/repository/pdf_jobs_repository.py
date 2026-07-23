import uuid

from db.database import get_pool
from enums.pdf_job_status import PdfJobStatus

_SELECT_COLS = """
    id, tenant_id, user_id, status,
    file_a_name, file_b_name,
    result_name, error_message,
    created_at, updated_at
"""

_SELECT_COLS_WITH_PATH = """
    id, tenant_id, user_id, status,
    file_a_name, file_b_name,
    result_path, result_name, error_message,
    created_at, updated_at
"""


async def create_job(
    job_id: str,
    tenant_id: str,
    user_id: str,
    file_a_name: str,
    file_b_name: str,
    file_a_path: str,
    file_b_path: str,
) -> dict:
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            f"""
            INSERT INTO pdf_jobs
                (id, tenant_id, user_id, status,
                 file_a_name, file_b_name, file_a_path, file_b_path)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            RETURNING {_SELECT_COLS}
            """,
            uuid.UUID(job_id),
            uuid.UUID(tenant_id),
            uuid.UUID(user_id),
            PdfJobStatus.PROCESSING.value,
            file_a_name,
            file_b_name,
            file_a_path,
            file_b_path,
        )
        return dict(row)


async def complete_job(job_id: str, result_path: str, result_name: str) -> dict:
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            f"""
            UPDATE pdf_jobs
            SET status = $2, result_path = $3, result_name = $4
            WHERE id = $1
            RETURNING {_SELECT_COLS}
            """,
            uuid.UUID(job_id),
            PdfJobStatus.COMPLETED.value,
            result_path,
            result_name,
        )
        return dict(row)


async def fail_job(job_id: str, error_message: str) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE pdf_jobs SET status = $2, error_message = $3 WHERE id = $1",
            uuid.UUID(job_id),
            PdfJobStatus.FAILED.value,
            error_message,
        )


async def list_jobs(tenant_id: str) -> list[dict]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            f"""
            SELECT {_SELECT_COLS}
            FROM pdf_jobs
            WHERE tenant_id = $1
            ORDER BY created_at DESC
            LIMIT 50
            """,
            uuid.UUID(tenant_id),
        )
        return [dict(r) for r in rows]


async def count_jobs_today(tenant_id: str) -> int:
    pool = await get_pool()
    async with pool.acquire() as conn:
        return await conn.fetchval(
            """
            SELECT COUNT(*) FROM pdf_jobs
            WHERE tenant_id = $1 AND created_at >= date_trunc('day', now())
            """,
            uuid.UUID(tenant_id),
        )


async def get_job(job_id: str, tenant_id: str) -> dict | None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            f"""
            SELECT {_SELECT_COLS_WITH_PATH}
            FROM pdf_jobs
            WHERE id = $1 AND tenant_id = $2
            """,
            uuid.UUID(job_id),
            uuid.UUID(tenant_id),
        )
        return dict(row) if row else None
