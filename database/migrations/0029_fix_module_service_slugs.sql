-- Migration: 0029_fix_module_service_slugs.sql
-- Corrige slugs de módulos e serviços gerados incorretamente
-- (acentos removiam a letra junto com o acento em vez de só o acento)

-- Exemplo do problema: "Imobiliária" → "imobiliria" (errado) → "imobiliaria" (correto)

-- Para usar esta migration o schema Supabase precisa ter a extensão unaccent
-- (disponível por padrão no Supabase)
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Função auxiliar para normalizar slug a partir do nome
CREATE OR REPLACE FUNCTION normalize_slug(val TEXT) RETURNS TEXT AS $$
BEGIN
  RETURN regexp_replace(
    regexp_replace(
      lower(unaccent(val)),
      '[^a-z0-9\s_-]', '', 'g'
    ),
    '\s+', '-', 'g'
  );
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Corrige slugs de módulos onde o slug atual difere do slug normalizado correto
-- Apenas atualiza registros cujo slug parece incorreto (não bate com a versão corrigida)
UPDATE platform_modules
SET slug = normalize_slug(name)
WHERE slug != normalize_slug(name)
  AND normalize_slug(name) != '';

-- Corrige slugs de serviços onde o slug atual difere do slug normalizado correto
UPDATE platform_module_services
SET slug = normalize_slug(name)
WHERE slug != normalize_slug(name)
  AND normalize_slug(name) != '';

-- Remove a função auxiliar (não precisa persistir)
DROP FUNCTION normalize_slug(TEXT);
