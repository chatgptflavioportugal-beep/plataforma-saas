package com.saas.profile.negocio;

import com.saas.profile.dao.TenantDAO;
import com.saas.profile.dao.UserProfileDAO;
import com.saas.profile.dao.UserTenantDAO;
import com.saas.profile.dto.tenant.*;
import com.saas.profile.entity.Tenant;
import com.saas.profile.negocio.impl.TenantNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Perfil/criação de tenant (empresa ou individual).
 *
 * A listagem/detalhe administrativo de tenants (usada por AdminResource em
 * backend-quarkus) permanece lá até a Fase 6 (admin-service) — esta camada de
 * negócio cobre apenas o que o próprio cliente vê/aciona sobre seu perfil.
 */
@ApplicationScoped
public class TenantNegocioImpl implements TenantNegocio {

    @Inject
    TenantDAO tenantDAO;

    @Inject
    UserProfileDAO userProfileDAO;

    @Inject
    UserTenantDAO userTenantDAO;

    // ----------------------------------------------------------------
    // Criar tenant de empresa (chamado pelo onboarding)
    // ----------------------------------------------------------------

    @Transactional
    public Tenant createTenant(String name, String slug, UUID ownerId, String type) {
        if (Tenant.findBySlug(slug) != null) {
            throw new IllegalArgumentException("Slug já em uso: " + slug);
        }

        // Para empresa: procura plano business free. Para individual: plano individual.
        // Sem fallback nenhum (nenhum plano ativo cadastrado) é um estado inesperado do
        // catálogo — propaga a falha em vez de criar um tenant sem plano.
        UUID freePlanId = tenantDAO.findPlanIdByType(type)
                .or(tenantDAO::findAnyActivePlanId)
                .orElseThrow(NoResultException::new);

        Tenant tenant = new Tenant();
        tenant.name = name;
        tenant.slug = slug;
        tenant.status = "active";
        tenant.type = type;
        tenant.planId = freePlanId;
        tenant.persist();

        tenantDAO.insertOwnerLink(ownerId, tenant.id);
        tenantDAO.insertSubscription(tenant.id, freePlanId);

        return tenant;
    }

    // ----------------------------------------------------------------
    // Perfil do tenant: subscription + plano + papel do usuário
    // Se o tenant individual não tiver subscription ainda, cria automaticamente.
    // ----------------------------------------------------------------

    @Transactional
    public TenantProfileResponse getTenantProfile(UUID tenantId) {
        var row = tenantDAO.findTenantProfile(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant não encontrado"));

        // Se não há subscription (tenant individual criado pelo trigger antes de ter planos),
        // cria automaticamente com o plano disponível de menor ordem.
        if (row.subId() == null) {
            ensureSubscription(tenantId, row.tenantType());
            return getTenantProfile(tenantId);
        }

        TenantDto tenantDto = new TenantDto(row.tenantId(), row.tenantName(), row.tenantSlug(), row.tenantStatus(),
                row.tenantType(), row.trialEndsAt());

        SubscriptionDto subDto = new SubscriptionDto(row.subId(), row.subStatus(), row.trialEnd(),
                row.currentPeriodStart(), row.currentPeriodEnd(),
                row.billingType() != null ? row.billingType() : "monthly", row.planVersion());

        PlanDto planDto = new PlanDto(row.planId(), row.planName(), row.planCode(), row.planType(),
                row.priceMonthly(), row.priceAnnual(), row.maxUsers(), row.maxAiRequestsMonth(),
                row.features(), row.totalMonthlyPrice(), row.totalAnnualMonthlyPrice(), row.totalAnnualPrice());

        return new TenantProfileResponse(tenantDto, subDto, planDto, row.role() != null ? row.role() : "owner");
    }

    private void ensureSubscription(UUID tenantId, String tenantType) {
        var planId = tenantDAO.findPlanIdByType(tenantType).or(tenantDAO::findAnyActivePlanId);
        if (planId.isEmpty()) return; // Sem planos cadastrados ainda — não faz nada
        tenantDAO.insertSubscriptionIfMissing(tenantId, planId.get());
    }

    // ----------------------------------------------------------------
    // Garante que o usuário tenha um tenant individual (backfill para usuários antigos)
    // Idempotente: se já existe, retorna o existente sem criar novo.
    // ----------------------------------------------------------------

    @Transactional
    public IndividualTenantResponse ensureIndividualTenant(UUID userId) {
        var existing = tenantDAO.findIndividualTenantByUser(userId);
        if (existing.isPresent()) {
            var row = existing.get();
            return new IndividualTenantResponse(row.id(), row.name(), row.slug(), "individual", true);
        }

        String fullName = userProfileDAO.findFullName(userId).orElse(null);
        String name = (fullName != null && !fullName.isBlank()) ? fullName.trim() : "Meu Plano";
        String slug = "individual-" + userId.toString().replace("-", "");

        Tenant tenant = createTenant(name, slug, userId, "individual");
        return new IndividualTenantResponse(tenant.id.toString(), tenant.name, tenant.slug, "individual", false);
    }

    // ----------------------------------------------------------------
    // Tenants do usuário autenticado (tela de seleção de perfil)
    // ----------------------------------------------------------------

    public List<MyTenantDto> listMyTenants(UUID userId) {
        return userTenantDAO.findAllByUser(userId).stream().map(r -> new MyTenantDto(
                r.id(), r.userId(), r.tenantId(), r.role(), r.isActive(),
                new TenantSummaryDto(r.tenantId(), r.tenantName(), r.tenantSlug(), r.tenantStatus(), r.tenantType(),
                        r.planId(), r.trialEndsAt(), r.createdAt(), r.updatedAt())
        )).toList();
    }
}
