package com.saas.subscription.controller;

import com.saas.subscription.security.TenantContext;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolução de acesso a módulo (assinatura/plano/limites) para consumo
 * interno do auth-service ao emitir o Module Access Token. Consolida a
 * lógica que antes vivia em auth-service.ModuleTokenResource — planos e
 * assinaturas são domínio deste serviço; auth-service deve apenas assinar
 * o token com os claims já resolvidos aqui.
 *
 * Chamado serviço-a-serviço repassando o mesmo header Authorization (JWT
 * Supabase) e X-Tenant-ID recebidos pelo auth-service do frontend — resolve
 * o tenant via o mesmo TenantResolutionFilter usado pelos demais endpoints
 * deste serviço, sem exigir um contrato de autenticação separado.
 *
 * Sempre responde 200 com um campo "resolution" indicando o resultado
 * (GRANTED / MODULE_NOT_FOUND / MODULE_EXPIRED / FREE_PLAN_NOT_ACTIVATED /
 * NO_ACCESS) — quem decide o status HTTP exposto ao frontend é o
 * auth-service, que já tinha esse contrato antes da migração.
 */
@Path("/api/v1/internal/module-access")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
public class ModuleAccessResource {

    @Inject
    EntityManager em;

    @GET
    @Path("/{moduleSlug}")
    @SuppressWarnings("unchecked")
    public Response resolve(@PathParam("moduleSlug") String moduleSlug, @Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        UUID tenantId = ctx.getTenantId();

        List<Object[]> moduleRows = em.createNativeQuery(
            "SELECT id::text, name FROM platform_modules WHERE slug = :slug AND is_active = TRUE"
        ).setParameter("slug", moduleSlug).getResultList();

        if (moduleRows.isEmpty()) {
            return Response.ok(Map.of("resolution", "MODULE_NOT_FOUND")).build();
        }

        String moduleId   = (String) moduleRows.get(0)[0];
        String moduleName = (String) moduleRows.get(0)[1];

        // Verifica acesso em três etapas (da mais específica para a mais ampla):
        //  a) profile_module_subscriptions ACTIVE/TRIAL/TRIAL_CANCELLED → SUBSCRIPTION
        //  b) plan_version_modules free (price=0) sem assinatura ainda   → FREE_PLAN_NOT_ACTIVATED
        //  c) moduleSlugSet do TenantContext (assinatura principal do tenant, legado) → TENANT_SUBSCRIPTION
        List<Object[]> subRows = em.createNativeQuery(
            "SELECT pvm.id::text, p.name, " +
            "  (pms.expires_at IS NOT NULL AND pms.expires_at < NOW()) AS past_expiry " +
            "FROM profile_module_subscriptions pms " +
            "JOIN plan_version_modules pvm ON pvm.id = pms.plan_version_id " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "WHERE pms.module_id = :moduleId AND pms.tenant_id = :tenantId " +
            "  AND pms.status IN ('ACTIVE', 'TRIAL', 'TRIAL_CANCELLED') " +
            "LIMIT 1"
        )
        .setParameter("moduleId", UUID.fromString(moduleId))
        .setParameter("tenantId", tenantId)
        .getResultList();

        // Assinatura ativa mas vencida: bloqueia direto, não cai para FREE_PLAN/TENANT_SUBSCRIPTION.
        if (!subRows.isEmpty() && Boolean.TRUE.equals(subRows.get(0)[2])) {
            return Response.ok(Map.of(
                "resolution", "MODULE_EXPIRED",
                "moduleId", moduleId,
                "moduleName", moduleName
            )).build();
        }

        String planVersionId = null;
        String planName;
        String accessSource;

        if (!subRows.isEmpty()) {
            planVersionId = (String) subRows.get(0)[0];
            planName      = (String) subRows.get(0)[1];
            accessSource  = "SUBSCRIPTION";
        } else {
            List<Object[]> freeRows = em.createNativeQuery(
                "SELECT pvm.id::text FROM plan_version_modules pvm " +
                "JOIN plans p ON p.id = pvm.plan_id " +
                "WHERE pvm.module_id = :moduleId AND pvm.status = 'active' AND pvm.monthly_price = 0 " +
                "LIMIT 1"
            ).setParameter("moduleId", UUID.fromString(moduleId)).getResultList();

            if (!freeRows.isEmpty()) {
                return Response.ok(Map.of(
                    "resolution", "FREE_PLAN_NOT_ACTIVATED",
                    "moduleId", moduleId,
                    "moduleName", moduleName,
                    "planVersionId", freeRows.get(0)[0]
                )).build();
            } else if (ctx.hasFeature(moduleSlug)) {
                planName     = ctx.getPlanCode();
                accessSource = "TENANT_SUBSCRIPTION";
            } else {
                return Response.ok(Map.of(
                    "resolution", "NO_ACCESS",
                    "moduleId", moduleId,
                    "moduleName", moduleName
                )).build();
            }
        }

        Map<String, Object> limits = loadPlanLimits(planVersionId, moduleSlug);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resolution", "GRANTED");
        result.put("moduleId", moduleId);
        result.put("moduleName", moduleName);
        result.put("planName", planName);
        result.put("accessSource", accessSource);
        result.put("limits", limits);
        return Response.ok(result).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadPlanLimits(String planVersionId, String moduleSlug) {
        if (planVersionId == null) return Map.of();

        // planVersionId é o id de plan_version_modules (não de plan_versions)
        List<Object[]> limitRows = em.createNativeQuery(
            "SELECT pvml.code, pvml.limit_value FROM plan_version_module_limits pvml " +
            "WHERE pvml.plan_version_module_id = :planVersionId"
        ).setParameter("planVersionId", UUID.fromString(planVersionId)).getResultList();

        // pvml.code é persistido como "<moduleSlug>.<limitCode>" (estável entre
        // upgrades/downgrades de plano); o token remove o prefixo pois já
        // carrega o moduleSlug em sua própria claim.
        String prefix = moduleSlug + ".";
        Map<String, Object> limits = new LinkedHashMap<>();
        for (Object[] row : limitRows) {
            String code = (String) row[0];
            String key = (code != null && code.startsWith(prefix)) ? code.substring(prefix.length()) : code;
            limits.put(key, row[1]);
        }
        return limits;
    }
}
