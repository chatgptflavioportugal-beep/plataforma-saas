import os

os.environ.setdefault("MODULE_ACCESS_TOKEN_SECRET", "test-module-access-token-secret")

import pytest

from platform_security.config import settings
from helpers import SECRET


@pytest.fixture(autouse=True)
def module_secret(monkeypatch):
    monkeypatch.setattr(settings, "MODULE_ACCESS_TOKEN_SECRET", SECRET)
