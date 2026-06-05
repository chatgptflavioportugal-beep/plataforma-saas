-- Migration: 0030_service_group_slug.sql
-- Adiciona coluna slug aos grupos de serviços para compor o código técnico dos serviços
-- Novo formato: <moduleSlug>.<groupSlug>.<serviceSlug>

-- DOWN:
-- ALTER TABLE platform_module_service_groups DROP CONSTRAINT IF EXISTS uq_pmsg_module_slug;
-- DROP INDEX IF EXISTS idx_pmsg_slug;
-- ALTER TABLE platform_module_service_groups DROP COLUMN IF EXISTS slug;

CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE platform_module_service_groups ADD COLUMN slug TEXT;

-- Popula slug a partir do nome usando a mesma lógica de normalização do sistema
UPDATE platform_module_service_groups
SET slug = regexp_replace(
    regexp_replace(
      lower(unaccent(name)),
      '[^a-z0-9\s_-]', '', 'g'
    ),
    '\s+', '-', 'g'
  );

ALTER TABLE platform_module_service_groups ALTER COLUMN slug SET NOT NULL;
ALTER TABLE platform_module_service_groups ADD CONSTRAINT uq_pmsg_module_slug UNIQUE (module_id, slug);
CREATE INDEX idx_pmsg_slug ON platform_module_service_groups(slug);
