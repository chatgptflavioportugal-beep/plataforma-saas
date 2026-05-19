-- Migration: 0017_drop_service_url_from_module_services.sql
-- Remove o campo service_url da tabela platform_module_services
-- O link/rota pertence apenas ao módulo (platform_modules.module_url)

-- DOWN:
-- ALTER TABLE platform_module_services ADD COLUMN service_url TEXT;

ALTER TABLE platform_module_services DROP COLUMN IF EXISTS service_url;
