package com.saas.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

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
    public void expireOverdueModuleSubscriptions() {
        int updated = em.createNativeQuery(
                "UPDATE profile_module_subscriptions SET status = 'EXPIRED', updated_at = NOW() " +
                "WHERE status = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at < NOW()"
        ).executeUpdate();

        if (updated > 0) {
            LOG.infof("Expiradas %d assinaturas de módulo vencidas", updated);
        }
    }
}
