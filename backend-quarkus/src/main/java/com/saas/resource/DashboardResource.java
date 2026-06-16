package com.saas.resource;

import com.saas.security.TenantContext;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/dashboard")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    EntityManager em;

    /**
     * Retorna todos os módulos da plataforma com status de acesso para o perfil ativo.
     *
     * accessStatus:
     *   SUBSCRIBED — perfil possui assinatura ativa deste módulo
     *   FREE       — módulo possui plano com preço zero disponível (sem assinatura ativa)
     *   LOCKED     — módulo não disponível e sem plano gratuito
     *
     * Serviços são retornados apenas para SUBSCRIBED e FREE.
     * Para membros com nível de acesso, serviços SUBSCRIBED são filtrados pelas permissões do nível.
     * Ordem de retorno: SUBSCRIBED → FREE → LOCKED.
     */
    @GET
    @Path("/modules")
    @SuppressWarnings("unchecked")
    public Response getDashboardModules(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        UUID tenantId = ctx.getTenantId();
        UUID userId   = ctx.getUserId();
        String role   = ctx.getUserRole();

        // 1. Todos os módulos ativos com status de assinatura do perfil e flag de plano free
        List<Object[]> moduleRows = em.createNativeQuery("""
            SELECT
              pm.id::text,
              pm.name,
              pm.slug,
              pm.description,
              pm.icon_path,
              pm.sort_order,
              pms.id::text               AS sub_id,
              pms.status                 AS sub_status,
              p.name                     AS plan_name,
              p.code                     AS plan_slug,
              pvm_sub.id::text           AS plan_version_id,
              (SELECT COUNT(*) FROM platform_module_services s
               WHERE s.module_id = pm.id AND s.is_active = TRUE) AS service_count,
              CASE WHEN EXISTS (
                SELECT 1 FROM plan_version_modules pvm2
                WHERE pvm2.module_id = pm.id
                  AND pvm2.status = 'active'
                  AND pvm2.monthly_price = 0
              ) THEN 1 ELSE 0 END        AS has_free_plan
            FROM platform_modules pm
            LEFT JOIN profile_module_subscriptions pms
              ON pms.module_id = pm.id
             AND pms.tenant_id = :tenantId
             AND pms.status = 'ACTIVE'
            LEFT JOIN plan_version_modules pvm_sub ON pvm_sub.id = pms.plan_version_id
            LEFT JOIN plans p ON p.id = pvm_sub.plan_id
            WHERE pm.is_active = TRUE
            ORDER BY pm.sort_order
        """).setParameter("tenantId", tenantId).getResultList();

        // 2. Permissões do membro (somente role = member com access_level_id)
        Set<String> memberServiceIds = null;
        if ("member".equals(role)) {
            List<String> permRows = em.createNativeQuery("""
                SELECT palp.service_id::text
                FROM profile_access_level_permissions palp
                JOIN user_tenants ut ON ut.access_level_id = palp.access_level_id
                WHERE ut.user_id = :userId
                  AND ut.tenant_id = :tenantId
                  AND ut.is_active = TRUE
            """)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .getResultList();
            memberServiceIds = new HashSet<>(permRows);
        }

        final Set<String> finalMemberServiceIds = memberServiceIds;

        List<Map<String, Object>> subscribed = new ArrayList<>();
        List<Map<String, Object>> free       = new ArrayList<>();
        List<Map<String, Object>> locked     = new ArrayList<>();

        for (Object[] row : moduleRows) {
            String moduleId      = (String) row[0];
            String moduleName    = (String) row[1];
            String moduleSlug    = (String) row[2];
            String moduleDesc    = (String) row[3];
            String moduleIcon    = (String) row[4];
            String subId         = (String) row[6];
            String subStatus     = (String) row[7];
            String planName      = (String) row[8];
            String planSlug      = (String) row[9];
            String planVersionId = (String) row[10];
            long serviceCount    = ((Number) row[11]).longValue();
            boolean hasFreePlan  = ((Number) row[12]).intValue() == 1;

            boolean isSubscribed = subId != null && "ACTIVE".equals(subStatus);

            String accessStatus;
            String badgeLabel;
            if (isSubscribed) {
                accessStatus = "SUBSCRIBED";
                badgeLabel   = planName != null ? planName : "Ativo";
            } else if (hasFreePlan) {
                accessStatus = "FREE";
                badgeLabel   = "Free";
            } else {
                accessStatus = "LOCKED";
                badgeLabel   = "Contratar";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("moduleId",      moduleId);
            item.put("moduleName",    moduleName);
            item.put("moduleSlug",    moduleSlug);
            item.put("moduleDescription", moduleDesc);
            item.put("moduleIconPath", moduleIcon);
            item.put("accessStatus",  accessStatus);
            item.put("planName",      planName);
            item.put("planSlug",      planSlug);
            item.put("planVersionId", planVersionId);
            item.put("badgeLabel",    badgeLabel);
            item.put("serviceCount",  serviceCount);

            if (!"LOCKED".equals(accessStatus)) {
                List<Object[]> svcRows = em.createNativeQuery("""
                    SELECT
                      s.id::text,
                      s.name,
                      s.slug,
                      s.description,
                      s.icon_path,
                      CASE WHEN g.status = 'ACTIVE' THEN g.id::text ELSE NULL END,
                      CASE WHEN g.status = 'ACTIVE' THEN g.name ELSE NULL END,
                      s.sort_order,
                      CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END,
                      s.route_key
                    FROM platform_module_services s
                    LEFT JOIN platform_module_service_groups g
                      ON g.id = s.service_group_id AND g.status = 'ACTIVE'
                    WHERE s.module_id = :moduleId AND s.is_active = TRUE
                    ORDER BY
                      CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END,
                      s.sort_order
                """).setParameter("moduleId", UUID.fromString(moduleId)).getResultList();

                List<Map<String, Object>> services = new ArrayList<>();
                for (Object[] srow : svcRows) {
                    String serviceId = (String) srow[0];

                    // Membros: filtrar por permissão em módulos assinados e free
                    if (finalMemberServiceIds != null) {
                        if (!finalMemberServiceIds.contains(serviceId)) continue;
                    }

                    Map<String, Object> svc = new LinkedHashMap<>();
                    svc.put("serviceId",          serviceId);
                    svc.put("serviceName",        srow[1]);
                    svc.put("serviceSlug",        srow[2]);
                    svc.put("serviceDescription", srow[3]);
                    svc.put("serviceIconPath",    srow[4]);
                    svc.put("serviceGroupId",     srow[5]);
                    svc.put("serviceGroupName",   srow[6]);
                    svc.put("routeKey",           srow[9]);
                    svc.put("hasAccess",          true);
                    services.add(svc);
                }
                item.put("services", services);
            } else {
                item.put("services", List.of());
            }

            switch (accessStatus) {
                case "SUBSCRIBED" -> subscribed.add(item);
                case "FREE"       -> free.add(item);
                default           -> locked.add(item);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(subscribed);
        result.addAll(free);
        result.addAll(locked);

        return Response.ok(result).build();
    }
}
