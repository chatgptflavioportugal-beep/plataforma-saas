-- Migration: 0053_fix_module_icon_path_prefix.sql
-- Corrige icon_path gravados com o prefixo de source-tree "/public/icons/..."
-- para o caminho de URL correto "/icons/..." (que é o que o Vite realmente serve
-- a partir da pasta public/). Sem isso, os ícones não carregam no front-end.

-- DOWN:
-- (irreversível de forma segura - não há como distinguir quais linhas tinham
-- originalmente o prefixo /public antes desta correção)

UPDATE platform_modules
SET icon_path = regexp_replace(icon_path, '^/public/icons/', '/icons/')
WHERE icon_path LIKE '/public/icons/%';

UPDATE platform_module_services
SET icon_path = regexp_replace(icon_path, '^/public/icons/', '/icons/')
WHERE icon_path LIKE '/public/icons/%';
