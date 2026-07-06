-- Migration: 0035_plan_limit_code_remove_plan_prefix.sql
-- Remove o prefixo do plano do code em plan_version_module_limits
-- Migra o formato de <planCode>.<moduleSlug>.<limitCode> para <moduleSlug>.<limitCode>
--
-- Motivo: o token de acesso ao módulo já representa exatamente o plano contratado,
-- então o code do limite deve identificar apenas o recurso dentro do módulo,
-- permanecendo estável entre upgrades/downgrades de plano.

-- DOWN:
-- UPDATE plan_version_module_limits pvml
-- SET code = p.code || '.' || pvml.code
-- FROM plan_version_modules pvm
-- JOIN plans p ON p.id = pvm.plan_id
-- WHERE pvml.plan_version_module_id = pvm.id;

UPDATE plan_version_module_limits pvml
SET code = pm.slug || '.' || substring(pvml.code FROM length(p.code || '.' || pm.slug || '.') + 1)
FROM plan_version_modules pvm
JOIN plans p ON p.id = pvm.plan_id
JOIN platform_modules pm ON pm.id = pvm.module_id
WHERE pvml.plan_version_module_id = pvm.id
  AND pvml.code IS NOT NULL
  AND pvml.code != ''
  AND pvml.code LIKE (p.code || '.' || pm.slug || '.%');
