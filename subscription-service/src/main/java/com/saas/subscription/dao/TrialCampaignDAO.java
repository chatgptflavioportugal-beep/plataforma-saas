package com.saas.subscription.dao;

import com.saas.subscription.entity.TrialCampaign;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TrialCampaignDAO implements PanacheRepositoryBase<TrialCampaign, UUID> {

    @Inject
    EntityManager em;

    /**
     * Campanha elegível para um plan_version_module: ACTIVE, com vagas, dentro
     * da janela de datas, maior prioridade (desempate pela mais antiga).
     */
    public Optional<TrialCampaign> findSelectableForPlanVersionModule(UUID planVersionModuleId) {
        return find("""
            planVersionModuleId = ?1 and status = 'ACTIVE' and usedSlots < maxSlots
              and (startDate is null or startDate <= current_date)
              and (endDate is null or endDate >= current_date)
            order by priority desc, createdAt asc
        """, planVersionModuleId).firstResultOptional();
    }

    public boolean hasCancelledCampaign(UUID planVersionModuleId) {
        return count("planVersionModuleId = ?1 and status = 'CANCELLED'", planVersionModuleId) > 0;
    }

    public List<TrialCampaign> listSelectableForModule(UUID moduleId) {
        return em.createQuery("""
            select tc from TrialCampaign tc
            join PlanVersionModule pvm on pvm.id = tc.planVersionModuleId
            join Plan p on p.id = pvm.planId
            where pvm.moduleId = :moduleId and pvm.status = 'active'
              and p.isActive = true and p.isCurrentVersion = true
              and tc.status = 'ACTIVE' and tc.usedSlots < tc.maxSlots
              and (tc.startDate is null or tc.startDate <= current_date)
              and (tc.endDate is null or tc.endDate >= current_date)
            order by tc.priority desc, tc.createdAt asc
        """, TrialCampaign.class)
            .setParameter("moduleId", moduleId)
            .getResultList();
    }

    public boolean everHadCampaignForModule(UUID moduleId) {
        long count = em.createQuery("""
            select count(tc) from TrialCampaign tc
            join PlanVersionModule pvm on pvm.id = tc.planVersionModuleId
            where pvm.moduleId = :moduleId
        """, Long.class)
            .setParameter("moduleId", moduleId)
            .getSingleResult();
        return count > 0;
    }

    /** Incremento atômico de vaga — retorna true só se havia vaga disponível no momento do UPDATE. */
    public boolean tryClaimSlot(UUID campaignId) {
        long updated = update("usedSlots = usedSlots + 1 where id = ?1 and usedSlots < maxSlots", campaignId);
        return updated == 1;
    }
}
