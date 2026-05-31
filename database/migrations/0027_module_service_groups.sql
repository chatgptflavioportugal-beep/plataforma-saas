-- Migration: 0027_module_service_groups.sql
-- Grupos de serviços dentro de módulos para organização hierárquica de permissões

-- DOWN:
-- ALTER TABLE platform_module_services DROP COLUMN IF EXISTS service_group_id;
-- DROP TABLE IF EXISTS platform_module_service_groups;

CREATE TABLE platform_module_service_groups (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id   UUID        NOT NULL REFERENCES platform_modules(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    description TEXT,
    icon_path   TEXT,
    sort_order  INTEGER     NOT NULL DEFAULT 99,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_platform_module_service_groups_updated_at
    BEFORE UPDATE ON platform_module_service_groups
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_pmsg_module_id   ON platform_module_service_groups(module_id);
CREATE INDEX idx_pmsg_status      ON platform_module_service_groups(status);
CREATE INDEX idx_pmsg_sort_order  ON platform_module_service_groups(sort_order);

ALTER TABLE platform_module_services
    ADD COLUMN service_group_id UUID REFERENCES platform_module_service_groups(id) ON DELETE SET NULL;

CREATE INDEX idx_pms_service_group_id ON platform_module_services(service_group_id);
