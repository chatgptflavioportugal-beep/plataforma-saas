package com.saas.subscription.negocio;

import com.saas.subscription.dao.PlanVersionModuleLimitDAO;
import com.saas.subscription.dao.PlanVersionModuleDAO;
import com.saas.subscription.dao.PlatformModuleDAO;
import com.saas.subscription.dao.ProfileModuleSubscriptionDAO;
import com.saas.subscription.dto.response.ModuleAccessResolutionResponse;
import com.saas.subscription.entity.PlanVersionModule;
import com.saas.subscription.entity.PlatformModule;
import com.saas.subscription.negocio.impl.ModuleAccessNegocio;
import com.saas.subscription.security.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolução de acesso a módulo (assinatura/plano/limites) para consumo
 * interno do auth-service ao emitir o Module Access Token — ver
 * ModuleAccessResource. Avalia o acesso em ordem de especificidade:
 * (1) assinatura ativa/trial; (2) plano gratuito ainda não ativado;
 * (3) fallback legado via feature set da assinatura principal do tenant.
 */
@ApplicationScoped
public class ModuleAccessNegocioImpl implements ModuleAccessNegocio {

    private static final List<String> USABLE_STATUSES = List.of("ACTIVE", "TRIAL", "TRIAL_CANCELLED");

    @Inject
    PlatformModuleDAO platformModuleDAO;

    @Inject
    ProfileModuleSubscriptionDAO profileModuleSubscriptionDAO;

    @Inject
    PlanVersionModuleDAO planVersionModuleDAO;

    @Inject
    PlanVersionModuleLimitDAO planVersionModuleLimitDAO;

    @Override
    public ModuleAccessResolutionResponse resolve(String moduleSlug, TenantContext ctx) {
        PlatformModule module = platformModuleDAO.findActiveBySlug(moduleSlug).orElse(null);
        if (module == null) {
            return ModuleAccessResolutionResponse.notFound();
        }

        var subscriptionOpt = profileModuleSubscriptionDAO
            .findByTenantAndModule(ctx.getTenantId(), module.id)
            .filter(sub -> USABLE_STATUSES.contains(sub.status));

        if (subscriptionOpt.isPresent()) {
            var subscription = subscriptionOpt.get();
            boolean pastExpiry = subscription.expiresAt != null && subscription.expiresAt.isBefore(OffsetDateTime.now());
            if (pastExpiry) {
                return ModuleAccessResolutionResponse.expired(module.id, module.name);
            }

            PlanVersionModule planVersionModule = planVersionModuleDAO.findById(subscription.planVersionId);
            String planName = planVersionModule != null && planVersionModule.plan != null
                ? planVersionModule.plan.name : null;
            Map<String, Object> limits = loadPlanLimits(subscription.planVersionId, moduleSlug);
            return ModuleAccessResolutionResponse.granted(module.id, module.name, planName, "SUBSCRIPTION", limits);
        }

        var freeOpt = planVersionModuleDAO.findFreeForModule(module.id);
        if (freeOpt.isPresent()) {
            return ModuleAccessResolutionResponse.freePlanNotActivated(module.id, module.name, freeOpt.get().id);
        }

        if (ctx.hasFeature(moduleSlug)) {
            return ModuleAccessResolutionResponse.granted(module.id, module.name, ctx.getPlanCode(), "TENANT_SUBSCRIPTION", Map.of());
        }

        return ModuleAccessResolutionResponse.noAccess(module.id, module.name);
    }

    /**
     * pvml.code é persistido como "<moduleSlug>.<limitCode>" (estável entre
     * upgrades/downgrades de plano); o token remove o prefixo pois já carrega
     * o moduleSlug em sua própria claim.
     */
    private Map<String, Object> loadPlanLimits(UUID planVersionModuleId, String moduleSlug) {
        if (planVersionModuleId == null) return Map.of();

        String prefix = moduleSlug + ".";
        Map<String, Object> limits = new LinkedHashMap<>();
        for (var limit : planVersionModuleLimitDAO.listByPlanVersionModule(planVersionModuleId)) {
            String code = limit.code;
            String key = (code != null && code.startsWith(prefix)) ? code.substring(prefix.length()) : code;
            limits.put(key, limit.limitValue);
        }
        return limits;
    }
}
