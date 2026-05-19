-- Migration: 0018_rename_icon_to_icon_path.sql
-- Substitui o campo icon (SVG inline) por icon_path (caminho do arquivo .svg físico)

-- DOWN:
-- ALTER TABLE platform_modules RENAME COLUMN icon_path TO icon;
-- ALTER TABLE platform_module_services RENAME COLUMN icon_path TO icon;

ALTER TABLE platform_modules RENAME COLUMN icon TO icon_path;
ALTER TABLE platform_module_services RENAME COLUMN icon TO icon_path;
