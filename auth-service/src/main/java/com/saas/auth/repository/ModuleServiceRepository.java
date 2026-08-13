package com.saas.auth.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

/**
 * Serviços (platform_module_services) disponíveis dentro de um módulo,
 * usados para montar as permissões carregadas no ModuleAccessToken.
 */
@ApplicationScoped
public class ModuleServiceRepository {

    @Inject
    EntityManager em;

    /** Todos os serviços ativos do módulo — usado para owner/admin, que tem acesso irrestrito. */
    @SuppressWarnings("unchecked")
    public List<String> findActiveServiceSlugsByModule(UUID moduleId) {
        return em.createNativeQuery("""
            SELECT s.slug
            FROM platform_module_services s
            WHERE s.module_id = :moduleId AND s.is_active = TRUE
        """).setParameter("moduleId", moduleId).getResultList();
    }

    /** Serviços do módulo liberados pelo nível de acesso do vínculo usuário-tenant — usado para membros. */
    @SuppressWarnings("unchecked")
    public List<String> findServiceSlugsByAccessLevel(UUID userId, UUID tenantId, UUID moduleId) {
        return em.createNativeQuery("""
            SELECT s.slug
            FROM profile_access_level_permissions palp
            JOIN user_tenants ut ON ut.access_level_id = palp.access_level_id
            JOIN platform_module_services s ON s.id = palp.service_id
            WHERE ut.user_id = :userId
              AND ut.tenant_id = :tenantId
              AND ut.is_active = TRUE
              AND palp.module_id = :moduleId
              AND s.is_active = TRUE
        """)
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .setParameter("moduleId", moduleId)
        .getResultList();
    }
}
