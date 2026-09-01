package com.saas.subscription.dao;

import com.saas.subscription.entity.ModuleTrialHistory;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ModuleTrialHistoryDAO implements PanacheRepositoryBase<ModuleTrialHistory, UUID> {

    @Inject
    EntityManager em;

    public List<ModuleTrialHistory> listByTenantOrderByStartedDesc(UUID tenantId) {
        return find("tenantId = ?1 order by trialStartedAt desc", tenantId).list();
    }

    /**
     * Se o tenant ainda está dentro do período de cooldown de reutilização
     * deste módulo (com base no último Trial concluído), retorna quando o
     * cooldown termina. Aritmética de datas feita em SQL (native) para evitar
     * ambiguidade de tipo do driver JDBC com TIMESTAMPTZ + INTERVAL.
     */
    @SuppressWarnings("unchecked")
    public Optional<OffsetDateTime> findCooldownEndsAt(UUID tenantId, UUID moduleId, int cooldownDays) {
        List<Object> rows = em.createNativeQuery(
            "SELECT (trial_finished_at + (:cooldownDays || ' days')::interval) " +
            "FROM module_trial_history " +
            "WHERE tenant_id = :tenantId AND module_id = :moduleId " +
            "  AND trial_finished_at IS NOT NULL " +
            "  AND trial_finished_at + (:cooldownDays || ' days')::interval > NOW() " +
            "ORDER BY trial_started_at DESC LIMIT 1"
        )
            .setParameter("tenantId", tenantId)
            .setParameter("moduleId", moduleId)
            .setParameter("cooldownDays", cooldownDays)
            .getResultList();

        if (rows.isEmpty()) return Optional.empty();
        Object value = rows.get(0);
        if (value instanceof OffsetDateTime odt) return Optional.of(odt);
        if (value instanceof java.sql.Timestamp ts) return Optional.of(ts.toInstant().atOffset(ZoneOffset.UTC));
        throw new IllegalStateException("Tipo de data inesperado: " + value.getClass());
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
