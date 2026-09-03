from dataclasses import dataclass

import pytest

from platform_database_python import DatabaseQuery, DatabaseQueryException, column


@dataclass
class UserTenantTO:
    tenant_id: str = column("tenant_id")
    role: str = column("role")


SQL = "SELECT tenant_id, role FROM user_tenants WHERE tenant_id = $1"


@pytest.mark.asyncio
async def test_fetch_all_maps_every_row(fake_connection):
    conn = fake_connection(rows_by_sql={SQL: [
        {"tenant_id": "t-1", "role": "OWNER"},
        {"tenant_id": "t-2", "role": "MEMBER"},
    ]})

    result = await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_all("t-1")

    assert result == [
        UserTenantTO(tenant_id="t-1", role="OWNER"),
        UserTenantTO(tenant_id="t-2", role="MEMBER"),
    ]
    assert conn.calls == [(SQL, ("t-1",))]


@pytest.mark.asyncio
async def test_fetch_optional_returns_none_for_zero_rows(fake_connection):
    conn = fake_connection(rows_by_sql={SQL: []})
    result = await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_optional("t-1")
    assert result is None


@pytest.mark.asyncio
async def test_fetch_optional_returns_single_row(fake_connection):
    conn = fake_connection(rows_by_sql={SQL: [{"tenant_id": "t-1", "role": "OWNER"}]})
    result = await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_optional("t-1")
    assert result == UserTenantTO(tenant_id="t-1", role="OWNER")


@pytest.mark.asyncio
async def test_fetch_optional_raises_for_multiple_rows(fake_connection):
    conn = fake_connection(rows_by_sql={SQL: [
        {"tenant_id": "t-1", "role": "OWNER"},
        {"tenant_id": "t-1", "role": "MEMBER"},
    ]})
    with pytest.raises(DatabaseQueryException):
        await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_optional("t-1")


@pytest.mark.asyncio
async def test_fetch_single_is_alias_of_fetch_optional(fake_connection):
    conn = fake_connection(rows_by_sql={SQL: [{"tenant_id": "t-1", "role": "OWNER"}]})
    result = await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_single("t-1")
    assert result == UserTenantTO(tenant_id="t-1", role="OWNER")


@pytest.mark.asyncio
async def test_fetch_raw_all_returns_plain_dicts(fake_connection):
    conn = fake_connection(rows_by_sql={SQL: [{"tenant_id": "t-1", "role": "OWNER"}]})
    result = await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_raw_all("t-1")
    assert result == [{"tenant_id": "t-1", "role": "OWNER"}]


@pytest.mark.asyncio
async def test_fetch_raw_optional_raises_for_multiple_rows(fake_connection):
    conn = fake_connection(rows_by_sql={SQL: [
        {"tenant_id": "t-1", "role": "OWNER"},
        {"tenant_id": "t-1", "role": "MEMBER"},
    ]})
    with pytest.raises(DatabaseQueryException):
        await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_raw_optional("t-1")


@pytest.mark.asyncio
async def test_execution_failure_raises_database_query_exception(fake_connection):
    conn = fake_connection(error_on_sql={SQL: RuntimeError("syntax error")})
    with pytest.raises(DatabaseQueryException):
        await DatabaseQuery().native_query(conn, SQL, UserTenantTO).fetch_all("t-1")
