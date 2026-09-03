package com.saas.usage.resource;

import com.saas.platformsecurity.CurrentModuleContext;
import com.saas.platformsecurity.RequireModuleAccess;
import com.saas.usage.dto.PagedResponse;
import com.saas.usage.dto.UsageAuditRequest;
import com.saas.usage.dto.UsageIncrementRequest;
import com.saas.usage.dto.UsageIncrementResponse;
import com.saas.usage.negocio.impl.UsageNegocio;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;

/**
 * Controle de uso de módulos: incremento/verificação de quota e auditoria de uso.
 * Todas as rotas exigem ModuleAccessToken (ver {@link RequireModuleAccess}) — tenant, módulo e
 * limites vêm direto das claims do token, sem consultar nenhum outro serviço. Aceita token de
 * qualquer módulo (moduleSlug vazio) — usage-service é consumido por todos os módulos, não é
 * exclusivo de um.
 */
@Path("/api/v1/usage")
@Tag(name = "Usage", description = "Incremento de contadores de uso, consulta de consumo e auditoria por módulo.")
@SecurityScheme(
    securitySchemeName = "moduleToken",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "ModuleAccessToken emitido pelo auth-service (POST /api/v1/module-token/{moduleSlug}). Enviar como 'Authorization: Bearer <token>'."
)
@SecurityRequirement(name = "moduleToken")
@RequireModuleAccess(moduleSlug = "")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsageResource {

    @Inject UsageNegocio usageNegocio;
    @Inject CurrentModuleContext contextHolder;

    @POST
    @Path("/increment")
    @Operation(
        summary = "Incrementa o contador de uso de uma métrica do módulo",
        description = "Incrementa (por padrão em 1, ou pelo valor de `amount`) o contador de " +
            "uso do dia para a métrica `metricCode`, no escopo tenant/usuário/módulo do " +
            "ModuleAccessToken. O limite considerado é lido diretamente da claim `limits` do " +
            "token — se o incremento ultrapassar o limite do plano, a operação é recusada " +
            "(mas o contador retorna o estado atual). Sem limite configurado para a métrica, " +
            "o incremento é sempre permitido."
    )
    @APIResponse(responseCode = "200", description = "Incremento permitido; retorna a contagem atualizada, o limite e o quanto ainda resta.")
    @APIResponse(responseCode = "400", description = "`metricCode` ausente ou em branco no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "ModuleAccessToken ausente, inválido, expirado ou emitido para outro módulo.")
    @APIResponse(responseCode = "429", description = "Limite de uso da métrica atingido — incremento não realizado (`allowed=false`).")
    public Response increment(UsageIncrementRequest body) {
        if (body.metricCode() == null || body.metricCode().isBlank()) {
            return Response.status(400).entity(java.util.Map.of("error", "metricCode é obrigatório")).build();
        }
        var ctx = contextHolder.get();
        Long limit = toLong(ctx.getLimit(body.metricCode()));

        UsageIncrementResponse result = usageNegocio.increment(
                ctx.tenantId(), ctx.userId(), ctx.moduleSlug(),
                body.metricCode(), body.amountOrDefault(), limit
        );

        return result.allowed()
                ? Response.ok(result).build()
                : Response.status(429).entity(result).build();
    }

    @GET
    @Path("/summary")
    @Operation(
        summary = "Lista o histórico de uso diário do tenant, paginado",
        description = "Operação exclusivamente de consulta. Retorna os registros diários de " +
            "consumo do tenant do ModuleAccessToken, filtráveis por módulo e por métrica, no " +
            "intervalo de datas informado (padrão: últimos 30 dias até hoje). Não altera " +
            "nenhum contador."
    )
    @APIResponse(responseCode = "200", description = "Página de registros de uso, com o total de itens.")
    @APIResponse(responseCode = "401", description = "ModuleAccessToken ausente, inválido, expirado ou emitido para outro módulo.")
    public Response summary(
            @Parameter(description = "Filtra pelo slug do módulo (opcional; sem filtro retorna todos os módulos do tenant).") @QueryParam("moduleSlug") String moduleSlug,
            @Parameter(description = "Filtra por código da métrica (opcional).") @QueryParam("metricCode") String metricCode,
            @Parameter(description = "Data inicial do intervalo (ISO-8601, ex.: 2026-07-01). Padrão: hoje - 30 dias.") @QueryParam("from") String from,
            @Parameter(description = "Data final do intervalo (ISO-8601, ex.: 2026-08-01). Padrão: hoje.") @QueryParam("to") String to,
            @Parameter(description = "Número da página, começando em 0.") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Quantidade de itens por página.") @QueryParam("pageSize") @DefaultValue("30") int pageSize) {
        var ctx = contextHolder.get();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? LocalDate.parse(to) : LocalDate.now();

        var items = usageNegocio.summary(ctx.tenantId(), moduleSlug, metricCode, fromDate, toDate, page, pageSize);
        long total = usageNegocio.countSummary(ctx.tenantId(), moduleSlug, metricCode, fromDate, toDate);
        return Response.ok(PagedResponse.of(items, page, pageSize, total)).build();
    }

    @POST
    @Path("/audit")
    @Operation(
        summary = "Registra um evento de auditoria de uso do módulo",
        description = "Grava um evento de auditoria (ação livre em `action`, com metadados " +
            "arbitrários em `metadata`) associado ao tenant/usuário/módulo do " +
            "ModuleAccessToken, opcionalmente vinculado a uma métrica. Não incrementa nenhum " +
            "contador de quota — é apenas um registro histórico para rastreabilidade."
    )
    @APIResponse(responseCode = "201", description = "Evento de auditoria registrado com sucesso.")
    @APIResponse(responseCode = "400", description = "`action` ausente ou em branco no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "ModuleAccessToken ausente, inválido, expirado ou emitido para outro módulo.")
    public Response audit(UsageAuditRequest body) {
        if (body.action() == null || body.action().isBlank()) {
            return Response.status(400).entity(java.util.Map.of("error", "action é obrigatório")).build();
        }
        var ctx = contextHolder.get();
        usageNegocio.audit(ctx.tenantId(), ctx.userId(), ctx.moduleSlug(),
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
