-- Migration: 0052_subscription_admin_permission_grants.sql
--
-- Complementa 0051_least_privilege_roles.sql: subscription-service passou a
-- checar permissão administrativa (requireAdminPermission) lendo
-- admin_access_levels/admin_access_level_permissions diretamente, em vez de
-- chamar o endpoint /api/v1/admin/permissions/check do admin-service via
-- HTTP. Falta só este GRANT para a role dedicada (mesmo scaffolding do 0051 —
-- sem efeito até os passos manuais daquela migration serem aplicados).
--
-- DOWN:
-- REVOKE SELECT ON admin_access_levels, admin_access_level_permissions
--   FROM role_subscription_service;

GRANT SELECT ON admin_access_levels, admin_access_level_permissions
    TO role_subscription_service;
