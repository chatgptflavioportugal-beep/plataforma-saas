package com.saas.profile.service;

import com.saas.profile.repository.UserTenantRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

    @Inject
    UserTenantRepository userTenantRepository;

    @ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:5100")
    String baseUrl;

    // ----------------------------------------------------------------
    // Listar membros ativos do tenant (com nome e e-mail)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMembers(UUID tenantId) {
        var rows = (List<Object[]>) em.createNativeQuery(
                "SELECT ut.user_id::text, up.full_name, au.email, ut.role, ut.created_at::text, " +
                "       ut.access_level_id::text, pal.name AS access_level_name " +
                "FROM user_tenants ut " +
                "LEFT JOIN user_profiles up ON up.id = ut.user_id " +
                "LEFT JOIN auth.users au ON au.id = ut.user_id " +
                "LEFT JOIN profile_access_levels pal ON pal.id = ut.access_level_id " +
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
            m.put("access_level_id", row[5]);
            m.put("access_level_name", row[6]);
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

        // Membro removido — invalida qualquer PAT/MAT em cache dele (ModuleTokenFilter
        // já rejeita por is_active=FALSE, o bump cobre também o caso de readmissão futura).
        userTenantRepository.bumpVersionForMember(targetUserId, tenantId);
    }

    // ----------------------------------------------------------------
    // Alterar o nível de acesso de um membro ativo
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Transactional
    public void changeMemberAccessLevel(UUID tenantId, UUID targetUserId, String accessLevelId) {
        UUID alId;
        try {
            alId = UUID.fromString(accessLevelId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de nível de acesso inválido");
        }

        var levelRows = (List<Object>) em.createNativeQuery(
                "SELECT name FROM profile_access_levels " +
                "WHERE id = :alId AND tenant_id = :tenantId AND status = 'ACTIVE'"
        ).setParameter("alId", alId).setParameter("tenantId", tenantId).getResultList();

        if (levelRows.isEmpty()) {
            throw new BadRequestException("Nível de acesso inválido ou inativo");
        }

        var targetRows = (List<Object>) em.createNativeQuery(
                "SELECT role FROM user_tenants " +
                "WHERE tenant_id = :tenantId AND user_id = :userId AND is_active = TRUE"
        ).setParameter("tenantId", tenantId).setParameter("userId", targetUserId).getResultList();

        if (targetRows.isEmpty()) throw new NotFoundException("Membro não encontrado");
        String targetRole = (String) targetRows.get(0);
        if (!"member".equals(targetRole)) {
            throw new BadRequestException("Apenas membros possuem nível de acesso atribuído");
        }

        em.createNativeQuery(
                "UPDATE user_tenants SET access_level_id = :alId, updated_at = NOW() " +
                "WHERE tenant_id = :tenantId AND user_id = :userId"
        )
        .setParameter("alId", alId)
        .setParameter("tenantId", tenantId)
        .setParameter("userId", targetUserId)
        .executeUpdate();

        userTenantRepository.bumpVersionForMember(targetUserId, tenantId);
    }

    // ----------------------------------------------------------------
    // Listar convites do tenant (pendentes + histórico)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listInvitations(UUID tenantId) {
        var rows = (List<Object[]>) em.createNativeQuery(
                "SELECT i.id::text, i.email, i.role, i.status, i.expires_at::text, i.created_at::text, " +
                "       i.access_level_id::text, pal.name AS access_level_name " +
                "FROM invitations i " +
                "LEFT JOIN profile_access_levels pal ON pal.id = i.access_level_id " +
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
            m.put("access_level_id", row[6]);
            m.put("access_level_name", row[7]);
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Enviar convite com Nível de Acesso
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> sendInvitation(UUID tenantId, String email, String accessLevelId, UUID invitedBy) {
        String normalizedEmail = email.trim().toLowerCase();

        UUID alId;
        try {
            alId = UUID.fromString(accessLevelId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de nível de acesso inválido");
        }

        @SuppressWarnings("unchecked")
        var alRows = (List<Object>) em.createNativeQuery(
                "SELECT name FROM profile_access_levels " +
                "WHERE id = :alId AND tenant_id = :tenantId AND status = 'ACTIVE'"
        ).setParameter("alId", alId).setParameter("tenantId", tenantId).getResultList();

        if (alRows.isEmpty()) {
            throw new BadRequestException("Nível de acesso inválido ou inativo");
        }
        String accessLevelName = (String) alRows.get(0);

        long isMember = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_tenants ut " +
                "JOIN auth.users au ON au.id = ut.user_id " +
                "WHERE ut.tenant_id = :tenantId AND au.email = :email AND ut.is_active = TRUE"
        ).setParameter("tenantId", tenantId).setParameter("email", normalizedEmail).getSingleResult()).longValue();

        if (isMember > 0) {
            throw new BadRequestException("Este usuário já é membro da empresa");
        }

        long pendingExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM invitations " +
                "WHERE tenant_id = :tenantId AND email = :email AND status = 'pending' AND expires_at > NOW()"
        ).setParameter("tenantId", tenantId).setParameter("email", normalizedEmail).getSingleResult()).longValue();

        if (pendingExists > 0) {
            throw new BadRequestException("Já existe um convite pendente para este e-mail");
        }

        String tenantName = (String) em.createNativeQuery(
                "SELECT name FROM tenants WHERE id = :tenantId"
        ).setParameter("tenantId", tenantId).getSingleResult();

        em.createNativeQuery(
                "INSERT INTO invitations (tenant_id, invited_by, email, role, access_level_id) " +
                "VALUES (:tenantId, :invitedBy, :email, 'member', :alId)"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("invitedBy", invitedBy)
        .setParameter("email", normalizedEmail)
        .setParameter("alId", alId)
        .executeUpdate();

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

        emailService.sendInvitationEmail(normalizedEmail, tenantName, accessLevelName,
                baseUrl + "/invite/accept?token=" + token);

        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", invitationId);
        m.put("email", normalizedEmail);
        m.put("access_level_id", alId.toString());
        m.put("access_level_name", accessLevelName);
        m.put("expires_at", expiresAt);
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
                "SELECT i.email, i.role, i.status, i.expires_at::text, t.name AS tenant_name, " +
                "       pal.name AS access_level_name " +
                "FROM invitations i " +
                "JOIN tenants t ON t.id = i.tenant_id " +
                "LEFT JOIN profile_access_levels pal ON pal.id = i.access_level_id " +
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
        m.put("access_level_name", row[5]);
        return m;
    }

    // ----------------------------------------------------------------
    // Aceitar convite (requer usuário autenticado)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> acceptInvitation(String token, UUID userId, String userEmail) {
        var invRows = (List<Object>) em.createNativeQuery(
                "SELECT email FROM invitations WHERE token = :token AND status = 'pending' AND expires_at > NOW()"
        ).setParameter("token", token).getResultList();

        if (invRows.isEmpty()) {
            long exists = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM invitations WHERE token = :token"
            ).setParameter("token", token).getSingleResult()).longValue();
            if (exists == 0) throw new NotFoundException("Convite não encontrado");
            throw new BadRequestException("Convite inválido, expirado ou já utilizado");
        }

        String inviteEmail = (String) invRows.get(0);
        String normalizedUserEmail = userEmail != null ? userEmail.trim().toLowerCase() : "";

        if (!inviteEmail.equalsIgnoreCase(normalizedUserEmail)) {
            throw new WebApplicationException(Response
                    .status(Response.Status.FORBIDDEN)
                    .entity(Map.of(
                            "error", "wrong_email",
                            "invitation_email", inviteEmail,
                            "user_email", normalizedUserEmail
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }

        var result = em.createNativeQuery(
                "SELECT accept_invitation(:token, :userId)"
        ).setParameter("token", token).setParameter("userId", userId).getSingleResult();

        String json = result.toString();

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
