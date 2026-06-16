-- Migration: adiciona route_key em platform_module_services
-- route_key é derivado do permissionKey (module.group?.service) com pontos e underscores trocados por hífen

ALTER TABLE platform_module_services
  ADD COLUMN IF NOT EXISTS route_key TEXT;

-- Gera route_key para todos os serviços existentes
-- Usa subquery para evitar referência ao alias da tabela alvo dentro do JOIN
UPDATE platform_module_services svc
SET route_key = computed.key
FROM (
  SELECT
    s.id,
    LOWER(
      REGEXP_REPLACE(
        REGEXP_REPLACE(
          CASE
            WHEN g.slug IS NOT NULL THEN m.slug || '.' || g.slug || '.' || s.slug
            ELSE m.slug || '.' || s.slug
          END,
          '[._\s]+', '-', 'g'
        ),
        '-+', '-', 'g'
      )
    ) AS key
  FROM platform_module_services s
  JOIN platform_modules m ON m.id = s.module_id
  LEFT JOIN platform_module_service_groups g ON g.id = s.service_group_id
) computed
WHERE svc.id = computed.id;

-- Garante que todos registros têm route_key antes de tornar NOT NULL
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM platform_module_services WHERE route_key IS NULL) THEN
    RAISE EXCEPTION 'Existem serviços sem route_key após a migração. Verifique os dados.';
  END IF;
END $$;

ALTER TABLE platform_module_services
  ALTER COLUMN route_key SET NOT NULL;

-- Unicidade global do route_key
ALTER TABLE platform_module_services
  ADD CONSTRAINT platform_module_services_route_key_key UNIQUE (route_key);

CREATE INDEX IF NOT EXISTS idx_platform_module_services_route_key
  ON platform_module_services (route_key);
