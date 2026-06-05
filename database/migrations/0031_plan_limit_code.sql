-- Migration: 0031_plan_limit_code.sql
-- Renomeia limit_key para code em plan_version_module_limits
-- Migra o formato de <limitCode> para <planCode>.<moduleSlug>.<limitCode>

-- DOWN:
-- UPDATE plan_version_module_limits pvml ... (complexo, não reverter automaticamente)
-- ALTER TABLE plan_version_module_limits RENAME COLUMN code TO limit_key;

ALTER TABLE plan_version_module_limits RENAME COLUMN limit_key TO code;

-- Atualiza registros existentes para o novo formato composto
-- Formato antigo: max-file-size
-- Formato novo:   start.pdf.max-file-size
UPDATE plan_version_module_limits pvml
SET code = p.code || '.' || pm.slug || '.' || pvml.code
FROM plan_version_modules pvm
JOIN plans p ON p.id = pvm.plan_id
JOIN platform_modules pm ON pm.id = pvm.module_id
WHERE pvml.plan_version_module_id = pvm.id
  AND pvml.code IS NOT NULL
  AND pvml.code != ''
  AND pvml.code NOT LIKE (p.code || '.%');
