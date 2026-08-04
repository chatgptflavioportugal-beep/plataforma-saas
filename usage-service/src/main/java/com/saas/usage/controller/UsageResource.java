package com.saas.usage.controller;

import com.saas.platformsecurity.CurrentModuleContext;
import com.saas.usage.dto.PagedResponse;
import com.saas.usage.dto.UsageAuditRequest;
import com.saas.usage.dto.UsageIncrementRequest;
import com.saas.usage.dto.UsageIncrementResponse;
import com.saas.usage.service.UsageService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;

/**
 * Controle de uso de módulos: incremento/verificação de quota e auditoria de uso.
 * Todas as rotas exigem ModuleAccessToken (ver ModuleTokenFilter) — tenant, módulo e
 * limites vêm direto das claims do token, sem consultar nenhum outro serviço.
 */
@Path("/api/v1/usage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsageResource {

    @Inject UsageService usageService;
    @Inject CurrentModuleContext contextHolder;

    @POST
    @Path("/increment")
    public Response increment(UsageIncrementRequest body) {
        if (body.metricCode() == null || body.metricCode().isBlank()) {
            return Response.status(400).entity(java.util.Map.of("error", "metricCode é obrigatório")).build();
        }
        var ctx = contextHolder.get();
        Long limit = toLong(ctx.getLimit(body.metricCode()));

        UsageIncrementResponse result = usageService.increment(
                ctx.tenantId(), ctx.userId(), ctx.moduleSlug(),
                body.metricCode(), body.amountOrDefault(), limit
        );

        return result.allowed()
                ? Response.ok(result).build()
                : Response.status(429).entity(result).build();
    }

    @GET
    @Path("/summary")
    public Response summary(@QueryParam("moduleSlug") String moduleSlug,
                             @QueryParam("metricCode") String metricCode,
                             @QueryParam("from") String from,
                             @QueryParam("to") String to,
                             @QueryParam("page") @DefaultValue("0") int page,
                             @QueryParam("pageSize") @DefaultValue("30") int pageSize) {
        var ctx = contextHolder.get();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? LocalDate.parse(to) : LocalDate.now();

        var items = usageService.summary(ctx.tenantId(), moduleSlug, metricCode, fromDate, toDate, page, pageSize);
        long total = usageService.countSummary(ctx.tenantId(), moduleSlug, metricCode, fromDate, toDate);
        return Response.ok(PagedResponse.of(items, page, pageSize, total)).build();
    }

    @POST
    @Path("/audit")
    public Response audit(UsageAuditRequest body) {
        if (body.action() == null || body.action().isBlank()) {
            return Response.status(400).entity(java.util.Map.of("error", "action é obrigatório")).build();
        }
        var ctx = contextHolder.get();
        usageService.audit(ctx.tenantId(), ctx.userId(), ctx.moduleSlug(),
                body.metricCode(), body.action(), body.metadata());
        return Response.status(201).build();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
