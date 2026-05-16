package com.saas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class InvitationService {

    @Inject
    EntityManager em;

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:3000")
    String baseUrl;

    // ----------------------------------------------------------------
    // Listar membros ativos do tenant (com nome e e-mail)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMembers(UUID tenantId) {
        var rows = (List<Object[]>) em.createNativeQuery(
                "SELECT ut.user_id::text, up.full_name, au.email, ut.role, ut.created_at::text " +
                "FROM user_tenants ut " +
                "LEFT JOIN user_profiles up ON up.id = ut.user_id " +
                "LEFT JOIN auth.users au ON au.id = ut.user_id " +
                "WHERE ut.tenant_id = :tenantId AND ut.is_active = TRUE " +
                "ORDER BY ut.created_at ASC"
        ).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("user_id", row[0]);
            m.put("full_name", row[1]);
            m.put("email", row[2]);
            m.put("role", row[3]);
            m.put("joined_at", row[4]);
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Remover membro do tenant (soft delete em user_tenants)
    // Apenas owner pode remover qualquer membro.
    // Admin pode remover somente membros (não outros admins).
    // Ninguém remove a si mesmo nem o último owner.
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Transactional
    public void removeMember(UUID tenantId, UUID targetUserId, UUID requestingUserId, String requestingRole) {
        if (targetUserId.equals(requestingUserId)) {
            throw new BadRequestException("Você não pode remover a si mesmo");
        }

        // Coluna única → getResultList() retorna List<Object> (String diretamente)
        var targetRows = (List<Object>) em.createNativeQuery(
                "SELECT ut.role FROM user_tenants ut " +
                "WHERE ut.tenant_id = :tenantId AND ut.user_id = :userId AND ut.is_active = TRUE"
        ).setParameter("tenantId", tenantId).setParameter("userId", targetUserId).getResultList();

        if (targetRows.isEmpty()) throw new NotFoundException("Membro não encontrado");
        String targetRole = (String) targetRows.get(0);

        if ("owner".equals(targetRole)) {
            throw new ForbiddenException("Não é possível remover o proprietário da empresa");
        }
        if ("admin".equals(requestingRole) && "admin".equals(targetRole)) {
            throw new ForbiddenException("Administradores não podem remover outros administradores");
        }

        em.createNativeQuery(
                "UPDATE user_tenants SET is_active = FALSE, updated_at = NOW() " +
                "WHERE tenant_id = :tenantId AND user_id = :userId"
        ).setParameter("tenantId", tenantId).setParameter("userId", targetUserId).executeUpdate();
    }

    // ----------------------------------------------------------------
    // Listar convites do tenant (pendentes + histórico)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listInvitations(UUID tenantId) {
        var rows = (List<Object[]>) em.createNativeQuery(
                "SELECT i.id::text, i.email, i.role, i.status, i.expires_at::text, i.created_at::text " +
                "FROM invitations i " +
                "WHERE i.tenant_id = :tenantId " +
                "ORDER BY i.created_at DESC"
        ).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("email", row[1]);
            m.put("role", row[2]);
            m.put("status", row[3]);
            m.put("expires_at", row[4]);
            m.put("created_at", row[5]);
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Enviar convite
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> sendInvitation(UUID tenantId, String email, String role, UUID invitedBy) {
        String normalizedEmail = email.trim().toLowerCase();

        // Validar role
        if (!List.of("admin", "member", "finance").contains(role)) {
            throw new BadRequestException("Role inválido: " + role);
        }

        // Verificar se já é membro ativo (COUNT pode retornar Long ou BigInteger)
        long isMember = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_tenants ut " +
                "JOIN auth.users au ON au.id = ut.user_id " +
                "WHERE ut.tenant_id = :tenantId AND au.email = :email AND ut.is_active = TRUE"
        ).setParameter("tenantId", tenantId).setParameter("email", normalizedEmail).getSingleResult()).longValue();

        if (isMember > 0) {
            throw new BadRequestException("Este usuário já é membro da empresa");
        }

        // Verificar se já existe convite pendente
        long pendingExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM invitations " +
                "WHERE tenant_id = :tenantId AND email = :email AND status = 'pending' AND expires_at > NOW()"
        ).setParameter("tenantId", tenantId).setParameter("email", normalizedEmail).getSingleResult()).longValue();

        if (pendingExists > 0) {
            throw new BadRequestException("Já existe um convite pendente para este e-mail");
        }

        // Buscar nome da empresa
        String tenantName = (String) em.createNativeQuery(
                "SELECT name FROM tenants WHERE id = :tenantId"
        ).setParameter("tenantId", tenantId).getSingleResult();

        // Criar convite (token gerado pelo DEFAULT da tabela)
        em.createNativeQuery(
                "INSERT INTO invitations (tenant_id, invited_by, email, role) " +
                "VALUES (:tenantId, :invitedBy, :email, :role)"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("invitedBy", invitedBy)
        .setParameter("email", normalizedEmail)
        .setParameter("role", role)
        .executeUpdate();

        // Buscar o convite recem-criado
        var result = (Object[]) em.createNativeQuery(
                "SELECT id::text, token, expires_at::text FROM invitations " +
                "WHERE tenant_id = :tenantId AND email = :email AND status = 'pending' " +
                "ORDER BY created_at DESC LIMIT 1"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("email", normalizedEmail)
        .getSingleResult();

        String invitationId = (String) result[0];
        String token = (String) result[1];
        String expiresAt = (String) result[2];
        String inviteLink = baseUrl + "/invite/accept?token=" + token;

        // Enviar e-mail (loga o link mesmo que o envio falhe)
        emailService.sendInvitationEmail(normalizedEmail, tenantName, role, inviteLink);

        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", invitationId);
        m.put("email", normalizedEmail);
        m.put("role", role);
        m.put("expires_at", expiresAt);
        m.put("invite_link", inviteLink);
        return m;
    }

    // ----------------------------------------------------------------
    // Cancelar convite
    // ----------------------------------------------------------------

    @Transactional
    public void cancelInvitation(UUID tenantId, UUID invitationId) {
        int updated = em.createNativeQuery(
                "UPDATE invitations SET status = 'cancelled' " +
                "WHERE id = :id AND tenant_id = :tenantId AND status = 'pending'"
        ).setParameter("id", invitationId).setParameter("tenantId", tenantId).executeUpdate();

        if (updated == 0) throw new NotFoundException("Convite não encontrado ou já processado");
    }

    // ----------------------------------------------------------------
    // Preview público do convite (sem autenticação)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> getInvitationPreview(String token) {
        var rows = (List<Object[]>) em.createNativeQuery(
                "SELECT i.email, i.role, i.status, i.expires_at::text, t.name as tenant_name " +
                "FROM invitations i JOIN tenants t ON t.id = i.tenant_id " +
                "WHERE i.token = :token"
        ).setParameter("token", token).getResultList();

        if (rows.isEmpty()) throw new NotFoundException("Convite não encontrado");

        Object[] row = rows.get(0);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("email", row[0]);
        m.put("role", row[1]);
        m.put("status", row[2]);
        m.put("expires_at", row[3]);
        m.put("tenant_name", row[4]);
        return m;
    }

    // ----------------------------------------------------------------
    // Aceitar convite (requer usuário autenticado)
    // Delega para função PostgreSQL SECURITY DEFINER
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> acceptInvitation(String token, UUID userId) {
        var result = em.createNativeQuery(
                "SELECT accept_invitation(:token, :userId)"
        ).setParameter("token", token).setParameter("userId", userId).getSingleResult();

        // O resultado é um JSONB como String
        String json = result.toString();

        // Parse simples do JSONB retornado
        if (json.contains("\"success\":false") || json.contains("\"success\": false")) {
            String error = extractJsonString(json, "error");
            throw new BadRequestException(error != null ? error : "Não foi possível aceitar o convite");
        }

        String tenantId = extractJsonString(json, "tenant_id");
        String role = extractJsonString(json, "role");

        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("tenant_id", tenantId);
        m.put("role", role);
        return m;
    }

    private String extractJsonString(String json, String key) {
        // Extrai valor string de um JSON simples (sem biblioteca)
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\": \"";
            idx = json.indexOf(search);
        }
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }
}
