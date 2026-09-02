package com.saas.subscription.negocio;

import com.saas.subscription.dao.DashboardDAO;
import com.saas.subscription.dao.ProfileAccessLevelPermissionDAO;
import com.saas.subscription.dto.response.DashboardModuleResponse;
import com.saas.subscription.dto.response.DashboardServiceResponse;
import com.saas.subscription.negocio.impl.DashboardNegocio;
import com.saas.subscription.negocio.impl.TrialCampaignNegocio;
import com.saas.subscription.to.ModuleAccessStatusTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * accessStatus:
 *   SUBSCRIBED — perfil possui assinatura ativa (dentro da validade) deste módulo
 *   TRIAL      — perfil está no período de Trial deste módulo (dentro da validade)
 *   EXPIRED    — perfil já assinou (ou testou) este módulo, mas a assinatura/trial venceu
 *   FREE       — módulo possui plano com preço zero disponível (sem assinatura ativa)
 *   LOCKED     — módulo não disponível e sem plano gratuito
 *
 * Serviços são retornados apenas para SUBSCRIBED, TRIAL e FREE.
 * Para membros com nível de acesso, serviços SUBSCRIBED são filtrados pelas permissões do nível.
 * Ordem de retorno: SUBSCRIBED → TRIAL → EXPIRED → FREE → LOCKED.
 */
@ApplicationScoped
public class DashboardNegocioImpl implements DashboardNegocio {

    @Inject
    DashboardDAO dashboardDAO;

    @Inject
    ProfileAccessLevelPermissionDAO profileAccessLevelPermissionDAO;

    @Inject
    TrialCampaignNegocio trialCampaignNegocio;

    @Override
    public List<DashboardModuleResponse> listModulesWithAccessStatus(UUID tenantId, UUID userId, String role) {
        List<ModuleAccessStatusTO> moduleRows = dashboardDAO.listModulesWithAccessStatus(tenantId);

        Set<UUID> memberServiceIds = "member".equals(role)
            ? profileAccessLevelPermissionDAO.findServiceIdsForMember(userId, tenantId)
            : null;

        List<DashboardModuleResponse> subscribed = new ArrayList<>();
        List<DashboardModuleResponse> trial      = new ArrayList<>();
        List<DashboardModuleResponse> expired    = new ArrayList<>();
        List<DashboardModuleResponse> free       = new ArrayList<>();
        List<DashboardModuleResponse> locked     = new ArrayList<>();

        for (var row : moduleRows) {
            boolean isTrialCancelled = "TRIAL_CANCELLED".equals(row.subscriptionStatus());
            boolean isExpired = row.subscriptionId() != null && ("EXPIRED".equals(row.subscriptionStatus())
                || "PENDING_PAYMENT".equals(row.subscriptionStatus())
                || (("ACTIVE".equals(row.subscriptionStatus()) || "TRIAL".equals(row.subscriptionStatus()) || isTrialCancelled) && row.pastExpiry()));
            boolean isTrial = row.subscriptionId() != null
                && ("TRIAL".equals(row.subscriptionStatus()) || isTrialCancelled) && !row.pastExpiry();
            boolean isSubscribed = row.subscriptionId() != null && "ACTIVE".equals(row.subscriptionStatus()) && !row.pastExpiry();

            String accessStatus;
            String badgeLabel;
            if (isSubscribed) {
                accessStatus = "SUBSCRIBED";
                badgeLabel = row.planName() != null ? row.planName() : "Ativo";
            } else if (isTrial) {
                accessStatus = "TRIAL";
                badgeLabel = isTrialCancelled ? "Trial cancelado" : trialBadgeLabel(row.trialDaysRemaining());
            } else if (isExpired) {
                accessStatus = "EXPIRED";
                badgeLabel = "Expirado";
            } else if (row.hasFreePlan()) {
                accessStatus = "FREE";
                badgeLabel = "Free";
            } else {
                accessStatus = "LOCKED";
                badgeLabel = "Contratar";

                var trialStatus = trialCampaignNegocio.resolveModuleTrialStatus(tenantId, row.moduleId());
                if (trialStatus.eligible()) {
                    badgeLabel = "Trial disponível";
                } else if (trialStatus.hadCampaignEver()) {
                    badgeLabel = "Trial encerrado";
                }
            }

            List<DashboardServiceResponse> services = List.of();
            if ("SUBSCRIBED".equals(accessStatus) || "TRIAL".equals(accessStatus) || "FREE".equals(accessStatus)) {
                services = dashboardDAO.listActiveServicesOrdered(row.moduleId()).stream()
                    .filter(svc -> memberServiceIds == null || memberServiceIds.contains(svc.serviceId()))
                    .map(svc -> new DashboardServiceResponse(
                        svc.serviceId(), svc.serviceName(), svc.serviceSlug(), svc.serviceDescription(),
                        svc.serviceIconPath(), svc.serviceGroupId(), svc.serviceGroupName(), svc.routeKey(), true
                    ))
                    .toList();
            }

            var item = new DashboardModuleResponse(
                row.moduleId(), row.moduleName(), row.moduleSlug(), row.moduleDescription(), row.moduleIconPath(),
                accessStatus, row.planName(), row.planSlug(), row.planVersionId(), badgeLabel, row.serviceCount(),
                row.subscriptionExpiresAt() != null ? row.subscriptionExpiresAt().toString() : null,
                isTrial ? row.trialDaysRemaining() : null, isTrial && isTrialCancelled, services
            );

            switch (accessStatus) {
                case "SUBSCRIBED" -> subscribed.add(item);
                case "TRIAL"      -> trial.add(item);
                case "EXPIRED"    -> expired.add(item);
                case "FREE"       -> free.add(item);
                default           -> locked.add(item);
            }
        }

        List<DashboardModuleResponse> result = new ArrayList<>();
        result.addAll(subscribed);
        result.addAll(trial);
        result.addAll(expired);
        result.addAll(free);
        result.addAll(locked);
        return result;
    }

    private static String trialBadgeLabel(Integer daysRemaining) {
        if (daysRemaining == null) return "Trial";
        if (daysRemaining <= 0) return "Último dia do Trial";
        if (daysRemaining == 1) return "Trial termina amanhã";
        return "Trial — restam " + daysRemaining + " dias";
    }
}
