package com.saas.resource;

import com.saas.security.TenantContext;
import com.saas.security.TokenService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gera o ModuleAccessToken (MAT) quando o usuário entra em um módulo.
 *
 * Autenticação: Supabase JWT + X-Tenant-ID (via TenantResolutionFilter existente).
 * O MAT carrega apenas permissões e limites do módulo solicitado.
 * Validade curta (padrão 30 min), renovável pelo frontend.
 */
@Path("/api/v1/module-token/{moduleSlug}")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ModuleTokenResource {

    @Inject
    EntityManager em;

    @Inject
    TokenService tokenService;

    @POST
    @SuppressWarnings("unchecked")
    public Response generate(
            @PathParam("moduleSlug") String moduleSlug,
            @Context SecurityContext secCtx
    ) {
        var ctx = TenantContext.from(secCtx);
        UUID userId   = ctx.getUserId();
        UUID tenantId = ctx.getTenantId();
        String role   = ctx.getUserRole();

        // 1. Resolve o módulo pelo slug
        List<Object[]> moduleRows = em.createNativeQuery("""
            SELECT id::text, name FROM platform_modules
            WHERE slug = :slug AND is_active = TRUE
        """).setParameter("slug", moduleSlug).getResultList();

        if (moduleRows.isEmpty()) {
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado: " + moduleSlug)).build();
        }

        String moduleId   = (String) moduleRows.get(0)[0];
        String moduleName = (String) moduleRows.get(0)[1];

        // 2. Verifica acesso em três etapas (da mais específica para a mais ampla):
        //    a) profile_module_subscriptions ACTIVE  → SUBSCRIPTION
        //    b) plan_version_modules free (price=0)  → FREE_PLAN
        //    c) moduleSlugSet do TenantContext        → TENANT_SUBSCRIPTION (caminho legado)
        //       O moduleSlugSet vem da assinatura principal do tenant (tenant_subscriptions),
        //       que é o acesso que funcionava antes desta feature.

        List<Object[]> subRows = em.createNativeQuery("""
            SELECT pms.id::text, pvm.id::text, p.name, p.code
            FROM profile_module_subscriptions pms
            JOIN plan_version_modules pvm ON pvm.id = pms.plan_version_id
            JOIN plans p ON p.id = pvm.plan_id
            WHERE pms.module_id = :moduleId
              AND pms.tenant_id = :tenantId
              AND pms.status = 'ACTIVE'
            LIMIT 1
        """)
        .setParameter("moduleId", UUID.fromString(moduleId))
        .setParameter("tenantId", tenantId)
        .getResultList();

        String planName    = null;
        String accessSource;
        String planVersionId = null;

        if (!subRows.isEmpty()) {
            planVersionId = (String) subRows.get(0)[1];
            planName      = (String) subRows.get(0)[2];
            accessSource  = "SUBSCRIPTION";
        } else {
            // b) Plano free associado ao módulo
            List<Object[]> freeRows = em.createNativeQuery("""
                SELECT pvm.id::text, p.name
                FROM plan_version_modules pvm
                JOIN plans p ON p.id = pvm.plan_id
                WHERE pvm.module_id = :moduleId
                  AND pvm.status = 'active'
                  AND pvm.monthly_price = 0
                LIMIT 1
            """).setParameter("moduleId", UUID.fromString(moduleId)).getResultList();

            if (!freeRows.isEmpty()) {
                planVersionId = (String) freeRows.get(0)[0];
                planName      = (String) freeRows.get(0)[1];
                accessSource  = "FREE_PLAN";
            } else if (ctx.hasFeature(moduleSlug)) {
                // c) Acesso via assinatura principal do tenant (legado, compatibilidade)
                planName     = ctx.getPlanCode();
                accessSource = "TENANT_SUBSCRIPTION";
            } else {
                return Response.status(403).entity(Map.of(
                        "error", "Acesso negado ao módulo: sem assinatura ativa nem plano gratuito",
                        "moduleSlug", moduleSlug
                )).build();
            }
        }

        // 3. Carrega permissões do módulo para o usuário
        List<String> permissions = loadModulePermissions(userId, tenantId, moduleId, moduleSlug, role);

        // 4. Carrega limites do plano (plan_version_module_limits)
        List<Map<String, Object>> limits = loadPlanLimits(moduleId, planVersionId);

        // 5. Versão de permissões para invalidação
        int permissionsVersion = resolvePermissionsVersion(userId, tenantId);

        long expiryMinutes = 30;
        Instant expiresAt = Instant.now().plusSeconds(expiryMinutes * 60);

        String token = tokenService.generateModuleToken(
                userId,
                tenantId,
                moduleId,
                moduleSlug,
                planName,
                accessSource,
                permissions,
                limits,
                permissionsVersion
        );

        return Response.ok(Map.of(
                "moduleAccessToken", token,
                "moduleSlug",        moduleSlug,
                "moduleName",        moduleName,
                "planName",          planName != null ? planName : "",
                "expiresAt",         expiresAt.toString(),
                "permissions",       permissions,
                "limits",            limits
        )).build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> loadModulePermissions(UUID userId, UUID tenantId,
                                                String moduleId, String moduleSlug, String role) {
        List<String> permissions = new ArrayList<>();

        if (List.of("owner", "admin").contains(role)) {
            // Owner/admin tem acesso a todos os serviços do módulo
            List<Object[]> svcRows = em.createNativeQuery("""
                SELECT s.slug,
                       CASE WHEN g.status = 'ACTIVE' THEN g.slug ELSE NULL END AS group_slug
                FROM platform_module_services s
                LEFT JOIN platform_module_service_groups g ON g.id = s.service_group_id AND g.status = 'ACTIVE'
                WHERE s.module_id = :moduleId AND s.is_active = TRUE
            """).setParameter("moduleId", UUID.fromString(moduleId)).getResultList();

            for (Object[] row : svcRows) {
                String serviceSlug = (String) row[0];
                String groupSlug   = (String) row[1];
                String key = (groupSlug != null)
                        ? "module." + moduleSlug + "." + groupSlug + "." + serviceSlug
                        : "module." + moduleSlug + "." + serviceSlug;
                permissions.add(key);
            }
        } else {
            // Membro: filtra pelos serviços do nível de acesso
            List<Object[]> svcRows = em.createNativeQuery("""
                SELECT s.slug,
                       CASE WHEN g.status = 'ACTIVE' THEN g.slug ELSE NULL END AS group_slug
                FROM profile_access_level_permissions palp
                JOIN user_tenants ut ON ut.access_level_id = palp.access_level_id
                JOIN platform_module_services s ON s.id = palp.service_id
                LEFT JOIN platform_module_service_groups g ON g.id = s.service_group_id AND g.status = 'ACTIVE'
                WHERE ut.user_id = :userId
                  AND ut.tenant_id = :tenantId
                  AND ut.is_active = TRUE
                  AND palp.module_id = :moduleId
                  AND s.is_active = TRUE
            """)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .setParameter("moduleId", UUID.fromString(moduleId))
            .getResultList();

            for (Object[] row : svcRows) {
                String serviceSlug = (String) row[0];
                String groupSlug   = (String) row[1];
                String key = (groupSlug != null)
                        ? "module." + moduleSlug + "." + groupSlug + "." + serviceSlug
                        : "module." + moduleSlug + "." + serviceSlug;
                permissions.add(key);
            }
        }

        return permissions;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadPlanLimits(String moduleId, String planVersionId) {
        if (planVersionId == null) return List.of();

        // planVersionId é o id de plan_version_modules (não de plan_versions)
        List<Object[]> limitRows = em.createNativeQuery("""
            SELECT pvml.code, pvml.limit_value, pvml.unit
            FROM plan_version_module_limits pvml
            WHERE pvml.plan_version_module_id = :planVersionId
        """)
        .setParameter("planVersionId", UUID.fromString(planVersionId))
        .getResultList();

        List<Map<String, Object>> limits = new ArrayList<>();
        for (Object[] row : limitRows) {
            limits.add(Map.of(
                    "key",   row[0],
                    "value", row[1],
                    "unit",  row[2] != null ? row[2] : ""
            ));
        }
        return limits;
    }

    private int resolvePermissionsVersion(UUID userId, UUID tenantId) {
        try {
            Number v = (Number) em.createNativeQuery(
                "SELECT permissions_version FROM user_tenants WHERE user_id = :uid AND tenant_id = :tid"
            ).setParameter("uid", userId).setParameter("tid", tenantId).getSingleResult();
            return v != null ? v.intValue() : 1;
        } catch (Exception e) {
            return 1;
        }
    }
}
