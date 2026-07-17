package com.saas.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Mantém profile_module_subscriptions.status coerente com expires_at.
 *
 * As checagens de acesso em tempo real (DashboardResource, ModuleTokenResource,
 * ServiceRouteResource, TenantSubscriptionRepository) já tratam uma assinatura
 * ACTIVE vencida como expirada independente deste job — este scheduler só
 * mantém a coluna status correta para telas que exibem o valor persistido
 * (ex.: badge de status em SubscriptionsPage).
 */
@ApplicationScoped
public class ModuleSubscriptionExpirationScheduler {

    private static final Logger LOG = Logger.getLogger(ModuleSubscriptionExpirationScheduler.class);

    @Inject
    EntityManager em;

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    @SuppressWarnings("unchecked")
    public void expireOverdueModuleSubscriptions() {
        List<UUID> affectedTenantIds = em.createNativeQuery(
                "SELECT DISTINCT tenant_id FROM profile_module_subscriptions " +
                "WHERE status = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at < NOW()"
        ).getResultList();

        if (affectedTenantIds.isEmpty()) return;

        int updated = em.createNativeQuery(
                "UPDATE profile_module_subscriptions SET status = 'EXPIRED', updated_at = NOW() " +
                "WHERE status = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at < NOW()"
        ).executeUpdate();

        // Assinatura de módulo expirou — invalida PAT/MAT em cache dos membros do tenant
        // para que o front reavalie o acesso na próxima requisição, sem esperar o MAT expirar.
        em.createNativeQuery(
                "UPDATE user_tenants SET permissions_version = permissions_version + 1 " +
                "WHERE tenant_id IN (:ids) AND is_active = TRUE"
        ).setParameter("ids", affectedTenantIds).executeUpdate();

        LOG.infof("Expiradas %d assinaturas de módulo vencidas", updated);
    }
}
