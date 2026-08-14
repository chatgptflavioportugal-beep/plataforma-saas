package com.saas.subscription.repository;

import com.saas.subscription.entity.PlanVersionModuleLimit;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PlanVersionModuleLimitRepository implements PanacheRepositoryBase<PlanVersionModuleLimit, UUID> {

    public List<PlanVersionModuleLimit> listByPlanVersionModule(UUID planVersionModuleId) {
        return find("planVersionModuleId = ?1 order by sortOrder", planVersionModuleId).list();
    }
}
