-- Adiciona controle de versão de permissões em user_tenants.
-- Incrementar este campo ao alterar: nível de acesso, assinatura, permissões do membro.
-- O ModuleAccessToken carrega a versão vigente; ao validar, backend rejeita se diferirem.

ALTER TABLE user_tenants
  ADD COLUMN IF NOT EXISTS permissions_version INTEGER NOT NULL DEFAULT 1;

-- Função auxiliar para incrementar a versão de um membro específico
CREATE OR REPLACE FUNCTION increment_member_permissions_version(p_user_id UUID, p_tenant_id UUID)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
  UPDATE user_tenants
     SET permissions_version = permissions_version + 1
   WHERE user_id = p_user_id
     AND tenant_id = p_tenant_id;
END;
$$;
