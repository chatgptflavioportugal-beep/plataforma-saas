-- Migration: 0038_plan_version_module_trial.sql
-- Adiciona configuração de Trial por plano de módulo (plan_version_modules).
-- Cada módulo dentro de uma versão de plano pode ou não oferecer Trial,
-- com duração própria em dias. Congelado na assinatura no momento da contratação
-- (profile_module_subscriptions), assim como já ocorre com preços e limites.

-- DOWN:
-- ALTER TABLE plan_version_modules DROP CONSTRAINT IF EXISTS chk_pvm_trial_days;
-- ALTER TABLE plan_version_modules DROP COLUMN IF EXISTS trial_enabled;
-- ALTER TABLE plan_version_modules DROP COLUMN IF EXISTS trial_days;

ALTER TABLE plan_version_modules
    ADD COLUMN IF NOT EXISTS trial_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS trial_days    INTEGER;

ALTER TABLE plan_version_modules
    ADD CONSTRAINT chk_pvm_trial_days CHECK (
        (trial_enabled = FALSE AND trial_days IS NULL) OR
        (trial_enabled = TRUE AND trial_days BETWEEN 1 AND 365)
    );
