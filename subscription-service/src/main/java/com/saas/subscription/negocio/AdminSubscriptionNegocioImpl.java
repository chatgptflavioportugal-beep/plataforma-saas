package com.saas.subscription.negocio;

import com.saas.subscription.dao.ProfileModuleSubscriptionDAO;
import com.saas.subscription.dao.UserTenantDAO;
import com.saas.subscription.entity.ProfileModuleSubscription;
import com.saas.subscription.negocio.impl.AdminSubscriptionNegocio;
import com.saas.subscription.negocio.impl.AuditNegocio;
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
 * assinatura de qualquer tenant. Distinto de ProfileModuleSubscriptionNegocioImpl
 * (tenant-scoped, acionado pelo próprio cliente sobre a própria assinatura).
 */
@ApplicationScoped
public class AdminSubscriptionNegocioImpl implements AdminSubscriptionNegocio {

    @Inject
    ProfileModuleSubscriptionDAO subscriptionDAO;

    @Inject
    UserTenantDAO userTenantDAO;

    @Inject
    AuditNegocio auditNegocio;

    @Override
    @Transactional
    public ActionResult cancel(String subscriptionId, UUID adminUserId) {
        ProfileModuleSubscription subscription = findById(subscriptionId)
            .filter(s -> List.of("ACTIVE", "TRIAL").contains(s.status))
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada ou já cancelada"));

        // TRIAL cancela para TRIAL_CANCELLED (acesso segue até expirar, só a renovação
        // é interrompida) — mesma semântica de ProfileModuleSubscriptionNegocioImpl.cancel.
        String newStatus = "TRIAL".equals(subscription.status) ? "TRIAL_CANCELLED" : "CANCELED";
        subscription.status = newStatus;
        subscription.canceledAt = OffsetDateTime.now();

        userTenantDAO.bumpVersionForTenant(subscription.tenantId);

        auditNegocio.log(subscription.tenantId, adminUserId, "subscription.admin_cancel",
            "profile_module_subscriptions", subscriptionId, (String) null);

        return new ActionResult(subscription.tenantId, newStatus);
    }

    @Override
    @Transactional
    public ActionResult reactivate(String subscriptionId, UUID adminUserId) {
        ProfileModuleSubscription subscription = findById(subscriptionId)
            .filter(s -> List.of("CANCELED", "TRIAL_CANCELLED").contains(s.status))
            .filter(s -> s.expiresAt == null || s.expiresAt.isAfter(OffsetDateTime.now()))
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada, não está cancelada ou já expirou"));

        String newStatus = "TRIAL_CANCELLED".equals(subscription.status) ? "TRIAL" : "ACTIVE";
        subscription.status = newStatus;
        subscription.canceledAt = null;

        userTenantDAO.bumpVersionForTenant(subscription.tenantId);

        auditNegocio.log(subscription.tenantId, adminUserId, "subscription.admin_reactivate",
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
        return java.util.Optional.ofNullable(subscriptionDAO.findById(id));
    }
}
