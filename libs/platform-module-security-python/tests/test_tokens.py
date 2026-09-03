import pytest

from helpers import TENANT_ID, USER_ID, make_module_token
from platform_security_python.exceptions import (
    AuthenticationError,
    AuthorizationError,
    ExpiredTokenError,
    InvalidTokenError,
    ModuleMismatchError,
)
from platform_security_python.tokens import decode_module_token


def test_decode_valid_token():
    token = make_module_token(module_slug="pdf", permissions=["pdf-merge"], limits={"max-file-size": 50})
    context = decode_module_token(token, "pdf")

    assert context.user_id == USER_ID
    assert context.tenant_id == TENANT_ID
    assert context.module_slug == "pdf"
    assert context.plan_name == "pro"
    assert context.has_permission("pdf-merge")
    assert not context.has_permission("pdf-split")
    assert context.get_limit("max-file-size") == 50
    assert context.get_limit("missing", "default") == "default"


def test_decode_expired_token():
    token = make_module_token(module_slug="pdf", exp_delta=-10)

    with pytest.raises(ExpiredTokenError):
        decode_module_token(token, "pdf")


def test_decode_bad_signature():
    token = make_module_token(module_slug="pdf", secret="wrong-secret")

    with pytest.raises(InvalidTokenError):
        decode_module_token(token, "pdf")


def test_decode_malformed_token():
    with pytest.raises(InvalidTokenError):
        decode_module_token("not-a-jwt", "pdf")


def test_decode_wrong_token_type():
    token = make_module_token(module_slug="pdf", token_type="PROFILE_ACCESS")

    with pytest.raises(InvalidTokenError):
        decode_module_token(token, "pdf")


def test_decode_wrong_module_slug():
    token = make_module_token(module_slug="whatsapp")

    with pytest.raises(ModuleMismatchError):
        decode_module_token(token, "pdf")


def test_decode_missing_token():
    with pytest.raises(AuthenticationError):
        decode_module_token("", "pdf")


def test_require_permission_raises_when_missing():
    token = make_module_token(module_slug="pdf", permissions=[])
    context = decode_module_token(token, "pdf")

    with pytest.raises(AuthorizationError):
        context.require_permission("pdf-merge")


def test_exception_hierarchy():
    assert issubclass(ExpiredTokenError, AuthenticationError)
    assert issubclass(InvalidTokenError, AuthenticationError)
    assert issubclass(ModuleMismatchError, AuthorizationError)
