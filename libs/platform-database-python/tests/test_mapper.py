from dataclasses import dataclass

import pytest

from platform_database_python import DatabaseMappingException, column, row_to_object, rows_to_objects


@dataclass
class UserTenantTO:
    tenant_id: str = column("tenant_id")
    role: str = column("role")


def test_row_to_object_maps_matching_columns():
    row = {"tenant_id": "t-1", "role": "OWNER"}
    to = row_to_object(row, UserTenantTO)
    assert to == UserTenantTO(tenant_id="t-1", role="OWNER")


def test_row_to_object_is_case_insensitive():
    row = {"TENANT_ID": "t-1", "Role": "OWNER"}
    to = row_to_object(row, UserTenantTO)
    assert to == UserTenantTO(tenant_id="t-1", role="OWNER")


def test_row_to_object_raises_when_column_missing():
    row = {"tenant_id": "t-1"}
    with pytest.raises(DatabaseMappingException):
        row_to_object(row, UserTenantTO)


def test_rows_to_objects_maps_each_row():
    rows = [
        {"tenant_id": "t-1", "role": "OWNER"},
        {"tenant_id": "t-2", "role": "MEMBER"},
    ]
    result = rows_to_objects(rows, UserTenantTO)
    assert result == [
        UserTenantTO(tenant_id="t-1", role="OWNER"),
        UserTenantTO(tenant_id="t-2", role="MEMBER"),
    ]
