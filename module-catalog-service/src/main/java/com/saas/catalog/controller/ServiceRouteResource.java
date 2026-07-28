package com.saas.catalog.controller;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;

@Path("/api/v1/services")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServiceRouteResource {

    @Inject
    EntityManager em;

    /**
     * Resolve um serviço pelo routeKey — catálogo puro, sem checagem de assinatura
     * ou permissão (isso é responsabilidade do subscription-service/profile-service;
     * o frontend resolve o accessStatus separadamente via subscription-service).
     *
     * accessStatus:
     *   FOUND           — serviço existe e está ativo
     *   NOT_FOUND       — routeKey não corresponde a nenhum serviço ativo
     */
    @GET
    @Path("/resolve-route/{routeKey}")
    @SuppressWarnings("unchecked")
    public Response resolveRoute(@PathParam("routeKey") String routeKey) {
        // Busca o serviço pelo routeKey — permission_key é calculado a partir dos slugs
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

        return Response.ok(buildResult(serviceId, serviceName, sRouteKey, permKey,
                moduleId, moduleName, moduleSlug, "FOUND")).build();
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
