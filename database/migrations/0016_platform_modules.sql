-- Migration: 0016_platform_modules.sql
-- Tabela de módulos e serviços/itens da plataforma

-- DOWN:
-- DROP TABLE IF EXISTS platform_module_services;
-- DROP TABLE IF EXISTS platform_modules;

CREATE TABLE platform_modules (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    slug        TEXT        UNIQUE NOT NULL,
    description TEXT,
    module_url  TEXT        NOT NULL,
    icon        TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order  INTEGER     NOT NULL DEFAULT 99,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_platform_modules_updated_at
    BEFORE UPDATE ON platform_modules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_platform_modules_slug ON platform_modules(slug);
CREATE INDEX idx_platform_modules_is_active ON platform_modules(is_active);
CREATE INDEX idx_platform_modules_sort_order ON platform_modules(sort_order);

-- ─── Serviços/Itens de cada módulo ──────────────────────────────────────────

CREATE TABLE platform_module_services (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id   UUID        NOT NULL REFERENCES platform_modules(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL,
    description TEXT,
    service_url TEXT        NOT NULL,
    icon        TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order  INTEGER     NOT NULL DEFAULT 99,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_module_service_slug UNIQUE (module_id, slug)
);

CREATE TRIGGER trg_platform_module_services_updated_at
    BEFORE UPDATE ON platform_module_services
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_pms_module_id ON platform_module_services(module_id);
CREATE INDEX idx_pms_is_active ON platform_module_services(is_active);
CREATE INDEX idx_pms_sort_order ON platform_module_services(sort_order);
