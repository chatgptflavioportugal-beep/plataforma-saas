import os

from config.config import settings


def storage_dir_for_tenant(tenant_id: str) -> str:
    path = os.path.join(settings.PDF_STORAGE_PATH, tenant_id)
    os.makedirs(path, exist_ok=True)
    return path


def save_file(path: str, content: bytes) -> None:
    with open(path, "wb") as f:
        f.write(content)


def read_file(path: str) -> bytes:
    with open(path, "rb") as f:
        return f.read()


def file_exists(path: str) -> bool:
    return os.path.exists(path)
