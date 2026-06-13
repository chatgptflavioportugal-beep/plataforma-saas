-- Migration: 0032_migrate_service_group_permissions.sql
-- Converte admin.services.groups.manage nas 5 permissões granulares de grupos de serviços

INSERT INTO admin_access_level_permissions (access_level_id, permission_key)
SELECT DISTINCT alp.access_level_id, v.perm_key
FROM admin_access_level_permissions alp
CROSS JOIN (VALUES
    ('admin.services.groups.view'),
    ('admin.services.groups.create'),
    ('admin.services.groups.edit'),
    ('admin.services.groups.activate'),
    ('admin.services.groups.deactivate')
) AS v(perm_key)
WHERE alp.permission_key = 'admin.services.groups.manage'
ON CONFLICT (access_level_id, permission_key) DO NOTHING;

DELETE FROM admin_access_level_permissions
WHERE permission_key = 'admin.services.groups.manage';
