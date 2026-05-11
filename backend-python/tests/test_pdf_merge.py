import io
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch
from main import app

VALID_TOKEN = "test-internal-token"


@pytest.fixture(autouse=True)
def set_env(monkeypatch):
    monkeypatch.setenv("INTERNAL_TOKEN", VALID_TOKEN)


def make_minimal_pdf() -> bytes:
    from pypdf import PdfWriter
    writer = PdfWriter()
    writer.add_blank_page(width=612, height=792)
    buf = io.BytesIO()
    writer.write(buf)
    buf.seek(0)
    return buf.read()


@pytest.fixture
def client():
    from config import settings
    with patch.object(settings, "INTERNAL_TOKEN", VALID_TOKEN):
        with TestClient(app) as c:
            yield c


def test_merge_success(client):
    pdf = make_minimal_pdf()
    response = client.post(
        "/pdf/merge",
        headers={
            "X-Internal-Token": VALID_TOKEN,
            "X-Tenant-ID": "tenant-uuid",
            "X-User-ID": "user-uuid",
        },
        files={
            "file_a": ("a.pdf", pdf, "application/pdf"),
            "file_b": ("b.pdf", pdf, "application/pdf"),
        },
    )
    assert response.status_code == 200
    assert response.headers["content-type"] == "application/pdf"


def test_merge_no_token(client):
    pdf = make_minimal_pdf()
    response = client.post(
        "/pdf/merge",
        headers={"X-Tenant-ID": "t", "X-User-ID": "u"},
        files={
            "file_a": ("a.pdf", pdf, "application/pdf"),
            "file_b": ("b.pdf", pdf, "application/pdf"),
        },
    )
    assert response.status_code == 422


def test_merge_invalid_token(client):
    pdf = make_minimal_pdf()
    response = client.post(
        "/pdf/merge",
        headers={
            "X-Internal-Token": "wrong-token",
            "X-Tenant-ID": "t",
            "X-User-ID": "u",
        },
        files={
            "file_a": ("a.pdf", pdf, "application/pdf"),
            "file_b": ("b.pdf", pdf, "application/pdf"),
        },
    )
    assert response.status_code == 401
