-- Migration: 0012_plan_most_popular.sql
-- Adiciona flag is_most_popular aos planos

ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS is_most_popular BOOLEAN NOT NULL DEFAULT FALSE;

-- Garante que apenas um plano ativo/atual pode ser o mais popular
-- (enforced na aplicação; índice partial para visibilidade)
CREATE UNIQUE INDEX IF NOT EXISTS idx_plans_most_popular
    ON plans(is_most_popular)
    WHERE is_most_popular = TRUE AND is_current_version = TRUE AND is_active = TRUE;

-- Marca 'pro' como mais popular por padrão
UPDATE plans
SET is_most_popular = TRUE
WHERE code = 'pro'
  AND is_current_version = TRUE
  AND is_active = TRUE;

-- DOWN:
-- DROP INDEX IF EXISTS idx_plans_most_popular;
-- ALTER TABLE plans DROP COLUMN IF EXISTS is_most_popular;
