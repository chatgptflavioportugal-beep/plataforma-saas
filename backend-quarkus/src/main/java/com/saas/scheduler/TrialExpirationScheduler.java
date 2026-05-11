package com.saas.scheduler;

import com.saas.entity.AuditLog;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class TrialExpirationScheduler {

    private static final Logger LOG = Logger.getLogger(TrialExpirationScheduler.class);

    @Inject
    EntityManager em;

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void checkTrialExpirations() {
        LOG.info("Verificando expiração de trials...");
        suspendExpiredTrials();
        sendExpirationAlerts();
    }

    private void suspendExpiredTrials() {
        int updated = em.createNativeQuery(
                "UPDATE tenants SET status = 'suspended', updated_at = NOW() " +
                "WHERE status = 'trial' AND trial_ends_at < NOW()"
        ).executeUpdate();

        em.createNativeQuery(
                "UPDATE tenant_subscriptions SET status = 'suspended', updated_at = NOW() " +
                "WHERE tenant_id IN (SELECT id FROM tenants WHERE status = 'suspended') " +
                "AND status = 'trial'"
        ).executeUpdate();

        if (updated > 0) {
            LOG.infof("Suspendidos %d tenants com trial expirado", updated);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendExpirationAlerts() {
        int[] alertDays = {7, 3, 1};

        for (int days : alertDays) {
            var tenants = em.createNativeQuery(
                    "SELECT t.id, t.name FROM tenants t " +
                    "WHERE t.status = 'trial' " +
                    "AND DATE(t.trial_ends_at) = DATE(NOW() + INTERVAL '" + days + " days') " +
                    "AND t.id NOT IN (" +
                    "  SELECT tenant_id FROM expiration_alerts " +
                    "  WHERE alert_type = :alertType AND DATE(sent_at) = CURRENT_DATE" +
                    ")",
                    Object[].class
            )
            .setParameter("alertType", days + "_days")
            .getResultList();

            for (Object row : tenants) {
                Object[] r = (Object[]) row;
                LOG.infof("Alerta %d dias para tenant %s (%s)", days, r[0], r[1]);

                em.createNativeQuery(
                        "INSERT INTO expiration_alerts (tenant_id, alert_type, days_remaining) " +
                        "VALUES (:tenantId, :alertType, :days)"
                )
                .setParameter("tenantId", r[0])
                .setParameter("alertType", days + "_days")
                .setParameter("days", days)
                .executeUpdate();
            }
        }
    }
}
