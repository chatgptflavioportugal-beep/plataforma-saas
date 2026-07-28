-- Migration: 0050_feature_flags.sql
-- Feature Flags da plataforma — cadastro administrativo simples (chave/valor
-- booleano), dono exclusivo é o Admin Service (ver AdminFeatureFlagResource).
-- Demais serviços podem apenas ler.

-- DOWN:
-- DROP TABLE IF EXISTS feature_flags;

CREATE TABLE IF NOT EXISTS feature_flags (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key               TEXT NOT NULL UNIQUE,
    name              TEXT NOT NULL,
    description       TEXT,
    is_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id UUID
);

CREATE INDEX IF NOT EXISTS idx_feature_flags_key ON feature_flags (key);
