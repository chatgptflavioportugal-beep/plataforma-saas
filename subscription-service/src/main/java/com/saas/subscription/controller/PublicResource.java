package com.saas.subscription.controller;

import com.saas.subscription.service.PlanService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Endpoints públicos do domínio de planos/assinaturas: catálogo de planos e
 * módulos disponíveis para contratação.
 *
 * Sem autenticação — não passa por @Authenticated nem pelo TenantResolutionFilter
 * (rotas /api/v1/public/** são explicitamente ignoradas pelo filtro). Toda a
 * leitura aqui é SELECT-only (PlanService); nenhum CRUD administrativo de
 * planos vive neste serviço.
 */
@Path("/api/v1/public")
@Tag(name = "Plans", description = "Catálogo público de planos e módulos disponíveis para contratação. Não requer autenticação.")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject
    PlanService planService;

    @GET
    @Path("/plans")
    @Operation(
        summary = "Lista os planos ativos disponíveis para contratação",
        description = "Endpoint público (sem autenticação). Retorna as versões correntes e " +
            "ativas dos planos (`is_active = TRUE AND is_current_version = TRUE`), com preços " +
            "totais calculados a partir dos módulos ativos de cada plano, contagem de módulos e " +
            "a lista resumida de módulos incluídos. Sem o parâmetro `type`, retorna planos de " +
            "todos os tipos. `page`/`size` são opcionais (1-based) — sem eles, retorna o catálogo " +
            "inteiro (mesmo comportamento histórico deste endpoint)."
    )
    @APIResponse(responseCode = "200", description = "Lista de planos ativos (pode ser vazia se nenhum plano ativo corresponder ao filtro).")
    public Response listPlans(
        @Parameter(description = "Filtra pelo tipo de plano (ex.: \"individual\" ou \"business\"). Opcional — sem filtro retorna todos os tipos.")
        @QueryParam("type") String planType,
        @Parameter(description = "Página (1-based), opcional. Só tem efeito quando `size` também é informado.")
        @QueryParam("page") Integer page,
        @Parameter(description = "Tamanho da página, opcional. Só tem efeito quando `page` também é informado.")
        @QueryParam("size") Integer size) {
        return Response.ok(planService.listActivePlans(planType, page, size)).build();
    }

    @GET
    @Path("/modules/billing-options")
    @Operation(
        summary = "Lista os módulos ativos com os planos disponíveis para contratação",
        description = "Endpoint público (sem autenticação), usado pela tela de contratação " +
            "orientada por módulo. Para cada módulo ativo, retorna os serviços que ele contém e " +
            "todas as opções de plano disponíveis (preço mensal/anual, limites e, quando " +
            "existente, a campanha de Trial vigente para aquele plano/módulo). `page`/`size` são " +
            "opcionais (1-based) — sem eles, retorna o catálogo inteiro (mesmo comportamento " +
            "histórico deste endpoint)."
    )
    @APIResponse(responseCode = "200", description = "Lista de módulos ativos com suas opções de plano e serviços.")
    public Response listModuleBillingOptions(
        @Parameter(description = "Página (1-based), opcional. Só tem efeito quando `size` também é informado.")
        @QueryParam("page") Integer page,
        @Parameter(description = "Tamanho da página, opcional. Só tem efeito quando `page` também é informado.")
        @QueryParam("size") Integer size) {
        return Response.ok(planService.listModuleBillingOptions(page, size)).build();
    }
}
