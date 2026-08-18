package com.saas.profile.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência de membros (user_tenants) e convites (invitations) de um tenant.
 */
@ApplicationScoped
public class InvitationRepository {

    @Inject
    EntityManager em;

    public record MemberRow(
            String userId, String fullName, String email, String role,
            String joinedAt, String accessLevelId, String accessLevelName
    ) {}

    public record InvitationRow(
            String id, String email, String role, String status,
            String expiresAt, String createdAt, String accessLevelId, String accessLevelName
    ) {}

    public record InvitationPreviewRow(
            String email, String role, String status, String expiresAt, String tenantName, String accessLevelName
    ) {}

    public record CreatedInvitation(String id, String token, String expiresAt) {}

    @SuppressWarnings("unchecked")
    public List<MemberRow> findActiveMembers(UUID tenantId) {
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT ut.user_id::text, up.full_name, au.email, ut.role, ut.created_at::text, " +
                "       ut.access_level_id::text, pal.name AS access_level_name " +
                "FROM user_tenants ut " +
                "LEFT JOIN user_profiles up ON up.id = ut.user_id " +
                "LEFT JOIN auth.users au ON au.id = ut.user_id " +
                "LEFT JOIN profile_access_levels pal ON pal.id = ut.access_level_id " +
                "WHERE ut.tenant_id = :tenantId AND ut.is_active = TRUE " +
                "ORDER BY ut.created_at ASC"
        ).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(r -> new MemberRow(
                (String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6]
        )).toList();
    }

    public Optional<String> findActiveMemberRole(UUID tenantId, UUID userId) {
        try {
            String role = (String) em.createNativeQuery(
                    "SELECT ut.role FROM user_tenants ut " +
                    "WHERE ut.tenant_id = :tenantId AND ut.user_id = :userId AND ut.is_active = TRUE"
            ).setParameter("tenantId", tenantId).setParameter("userId", userId).getSingleResult();
            return Optional.ofNullable(role);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void deactivateMember(UUID tenantId, UUID userId) {
        em.createNativeQuery(
                "UPDATE user_tenants SET is_active = FALSE, updated_at = NOW() " +
                "WHERE tenant_id = :tenantId AND user_id = :userId"
        ).setParameter("tenantId", tenantId).setParameter("userId", userId).executeUpdate();
    }

    public Optional<String> findActiveAccessLevelName(UUID tenantId, UUID accessLevelId) {
        try {
            String name = (String) em.createNativeQuery(
                    "SELECT name FROM profile_access_levels " +
                    "WHERE id = :alId AND tenant_id = :tenantId AND status = 'ACTIVE'"
            ).setParameter("alId", accessLevelId).setParameter("tenantId", tenantId).getSingleResult();
            return Optional.ofNullable(name);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void updateMemberAccessLevel(UUID tenantId, UUID userId, UUID accessLevelId) {
        em.createNativeQuery(
                "UPDATE user_tenants SET access_level_id = :alId, updated_at = NOW() " +
                "WHERE tenant_id = :tenantId AND user_id = :userId"
        )
        .setParameter("alId", accessLevelId)
        .setParameter("tenantId", tenantId)
        .setParameter("userId", userId)
        .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<InvitationRow> findAllByTenant(UUID tenantId) {
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT i.id::text, i.email, i.role, i.status, i.expires_at::text, i.created_at::text, " +
                "       i.access_level_id::text, pal.name AS access_level_name " +
                "FROM invitations i " +
                "LEFT JOIN profile_access_levels pal ON pal.id = i.access_level_id " +
                "WHERE i.tenant_id = :tenantId " +
                "ORDER BY i.created_at DESC"
        ).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(r -> new InvitationRow(
                (String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6], (String) r[7]
        )).toList();
    }

    public long countActiveMemberByEmail(UUID tenantId, String email) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_tenants ut " +
                "JOIN auth.users au ON au.id = ut.user_id " +
                "WHERE ut.tenant_id = :tenantId AND au.email = :email AND ut.is_active = TRUE"
        ).setParameter("tenantId", tenantId).setParameter("email", email).getSingleResult()).longValue();
    }

    public long countPendingInvitation(UUID tenantId, String email) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM invitations " +
                "WHERE tenant_id = :tenantId AND email = :email AND status = 'pending' AND expires_at > NOW()"
        ).setParameter("tenantId", tenantId).setParameter("email", email).getSingleResult()).longValue();
    }

    public String findTenantName(UUID tenantId) {
        return (String) em.createNativeQuery(
                "SELECT name FROM tenants WHERE id = :tenantId"
        ).setParameter("tenantId", tenantId).getSingleResult();
    }

    /** Cria o convite e devolve id/token/expiração num único round-trip (RETURNING). */
    public CreatedInvitation insertInvitation(UUID tenantId, UUID invitedBy, String email, UUID accessLevelId) {
        Object[] row = (Object[]) em.createNativeQuery(
                "INSERT INTO invitations (tenant_id, invited_by, email, role, access_level_id) " +
                "VALUES (:tenantId, :invitedBy, :email, 'member', :alId) " +
                "RETURNING id::text, token, expires_at::text"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("invitedBy", invitedBy)
        .setParameter("email", email)
        .setParameter("alId", accessLevelId)
        .getSingleResult();

        return new CreatedInvitation((String) row[0], (String) row[1], (String) row[2]);
    }

    /** @return {@code true} se um convite pendente foi cancelado. */
    public boolean cancelPendingInvitation(UUID tenantId, UUID invitationId) {
        int updated = em.createNativeQuery(
                "UPDATE invitations SET status = 'cancelled' " +
                "WHERE id = :id AND tenant_id = :tenantId AND status = 'pending'"
        ).setParameter("id", invitationId).setParameter("tenantId", tenantId).executeUpdate();
        return updated > 0;
    }

    public Optional<InvitationPreviewRow> findPreviewByToken(String token) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT i.email, i.role, i.status, i.expires_at::text, t.name AS tenant_name, " +
                "       pal.name AS access_level_name " +
                "FROM invitations i " +
                "JOIN tenants t ON t.id = i.tenant_id " +
                "LEFT JOIN profile_access_levels pal ON pal.id = i.access_level_id " +
                "WHERE i.token = :token"
        ).setParameter("token", token).getResultList();

        if (rows.isEmpty()) return Optional.empty();
        Object[] r = rows.get(0);
        return Optional.of(new InvitationPreviewRow((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5]));
    }

    /** E-mail do convite, apenas se ainda estiver pendente e não expirado. */
    public Optional<String> findAcceptableInvitationEmail(String token) {
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) em.createNativeQuery(
                "SELECT email FROM invitations WHERE token = :token AND status = 'pending' AND expires_at > NOW()"
        ).setParameter("token", token).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of((String) rows.get(0));
    }

    public boolean invitationTokenExists(String token) {
        long exists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM invitations WHERE token = :token"
        ).setParameter("token", token).getSingleResult()).longValue();
        return exists > 0;
    }

    /** Chama a função de banco {@code accept_invitation}, que vincula o usuário e encerra outros convites pendentes. */
    public Object callAcceptInvitationFunction(String token, UUID userId) {
        return em.createNativeQuery("SELECT accept_invitation(:token, :userId)")
                .setParameter("token", token)
                .setParameter("userId", userId)
                .getSingleResult();
    }
}
