import time
import uuid
from unittest.mock import AsyncMock, patch

import jwt
import pytest
from fastapi.testclient import TestClient

from main import app

SECRET = "test-module-access-token-secret"
TENANT_ID = str(uuid.uuid4())
USER_ID = str(uuid.uuid4())


def make_module_token(*, permissions: list[str], module_slug: str = "whatsapp") -> str:
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
        "limits": {},
        "permissionsVersion": 1,
        "iat": now,
        "exp": now + 300,
    }
    return jwt.encode(payload, SECRET, algorithm="HS256")


@pytest.fixture
def client(monkeypatch):
    from config.config import settings

    monkeypatch.setattr(settings, "MODULE_ACCESS_TOKEN_SECRET", SECRET)

    with (
        patch("main.init_pool", new=AsyncMock()),
        patch("main.close_pool", new=AsyncMock()),
    ):
        with TestClient(app) as c:
            yield c


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_send_message_without_token(client):
    response = client.post("/whatsapp/messages", json={"to": "+5511999999999", "text": "oi"})
    assert response.status_code == 422  # header Authorization ausente


def test_send_message_without_permission(client):
    token = make_module_token(permissions=[])
    response = client.post(
        "/whatsapp/messages",
        headers={"Authorization": f"Bearer {token}"},
        json={"to": "+5511999999999", "text": "oi"},
    )
    assert response.status_code == 403


def test_send_message_not_implemented(client):
    token = make_module_token(permissions=["whatsapp-send"])
    response = client.post(
        "/whatsapp/messages",
        headers={"Authorization": f"Bearer {token}"},
        json={"to": "+5511999999999", "text": "oi"},
    )
    assert response.status_code == 501
