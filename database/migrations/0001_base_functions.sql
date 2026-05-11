-- Migration: 0001_base_functions.sql
-- Funções utilitárias base

-- DOWN:
-- DROP FUNCTION IF EXISTS update_updated_at_column;

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
