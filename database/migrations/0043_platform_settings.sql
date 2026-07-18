-- Migration: 0043_platform_settings.sql
-- Configurações gerais da plataforma (chave/valor). Hoje guarda apenas o
-- período mínimo (em dias) para um perfil poder reutilizar o Trial de um
-- módulo já utilizado antes; a tabela é genérica para futuras configurações.

-- DOWN:
-- DROP TABLE IF EXISTS platform_settings;

CREATE TABLE platform_settings (
    key                 TEXT        PRIMARY KEY,
    value               TEXT        NOT NULL,
    description         TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_user_id  UUID
);

INSERT INTO platform_settings (key, value, description) VALUES
    ('trial_reuse_cooldown_days', '365',
     'Dias mínimos entre o fim de um Trial de um módulo e a liberação de um novo Trial do mesmo módulo, para o mesmo perfil.')
ON CONFLICT (key) DO NOTHING;
