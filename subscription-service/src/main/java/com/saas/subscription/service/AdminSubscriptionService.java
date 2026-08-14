package com.saas.subscription.service;

import com.saas.subscription.entity.ProfileModuleSubscription;
import com.saas.subscription.repository.ProfileModuleSubscriptionRepository;
import com.saas.subscription.repository.UserTenantRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ações administrativas sobre o ciclo de vida de assinaturas — cancelar/
 * reativar em nome de um administrador da plataforma agindo sobre a
 * assinatura de qualquer tenant. Distinto de ProfileModuleSubscriptionService
 * (tenant-scoped, acionado pelo próprio cliente sobre a própria assinatura).
 */
@ApplicationScoped
public class AdminSubscriptionService {

    @Inject
    ProfileModuleSubscriptionRepository subscriptionRepository;

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    AuditService auditService;

    public record ActionResult(UUID tenantId, String status) {}

    @Transactional
    public ActionResult cancel(String subscriptionId, UUID adminUserId) {
        ProfileModuleSubscription subscription = findById(subscriptionId)
            .filter(s -> List.of("ACTIVE", "TRIAL").contains(s.status))
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada ou já cancelada"));

        // TRIAL cancela para TRIAL_CANCELLED (acesso segue até expirar, só a renovação
        // é interrompida) — mesma semântica de ProfileModuleSubscriptionService.cancel.
        String newStatus = "TRIAL".equals(subscription.status) ? "TRIAL_CANCELLED" : "CANCELED";
        subscription.status = newStatus;
        subscription.canceledAt = OffsetDateTime.now();

        userTenantRepository.bumpVersionForTenant(subscription.tenantId);

        auditService.log(subscription.tenantId, adminUserId, "subscription.admin_cancel",
            "profile_module_subscriptions", subscriptionId, (String) null);

        return new ActionResult(subscription.tenantId, newStatus);
    }

    @Transactional
    public ActionResult reactivate(String subscriptionId, UUID adminUserId) {
        ProfileModuleSubscription subscription = findById(subscriptionId)
            .filter(s -> List.of("CANCELED", "TRIAL_CANCELLED").contains(s.status))
            .filter(s -> s.expiresAt == null || s.expiresAt.isAfter(OffsetDateTime.now()))
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada, não está cancelada ou já expirou"));

        String newStatus = "TRIAL_CANCELLED".equals(subscription.status) ? "TRIAL" : "ACTIVE";
        subscription.status = newStatus;
        subscription.canceledAt = null;

        userTenantRepository.bumpVersionForTenant(subscription.tenantId);

        auditService.log(subscription.tenantId, adminUserId, "subscription.admin_reactivate",
            "profile_module_subscriptions", subscriptionId, (String) null);

        return new ActionResult(subscription.tenantId, newStatus);
    }

    /** IDs malformados são tratados como "não encontrado", não como erro — mesmo comportamento anterior. */
    private java.util.Optional<ProfileModuleSubscription> findById(String subscriptionId) {
        UUID id;
        try {
            id = UUID.fromString(subscriptionId);
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(subscriptionRepository.findById(id));
    }
}
