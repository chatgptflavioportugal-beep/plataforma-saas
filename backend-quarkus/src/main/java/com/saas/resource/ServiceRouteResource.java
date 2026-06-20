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

@Path("/api/v1/services")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServiceRouteResource {

    @Inject
    EntityManager em;

    /**
     * Resolve um serviço pelo routeKey e valida o acesso do perfil ativo.
     *
     * accessStatus:
     *   ALLOWED         — serviço existe, módulo acessível e perfil tem permissão
     *   DENIED          — serviço existe, mas módulo não acessível ou membro sem permissão
     *   NOT_FOUND       — routeKey não corresponde a nenhum serviço ativo
     */
    @GET
    @Path("/resolve-route/{routeKey}")
    @SuppressWarnings("unchecked")
    public Response resolveRoute(@PathParam("routeKey") String routeKey,
                                 @Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        UUID tenantId = ctx.getTenantId();
        UUID userId   = ctx.getUserId();
        String role   = ctx.getUserRole();

        // 1. Busca o serviço pelo routeKey — permission_key é calculado a partir dos slugs
        List<Object[]> svcRows = em.createNativeQuery("""
            SELECT
              s.id::text,
              s.name,
              s.route_key,
              m.id::text,
              m.name,
              m.slug,
              s.slug,
              g.slug
            FROM platform_module_services s
            JOIN platform_modules m ON m.id = s.module_id
            LEFT JOIN platform_module_service_groups g
              ON g.id = s.service_group_id AND g.status = 'ACTIVE'
            WHERE s.route_key = :routeKey
              AND s.is_active = TRUE
              AND m.is_active = TRUE
        """).setParameter("routeKey", routeKey).getResultList();

        if (svcRows.isEmpty()) {
            return Response.ok(Map.of(
                "routeKey",     routeKey,
                "accessStatus", "NOT_FOUND"
            )).build();
        }

        Object[] srow      = svcRows.get(0);
        String serviceId   = (String) srow[0];
        String serviceName = (String) srow[1];
        String sRouteKey   = (String) srow[2];
        String moduleId    = (String) srow[3];
        String moduleName  = (String) srow[4];
        String moduleSlug  = (String) srow[5];
        String serviceSlug = (String) srow[6];
        String groupSlug   = (String) srow[7];

        // Monta permission_key do mesmo modo que o frontend: module.group?.service
        String permKey = (groupSlug != null)
            ? moduleSlug + "." + groupSlug + "." + serviceSlug
            : moduleSlug + "." + serviceSlug;

        // 2. Verifica se o módulo é acessível para o tenant (SUBSCRIBED ou FREE)
        List<Object[]> accessRows = em.createNativeQuery("""
            SELECT
              pms.id::text,
              pms.status
            FROM profile_module_subscriptions pms
            WHERE pms.module_id = :moduleId
              AND pms.tenant_id = :tenantId
              AND pms.status = 'ACTIVE'
            LIMIT 1
        """)
        .setParameter("moduleId", UUID.fromString(moduleId))
        .setParameter("tenantId", tenantId)
        .getResultList();

        boolean isSubscribed = !accessRows.isEmpty();

        // Verifica se módulo possui plano free
        boolean hasFreePlan = false;
        if (!isSubscribed) {
            List<?> freePlanRows = em.createNativeQuery("""
                SELECT 1 FROM plan_version_modules pvm
                WHERE pvm.module_id = :moduleId
                  AND pvm.status = 'active'
                  AND pvm.monthly_price = 0
                LIMIT 1
            """).setParameter("moduleId", UUID.fromString(moduleId)).getResultList();
            hasFreePlan = !freePlanRows.isEmpty();
        }

        boolean hasModuleAccess = isSubscribed || hasFreePlan;

        if (!hasModuleAccess) {
            return Response.ok(buildResult(serviceId, serviceName, sRouteKey, permKey,
                    moduleId, moduleName, moduleSlug, "DENIED")).build();
        }

        // 3. Para membros com nível de acesso, verificar permissão no serviço (subscribed ou free)
        if ("member".equals(role) && hasModuleAccess) {
            List<?> permRows = em.createNativeQuery("""
                SELECT 1
                FROM profile_access_level_permissions palp
                JOIN user_tenants ut ON ut.access_level_id = palp.access_level_id
                WHERE ut.user_id = :userId
                  AND ut.tenant_id = :tenantId
                  AND ut.is_active = TRUE
                  AND palp.service_id = :serviceId
                LIMIT 1
            """)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .setParameter("serviceId", UUID.fromString(serviceId))
            .getResultList();

            if (permRows.isEmpty()) {
                return Response.ok(buildResult(serviceId, serviceName, sRouteKey, permKey,
                        moduleId, moduleName, moduleSlug, "DENIED")).build();
            }
        }

        return Response.ok(buildResult(serviceId, serviceName, sRouteKey, permKey,
                moduleId, moduleName, moduleSlug, "ALLOWED")).build();
    }

    private Map<String, Object> buildResult(String serviceId, String serviceName,
                                             String routeKey, String permissionKey,
                                             String moduleId, String moduleName,
                                             String moduleSlug, String accessStatus) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serviceId",     serviceId);
        result.put("serviceName",   serviceName);
        result.put("routeKey",      routeKey);
        result.put("permissionKey", permissionKey);
        result.put("moduleId",      moduleId);
        result.put("moduleName",    moduleName);
        result.put("moduleSlug",    moduleSlug);
        result.put("accessStatus",  accessStatus);
        return result;
    }
}
