package com.saas.subscription.dao;

import com.saas.subscription.entity.ModuleTrialHistory;
import com.saas.subscription.to.CooldownTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ModuleTrialHistoryDAO implements PanacheRepositoryBase<ModuleTrialHistory, UUID> {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public List<ModuleTrialHistory> listByTenantOrderByStartedDesc(UUID tenantId) {
        return find("tenantId = ?1 order by trialStartedAt desc", tenantId).list();
    }

    /**
     * Se o tenant ainda está dentro do período de cooldown de reutilização
     * deste módulo (com base no último Trial concluído), retorna quando o
     * cooldown termina. Aritmética de datas feita em SQL (native) para evitar
     * ambiguidade de tipo do driver JDBC com TIMESTAMPTZ + INTERVAL.
     */
    public Optional<OffsetDateTime> findCooldownEndsAt(UUID tenantId, UUID moduleId, int cooldownDays) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT (trial_finished_at + (:cooldownDays || ' days')::interval) AS cooldown_ends_at
                        FROM module_trial_history
                        WHERE tenant_id = :tenantId AND module_id = :moduleId
                          AND trial_finished_at IS NOT NULL
                          AND trial_finished_at + (:cooldownDays || ' days')::interval > NOW()
                        ORDER BY trial_started_at DESC LIMIT 1
                        """, CooldownTO.class)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleId", moduleId)
                .setParameter("cooldownDays", cooldownDays)
                .getOptionalResult()
                .map(CooldownTO::cooldownEndsAt);
    }

    /** Fecha o registro de histórico (Trial terminando agora, cancelado ou não). */
    public void markFinished(UUID historyId, boolean becameCustomer) {
        update("""
            becameCustomer = becameCustomer or ?1, trialFinishedAt = coalesce(trialFinishedAt, current_timestamp)
            where id = ?2
        """, becameCustomer, historyId);
    }

    public void markCanceled(UUID historyId) {
        update("trialCanceledAt = current_timestamp where id = ?1", historyId);
    }

    public void markReactivated(UUID historyId) {
        update("trialCanceledAt = null where id = ?1", historyId);
    }
}
