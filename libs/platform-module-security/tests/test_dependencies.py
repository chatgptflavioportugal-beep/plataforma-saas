from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from helpers import make_module_token
from platform_security.context import ModuleContext
from platform_security.dependencies import module_security

app = FastAPI()


@app.get("/protected")
async def protected(auth: ModuleContext = Depends(module_security("pdf"))):
    return {"tenant_id": auth.tenant_id}


@app.get("/protected-with-permission")
async def protected_with_permission(
    auth: ModuleContext = Depends(module_security("pdf", "pdf-merge")),
):
    return {"ok": True}


client = TestClient(app)


def test_missing_authorization_header_returns_422():
    response = client.get("/protected")
    assert response.status_code == 422


def test_malformed_authorization_header_returns_401():
    response = client.get("/protected", headers={"Authorization": "Token abc"})
    assert response.status_code == 401


def test_valid_token_returns_200():
    token = make_module_token(module_slug="pdf")
    response = client.get("/protected", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    assert response.json()["tenant_id"]


def test_wrong_module_slug_returns_403():
    token = make_module_token(module_slug="whatsapp")
    response = client.get("/protected", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 403


def test_missing_permission_returns_403():
    token = make_module_token(module_slug="pdf", permissions=[])
    response = client.get(
        "/protected-with-permission",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 403


def test_with_permission_returns_200():
    token = make_module_token(module_slug="pdf", permissions=["pdf-merge"])
    response = client.get(
        "/protected-with-permission",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
