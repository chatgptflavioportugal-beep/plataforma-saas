-- Remove coluna features (flags) da tabela plans.
-- Capacidades do plano passam a ser controladas exclusivamente por
-- plan_version_modules e plan_version_module_limits.
ALTER TABLE plans DROP COLUMN IF EXISTS features;
