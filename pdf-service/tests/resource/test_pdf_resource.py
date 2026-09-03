import io
import time
import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

import jwt
import pytest
from fastapi.testclient import TestClient

from main import app

SECRET = "test-module-access-token-secret"
TENANT_ID = str(uuid.uuid4())
USER_ID = str(uuid.uuid4())


def make_module_token(*, permissions: list[str], module_slug: str = "pdf") -> str:
    now = int(time.time())
    payload = {
        "tokenType": "MODULE_ACCESS",
        "sub": USER_ID,
        "tenantId": TENANT_ID,
        "moduleId": str(uuid.uuid4()),
        "moduleSlug": module_slug,
        "planName": "pro",
        "accessSource": "plan",
        "permissions": permissions,
        "limits": {"max-file-size": 50},
        "permissionsVersion": 1,
        "iat": now,
        "exp": now + 300,
    }
    return jwt.encode(payload, SECRET, algorithm="HS256")


def make_minimal_pdf() -> bytes:
    from pypdf import PdfWriter

    writer = PdfWriter()
    writer.add_blank_page(width=612, height=792)
    buf = io.BytesIO()
    writer.write(buf)
    buf.seek(0)
    return buf.read()


def _job_row(**overrides) -> dict:
    now = datetime.now(timezone.utc)
    row = {
        "id": uuid.uuid4(),
        "tenant_id": uuid.UUID(TENANT_ID),
        "user_id": uuid.UUID(USER_ID),
        "status": "processing",
        "file_a_name": "a.pdf",
        "file_b_name": "b.pdf",
        "result_name": None,
        "error_message": None,
        "created_at": now,
        "updated_at": now,
    }
    row.update(overrides)
    return row


@pytest.fixture
def client(monkeypatch, tmp_path):
    from config.config import settings
    from platform_security_python.config import settings as security_settings

    monkeypatch.setattr(security_settings, "MODULE_ACCESS_TOKEN_SECRET", SECRET)
    monkeypatch.setattr(settings, "PDF_STORAGE_PATH", str(tmp_path))

    with (
        patch("main.init_pool", new=AsyncMock()),
        patch("main.close_pool", new=AsyncMock()),
    ):
        with TestClient(app) as c:
            yield c


@pytest.fixture(autouse=True)
def mock_repository():
    created = _job_row()
    completed = _job_row(id=created["id"], status="completed", result_name="a_b_merged.pdf")

    with (
        patch("dao.pdf_jobs_dao.create_job", new=AsyncMock(return_value=created)),
        patch("dao.pdf_jobs_dao.complete_job", new=AsyncMock(return_value=completed)),
        patch("dao.pdf_jobs_dao.fail_job", new=AsyncMock()),
        patch("dao.pdf_jobs_dao.list_jobs", new=AsyncMock(return_value=[created])),
        patch("dao.pdf_jobs_dao.count_jobs_today", new=AsyncMock(return_value=0)),
    ):
        yield


def test_merge_success(client):
    pdf = make_minimal_pdf()
    token = make_module_token(permissions=["pdf-merge"])

    response = client.post(
        "/pdf/merge",
        headers={"Authorization": f"Bearer {token}"},
        files={
            "file_a": ("a.pdf", pdf, "application/pdf"),
            "file_b": ("b.pdf", pdf, "application/pdf"),
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "completed"
    assert body["tenant_id"] == TENANT_ID


def test_merge_without_token(client):
    pdf = make_minimal_pdf()

    response = client.post(
        "/pdf/merge",
        files={
            "file_a": ("a.pdf", pdf, "application/pdf"),
            "file_b": ("b.pdf", pdf, "application/pdf"),
        },
    )

    assert response.status_code == 422  # header Authorization ausente


def test_merge_without_permission(client):
    pdf = make_minimal_pdf()
    token = make_module_token(permissions=[])

    response = client.post(
        "/pdf/merge",
        headers={"Authorization": f"Bearer {token}"},
        files={
            "file_a": ("a.pdf", pdf, "application/pdf"),
            "file_b": ("b.pdf", pdf, "application/pdf"),
        },
    )

    assert response.status_code == 403


def test_list_jobs_requires_only_valid_token(client):
    token = make_module_token(permissions=[])

    response = client.get("/pdf/jobs", headers={"Authorization": f"Bearer {token}"})

    assert response.status_code == 200
    assert len(response.json()) == 1
