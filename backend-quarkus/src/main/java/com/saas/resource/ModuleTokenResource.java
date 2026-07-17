package com.saas.resource;

import com.saas.repository.UserTenantRepository;
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

    @Inject
    UserTenantRepository userTenantRepository;

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
        //    b) plan_version_modules free (price=0) sem assinatura → ainda não ativado
        //       (o front deve chamar POST /api/v1/subscriptions/free e tentar de novo)
        //    c) moduleSlugSet do TenantContext        → TENANT_SUBSCRIPTION (caminho legado)
        //       O moduleSlugSet vem da assinatura principal do tenant (tenant_subscriptions),
        //       que é o acesso que funcionava antes desta feature.

        List<Object[]> subRows = em.createNativeQuery("""
            SELECT pms.id::text, pvm.id::text, p.name, p.code,
                   (pms.expires_at IS NOT NULL AND pms.expires_at < NOW()) AS past_expiry
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

        // Assinatura ativa mas vencida: bloqueia direto, não cai para FREE_PLAN/TENANT_SUBSCRIPTION.
        if (!subRows.isEmpty() && Boolean.TRUE.equals(subRows.get(0)[4])) {
            return Response.status(403).entity(Map.of(
                    "code", "MODULE_EXPIRED",
                    "error", "Assinatura deste módulo expirou",
                    "moduleSlug", moduleSlug
            )).build();
        }

        String planName    = null;
        String accessSource;
        String planVersionId = null;

        if (!subRows.isEmpty()) {
            planVersionId = (String) subRows.get(0)[1];
            planName      = (String) subRows.get(0)[2];
            accessSource  = "SUBSCRIPTION";
        } else {
            // b) Plano free associado ao módulo, mas ainda não ativado (sem linha em
            //    profile_module_subscriptions). Não emite token — o front deve chamar
            //    POST /api/v1/subscriptions/free para ativar e então tentar de novo.
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
                return Response.status(409).entity(Map.of(
                        "code", "FREE_PLAN_NOT_ACTIVATED",
                        "error", "Plano gratuito disponível para este módulo, mas ainda não ativado",
                        "moduleSlug", moduleSlug,
                        "moduleId", moduleId,
                        "planVersionId", freeRows.get(0)[0]
                )).build();
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
        Map<String, Object> limits = loadPlanLimits(moduleId, planVersionId, moduleSlug);

        // 5. Versão de permissões para invalidação
        int permissionsVersion = userTenantRepository.resolvePermissionsVersion(userId, tenantId);

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
        // moduleSlug já é conhecido pelo token (claim moduleSlug); a permissão
        // carrega apenas o identificador do serviço dentro do módulo.
        List<String> permissions = new ArrayList<>();

        if (List.of("owner", "admin").contains(role)) {
            // Owner/admin tem acesso a todos os serviços do módulo
            List<String> svcRows = em.createNativeQuery("""
                SELECT s.slug
                FROM platform_module_services s
                WHERE s.module_id = :moduleId AND s.is_active = TRUE
            """).setParameter("moduleId", UUID.fromString(moduleId)).getResultList();

            permissions.addAll(svcRows);
        } else {
            // Membro: filtra pelos serviços do nível de acesso
            List<String> svcRows = em.createNativeQuery("""
                SELECT s.slug
                FROM profile_access_level_permissions palp
                JOIN user_tenants ut ON ut.access_level_id = palp.access_level_id
                JOIN platform_module_services s ON s.id = palp.service_id
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

            permissions.addAll(svcRows);
        }

        return permissions;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadPlanLimits(String moduleId, String planVersionId, String moduleSlug) {
        if (planVersionId == null) return Map.of();

        // planVersionId é o id de plan_version_modules (não de plan_versions)
        List<Object[]> limitRows = em.createNativeQuery("""
            SELECT pvml.code, pvml.limit_value
            FROM plan_version_module_limits pvml
            WHERE pvml.plan_version_module_id = :planVersionId
        """)
        .setParameter("planVersionId", UUID.fromString(planVersionId))
        .getResultList();

        // pvml.code é persistido como "<moduleSlug>.<limitCode>" (estável entre
        // upgrades/downgrades de plano); o token remove o prefixo pois já
        // carrega o moduleSlug em sua própria claim.
        String prefix = moduleSlug + ".";
        Map<String, Object> limits = new java.util.LinkedHashMap<>();
        for (Object[] row : limitRows) {
            String code = (String) row[0];
            String key = (code != null && code.startsWith(prefix)) ? code.substring(prefix.length()) : code;
            limits.put(key, row[1]);
        }
        return limits;
    }

}
