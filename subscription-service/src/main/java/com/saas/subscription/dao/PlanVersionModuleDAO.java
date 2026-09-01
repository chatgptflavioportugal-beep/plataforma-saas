package com.saas.subscription.dao;

import com.saas.subscription.entity.PlanVersionModule;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlanVersionModuleDAO implements PanacheRepositoryBase<PlanVersionModule, UUID> {

    public boolean existsActiveForModule(UUID planVersionModuleId, UUID moduleId) {
        return count("id = ?1 and moduleId = ?2 and status = 'active'", planVersionModuleId, moduleId) > 0;
    }

    /** Plano com preço zero (Free) ativo do módulo, se existir. */
    public Optional<PlanVersionModule> findFreeForModule(UUID moduleId) {
        return find("moduleId = ?1 and status = 'active' and monthlyPrice = 0 order by id", moduleId)
            .firstResultOptional();
    }

    /**
     * Todas as combinações módulo/plano vigentes e ativas do catálogo (plano
     * ativo e na versão corrente) — usado para varrer a elegibilidade de Trial
     * de todo o catálogo (ProfileModuleSubscriptionResource.listTrialEligibility).
     */
    public List<PlanVersionModule> listActiveOfCurrentPlans() {
        return find("""
            status = 'active' and plan.isActive = true and plan.isCurrentVersion = true
        """).list();
    }
}
