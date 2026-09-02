package com.saas.subscription.dao;

import com.saas.subscription.to.ActiveServiceTO;
import com.saas.subscription.to.ModuleAccessStatusTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

/**
 * Consulta agregada do Dashboard: status de acesso do perfil a todos os
 * módulos ativos, mais os serviços de cada módulo elegível. Mantida como
 * Native Query (CASE/EXTRACT EPOCH/subselects correlacionados sem
 * equivalente direto em JPQL, e uma única ida ao banco em vez de N+1
 * entidades). Mapeada via {@code DatabaseQuery}/TO — Object[] nunca aparece nesta classe.
 */
@ApplicationScoped
public class DashboardDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public List<ModuleAccessStatusTO> listModulesWithAccessStatus(UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT
                          pm.id, pm.name, pm.slug, pm.description, pm.icon_path,
                          pms.id               AS sub_id,
                          pms.status           AS sub_status,
                          pms.expires_at       AS sub_expires_at,
                          (pms.status IN ('ACTIVE', 'TRIAL', 'TRIAL_CANCELLED') AND pms.expires_at IS NOT NULL AND pms.expires_at < NOW())
                                                AS sub_past_expiry,
                          p.name               AS plan_name,
                          p.code               AS plan_slug,
                          pvm_sub.id           AS plan_version_id,
                          (SELECT COUNT(*) FROM platform_module_services s
                           WHERE s.module_id = pm.id AND s.is_active = TRUE) AS service_count,
                          CASE WHEN EXISTS (
                            SELECT 1 FROM plan_version_modules pvm2
                            WHERE pvm2.module_id = pm.id
                              AND pvm2.status = 'active'
                              AND pvm2.monthly_price = 0
                          ) THEN 1 ELSE 0 END  AS has_free_plan,
                          CASE WHEN pms.status IN ('TRIAL', 'TRIAL_CANCELLED') AND pms.expires_at IS NOT NULL
                            THEN GREATEST(0, CEIL(EXTRACT(EPOCH FROM (pms.expires_at - NOW())) / 86400.0))::int
                            ELSE NULL END      AS trial_days_remaining
                        FROM platform_modules pm
                        LEFT JOIN profile_module_subscriptions pms
                          ON pms.module_id = pm.id
                         AND pms.tenant_id = :tenantId
                         AND pms.status IN ('ACTIVE', 'TRIAL', 'TRIAL_CANCELLED', 'EXPIRED', 'PENDING_PAYMENT')
                        LEFT JOIN plan_version_modules pvm_sub ON pvm_sub.id = pms.plan_version_id
                        LEFT JOIN plans p ON p.id = pvm_sub.plan_id
                        WHERE pm.is_active = TRUE
                        ORDER BY pm.sort_order
                        """, ModuleAccessStatusTO.class)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }

    public List<ActiveServiceTO> listActiveServicesOrdered(UUID moduleId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT
                          s.id AS service_id, s.name AS service_name, s.slug AS service_slug,
                          s.description AS service_description, s.icon_path AS service_icon_path,
                          CASE WHEN g.status = 'ACTIVE' THEN g.id ELSE NULL END AS service_group_id,
                          CASE WHEN g.status = 'ACTIVE' THEN g.name ELSE NULL END AS service_group_name,
                          s.route_key AS route_key,
                          CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END AS group_sort_key
                        FROM platform_module_services s
                        LEFT JOIN platform_module_service_groups g
                          ON g.id = s.service_group_id AND g.status = 'ACTIVE'
                        WHERE s.module_id = :moduleId AND s.is_active = TRUE
                        ORDER BY
                          CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END,
                          s.sort_order
                        """, ActiveServiceTO.class)
                .setParameter("moduleId", moduleId)
                .getResultList();
    }
}
