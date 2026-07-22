package com.saas.subscription.controller;

import com.saas.subscription.security.AdminAuthService;
import com.saas.subscription.service.PlanService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CRUD administrativo de planos, versionamento e módulos/limitações de cada versão.
 * Movido de AdminResource (backend-quarkus). Não usa TenantContext — autorização é
 * feita via papel administrativo global, a mesma checagem que existia no monólito.
 */
@Path("/api/v1/admin/plans")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlanAdminResource {

    @Inject PlanService planService;
    @Inject AdminAuthService adminAuth;

    // ----------------------------------------------------------------
    // Planos
    // ----------------------------------------------------------------

    @GET
    public Response listPlans() {
        adminAuth.requireAdminPermission("admin.plans.view");
        return Response.ok(planService.listAllPlansAdmin()).build();
    }

    @GET
    @Path("/{code}/versions")
    public Response getPlanVersions(@PathParam("code") String code) {
        adminAuth.requireAdminPermission("admin.plans.version_history");
        return Response.ok(planService.getPlanVersionHistory(code)).build();
    }

    @POST
    public Response createPlan(Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.create");
        var req = mapToRequest(body);
        if (req.code() == null || req.code().isBlank())
            return Response.status(400).entity(Map.of("error", "code é obrigatório")).build();
        if (req.name() == null || req.name().isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        return Response.status(201).entity(planService.createPlan(req)).build();
    }

    @POST
    @Path("/{id}/new-version")
    public Response createNewVersion(@PathParam("id") String id, Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.create_version");
        try {
            var req = mapToRequest(body);
            return Response.status(201).entity(planService.createNewVersion(id, req, adminAuth.currentUserId())).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/edit")
    public Response editPlanWithNewVersion(@PathParam("id") String id, Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToRequest(body);
            var modules = mapToPlanModuleWithLimitsRequests(body);
            return Response.status(201).entity(planService.createNewVersionWithModules(id, req, modules, adminAuth.currentUserId())).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.WILDCARD)
    public Response togglePlanStatus(@PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.plans.activate");
        try {
            return Response.ok(planService.togglePlanStatus(id)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}/popular")
    @Consumes(MediaType.WILDCARD)
    public Response setMostPopular(@PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planService.setMostPopular(id)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Módulos da versão do plano (plan_version_modules)
    // ----------------------------------------------------------------

    @GET
    @Path("/{planId}/modules")
    public Response listPlanVersionModules(@PathParam("planId") String planId) {
        adminAuth.requireAdminPermission("admin.plans.view");
        return Response.ok(planService.listPlanVersionModules(planId)).build();
    }

    @POST
    @Path("/{planId}/modules")
    public Response addPlanVersionModule(@PathParam("planId") String planId, Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleRequest(body);
            return Response.status(201).entity(planService.addPlanVersionModule(planId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{planId}/modules/{pvmId}")
    public Response updatePlanVersionModule(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleRequest(body);
            return Response.ok(planService.updatePlanVersionModule(pvmId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{planId}/modules/{pvmId}")
    public Response removePlanVersionModule(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planService.removePlanVersionModule(pvmId)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Limitações dos módulos do plano (plan_version_module_limits)
    // ----------------------------------------------------------------

    @POST
    @Path("/{planId}/modules/{pvmId}/limits")
    public Response addPlanVersionModuleLimit(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleLimitRequest(body);
            return Response.status(201).entity(planService.addPlanVersionModuleLimit(pvmId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{planId}/modules/{pvmId}/limits/{limitId}")
    public Response updatePlanVersionModuleLimit(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            @PathParam("limitId") String limitId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleLimitRequest(body);
            return Response.ok(planService.updatePlanVersionModuleLimit(limitId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{planId}/modules/{pvmId}/limits/{limitId}")
    public Response removePlanVersionModuleLimit(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            @PathParam("limitId") String limitId) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planService.removePlanVersionModuleLimit(limitId)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private PlanService.PlanRequest mapToRequest(Map<String, Object> body) {
        return new PlanService.PlanRequest(
            (String) body.get("name"),
            (String) body.get("code"),
            (String) body.get("description"),
            body.get("price_monthly") != null ? new BigDecimal(body.get("price_monthly").toString()) : null,
            body.get("price_annual")  != null ? new BigDecimal(body.get("price_annual").toString())  : null,
            body.get("discount_annual_percent") != null ? ((Number) body.get("discount_annual_percent")).intValue() : null,
            body.get("max_users") != null ? ((Number) body.get("max_users")).intValue() : null,
            body.get("max_ai_requests_month") != null ? ((Number) body.get("max_ai_requests_month")).intValue() : null,
            (String) body.get("billing_type"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null,
            (String) body.get("plan_type")
        );
    }

    private PlanService.PlanVersionModuleRequest mapToPlanVersionModuleRequest(Map<String, Object> body) {
        return new PlanService.PlanVersionModuleRequest(
            (String) body.get("module_id"),
            body.get("monthly_price")         != null ? new BigDecimal(body.get("monthly_price").toString())         : null,
            body.get("annual_monthly_price")  != null ? new BigDecimal(body.get("annual_monthly_price").toString())  : null,
            (String) body.get("status"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null
        );
    }

    private PlanService.PlanVersionModuleLimitRequest mapToPlanVersionModuleLimitRequest(Map<String, Object> body) {
        return new PlanService.PlanVersionModuleLimitRequest(
            (String) body.get("title"),
            (String) body.get("description"),
            (String) body.get("code"),
            (String) body.get("limit_value"),
            (String) body.get("unit"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null
        );
    }

    @SuppressWarnings("unchecked")
    private List<PlanService.PlanModuleWithLimitsRequest> mapToPlanModuleWithLimitsRequests(Map<String, Object> body) {
        Object raw = body.get("modules");
        if (!(raw instanceof List<?> list)) return null;
        return list.stream().map(item -> {
            Map<String, Object> m = (Map<String, Object>) item;
            List<PlanService.PlanVersionModuleLimitRequest> limits = null;
            if (m.get("limits") instanceof List<?> ll) {
                limits = ll.stream().map(li -> {
                    Map<String, Object> l = (Map<String, Object>) li;
                    return new PlanService.PlanVersionModuleLimitRequest(
                        (String) l.get("title"),
                        (String) l.get("description"),
                        (String) l.get("code"),
                        (String) l.get("limit_value"),
                        (String) l.get("unit"),
                        l.get("sort_order") != null ? ((Number) l.get("sort_order")).intValue() : null
                    );
                }).collect(java.util.stream.Collectors.toList());
            }
            return new PlanService.PlanModuleWithLimitsRequest(
                (String) m.get("module_id"),
                m.get("monthly_price")        != null ? new BigDecimal(m.get("monthly_price").toString())        : null,
                m.get("annual_monthly_price") != null ? new BigDecimal(m.get("annual_monthly_price").toString()) : null,
                (String) m.get("status"),
                m.get("sort_order") != null ? ((Number) m.get("sort_order")).intValue() : null,
                limits
            );
        }).collect(java.util.stream.Collectors.toList());
    }
}
