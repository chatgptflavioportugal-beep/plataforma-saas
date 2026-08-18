package com.saas.admin.controller;

import com.saas.admin.client.SubscriptionServiceClient;
import com.saas.admin.dto.SubscriptionPageDTO;
import com.saas.admin.dto.SubscriptionsSummaryDTO;
import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.AdminGeneralService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Visão administrativa (listagem/resumo) e ciclo de vida (cancelar/reativar)
 * de assinaturas de módulo, em nome de um administrador da plataforma.
 *
 * Listagem/resumo (GET) consultam profile_module_subscriptions diretamente
 * via AdminGeneralService. Cancelamento/reativação (POST) são um proxy puro
 * para subscription-service, único dono de escrita naquela tabela (regra de
 * table ownership) — autenticação e checagem de permissão administrativa
 * para essas duas ações são feitas por lá, em AdminAuthService, usando o
 * mesmo Authorization repassado aqui.
 *
 * Todos os métodos deste recurso precisam ficar nesta única classe: duas
 * classes JAX-RS mapeando o mesmo path literal "/api/v1/admin/subscriptions"
 * faz o RESTEasy Reactive rotear tudo para uma delas e 404 nos métodos da
 * outra.
 */
@Path("/api/v1/admin/subscriptions")
@Tag(name = "Subscriptions", description = "Visão administrativa e ações sobre o ciclo de vida de assinaturas de módulo de qualquer tenant (listar, resumir, cancelar/reativar em nome de um administrador da plataforma). Contexto exclusivamente administrativo — não deve ser utilizado pelo ambiente cliente. Listagem/resumo leem profile_module_subscriptions diretamente; cancelar/reativar são um proxy transparente para o admin-endpoint equivalente do subscription-service, único dono de escrita naquela tabela.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminSubscriptionResource {

    @Inject
    @RestClient
    SubscriptionServiceClient subscriptionServiceClient;

    @Inject
    AdminGeneralService adminGeneralService;

    @Inject
    AdminAuthService adminAuth;

    @GET
    @Path("/summary")
    @Operation(
        summary = "Retorna contadores agregados de assinaturas de módulos por perfil",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Agrega, sobre profile_module_subscriptions, o total de assinaturas e a contagem por " +
            "status (ACTIVE, CANCELED, EXPIRED, PENDING_PAYMENT, TRIAL, TRIAL_CANCELLED) e por ciclo de " +
            "cobrança (MONTHLY, ANNUAL). Não aplica filtros. Requer a permissão granular " +
            "'admin.subscriptions.view'."
    )
    @APIResponse(responseCode = "200", description = "Objeto com os contadores agregados de assinaturas.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.subscriptions.view'.")
    public Response getSubscriptionsSummary() {
        adminAuth.requireAdminPermission("admin.subscriptions.view");

        SubscriptionsSummaryDTO summary = adminGeneralService.getSubscriptionsSummary();
        return Response.ok(summary).build();
    }

    @GET
    @Operation(
        summary = "Lista, com paginação, as assinaturas de módulos sob a visão administrativa",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Lista registros de profile_module_subscriptions com dados agregados do perfil " +
            "(individual ou empresa), do módulo, do plano/versão contratada e do status da assinatura. " +
            "Suporta um amplo conjunto de filtros combináveis (busca textual, tipo/ID de perfil, empresa, " +
            "usuário, módulo, plano, ciclo de cobrança, status, intervalo de data de início, janela de " +
            "expiração pré-definida e status de renovação) e paginação via 'page'/'size' (size limitado a " +
            "1..100). O cancelamento/reativação de assinaturas não é feito por este serviço — é responsabilidade " +
            "do subscription-service, chamado diretamente pelo frontend-admin. Requer a permissão granular " +
            "'admin.subscriptions.view'."
    )
    @APIResponse(responseCode = "200", description = "Página de assinaturas que atendem aos filtros informados, com total de itens.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.subscriptions.view'.")
    public Response listSubscriptions(
            @Parameter(description = "Busca textual por nome da empresa, do módulo, do plano, do owner ou e-mail do owner (opcional).")
            @QueryParam("search")        String search,
            @Parameter(description = "Filtra pelo tipo de perfil: 'INDIVIDUAL' ou qualquer outro valor é tratado como empresa (opcional).")
            @QueryParam("profileType")   String profileType,
            @Parameter(description = "Filtra por ID (UUID) do tenant/perfil (individual ou empresa). Opcional.")
            @QueryParam("profileId")     String profileId,
            @Parameter(description = "Filtra por ID (UUID) da empresa (tenant do tipo 'business'). Opcional.")
            @QueryParam("companyId")     String companyId,
            @Parameter(description = "Filtra por ID do usuário owner ou por trecho do e-mail do owner. Opcional.")
            @QueryParam("userId")        String userId,
            @Parameter(description = "Filtra por ID (UUID) do módulo da plataforma. Opcional.")
            @QueryParam("moduleId")      String moduleId,
            @Parameter(description = "Filtra por ID (UUID) do plano. Opcional.")
            @QueryParam("planId")        String planId,
            @Parameter(description = "Filtra pelo ciclo de cobrança: MONTHLY ou ANNUAL. Opcional.")
            @QueryParam("billingCycle")  String billingCycle,
            @Parameter(description = "Filtra pelo status da assinatura (ex.: ACTIVE, CANCELED, EXPIRED, TRIAL). Opcional.")
            @QueryParam("status")        String status,
            @Parameter(description = "Data/hora mínima de início da assinatura (filtro >=). Opcional.")
            @QueryParam("startDateFrom") String startDateFrom,
            @Parameter(description = "Data/hora máxima de início da assinatura (filtro <=). Opcional.")
            @QueryParam("startDateTo")   String startDateTo,
            @Parameter(description = "Janela de expiração pré-definida: '7', '15', '30' (dias a partir de agora), 'overdue' (já expiradas) ou 'none' (sem data de expiração). Opcional.")
            @QueryParam("expiresIn")     String expiresIn,
            @Parameter(description = "Filtra por status de renovação: 'active' (assinatura ACTIVE) ou 'canceled' (CANCELED/EXPIRED). Opcional.")
            @QueryParam("renewalStatus") String renewalStatus,
            @Parameter(description = "Número da página, começando em 0. Padrão: 0.")
            @QueryParam("page")          @DefaultValue("0") int page,
            @Parameter(description = "Quantidade de itens por página (limitado a 1..100). Padrão: 20.")
            @QueryParam("size")          @DefaultValue("20") int size
    ) {
        adminAuth.requireAdminPermission("admin.subscriptions.view");

        SubscriptionPageDTO result = adminGeneralService.listSubscriptions(
            search, profileType, profileId, companyId, userId, moduleId, planId, billingCycle, status,
            startDateFrom, startDateTo, expiresIn, renewalStatus, page, size);

        return Response.ok(result).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Operation(
        summary = "Cancela (como administrador) a assinatura de um módulo de qualquer tenant",
        description = "Este endpoint pertence exclusivamente ao contexto administrativo e não " +
            "deve ser utilizado pelo ambiente cliente. O admin-service não escreve " +
            "diretamente em profile_module_subscriptions (tabela de propriedade do " +
            "subscription-service) — este recurso é um proxy puro: repassa o cabeçalho " +
            "`Authorization` recebido para `POST /api/v1/admin/subscriptions/{id}/cancel` no " +
            "subscription-service e devolve exatamente o status HTTP e o corpo retornados por " +
            "lá. A checagem da permissão administrativa `admin.subscriptions.cancel` (via " +
            "AdminAuthService) é feita no subscription-service, não neste serviço. " +
            "Assinaturas em TRIAL viram TRIAL_CANCELLED (acesso mantido até expirar); " +
            "assinaturas ACTIVE viram CANCELED."
    )
    @APIResponse(responseCode = "200", description = "Assinatura cancelada com sucesso no subscription-service; retorna o novo status (CANCELED ou TRIAL_CANCELLED), repassado do upstream.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado (validado tanto neste serviço quanto, novamente, no subscription-service).")
    @APIResponse(responseCode = "403", description = "Repassado do subscription-service: usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.subscriptions.cancel`.")
    @APIResponse(responseCode = "404", description = "Repassado do subscription-service: assinatura não encontrada para o `id` informado, ou já está em um status diferente de ACTIVE/TRIAL (já cancelada).")
    public Response cancel(
            @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a cancelar.", required = true) @PathParam("id") String id,
            @Context HttpHeaders httpHeaders) {
        String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);
        try (Response upstream = subscriptionServiceClient.cancelSubscription(authorization, id)) {
            return Response.status(upstream.getStatus()).entity(upstream.readEntity(String.class)).build();
        }
    }

    @POST
    @Path("/{id}/reactivate")
    @Operation(
        summary = "Reativa (como administrador) uma assinatura de módulo cancelada de qualquer tenant",
        description = "Este endpoint pertence exclusivamente ao contexto administrativo e não " +
            "deve ser utilizado pelo ambiente cliente. Assim como `POST /{id}/cancel`, é um " +
            "proxy puro para `POST /api/v1/admin/subscriptions/{id}/reactivate` no " +
            "subscription-service — repassa o cabeçalho `Authorization` recebido e devolve " +
            "exatamente o status HTTP e o corpo retornados por lá, onde a permissão " +
            "administrativa `admin.subscriptions.reactivate` é checada (via AdminAuthService). " +
            "Só reativa assinaturas ainda dentro da validade (`expires_at` nulo ou futuro): " +
            "CANCELED volta para ACTIVE, TRIAL_CANCELLED volta para TRIAL."
    )
    @APIResponse(responseCode = "200", description = "Assinatura reativada com sucesso no subscription-service; retorna o novo status (ACTIVE ou TRIAL), repassado do upstream.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado (validado tanto neste serviço quanto, novamente, no subscription-service).")
    @APIResponse(responseCode = "403", description = "Repassado do subscription-service: usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.subscriptions.reactivate`.")
    @APIResponse(responseCode = "404", description = "Repassado do subscription-service: assinatura não encontrada, não está em status CANCELED/TRIAL_CANCELLED, ou já expirou.")
    public Response reactivate(
            @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a reativar.", required = true) @PathParam("id") String id,
            @Context HttpHeaders httpHeaders) {
        String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);
        try (Response upstream = subscriptionServiceClient.reactivateSubscription(authorization, id)) {
            return Response.status(upstream.getStatus()).entity(upstream.readEntity(String.class)).build();
        }
    }
}
