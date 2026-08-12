package com.saas.admin.controller;

import com.saas.admin.client.SubscriptionServiceClient;
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
 * Ações administrativas sobre o ciclo de vida de assinaturas — cancelar/
 * reativar em nome de um administrador da plataforma.
 *
 * frontend-admin só pode consumir admin-service; profile_module_subscriptions
 * é tabela de propriedade de subscription-service (regra de table ownership),
 * então este recurso apenas repassa a requisição — autenticação e checagem
 * de permissão administrativa continuam sendo feitas por lá, em
 * AdminAuthService, usando o mesmo Authorization repassado aqui.
 */
@Path("/api/v1/admin/subscriptions")
@Tag(name = "Subscriptions", description = "Ações administrativas sobre o ciclo de vida de assinaturas de módulo de qualquer tenant (cancelar/reativar em nome de um administrador da plataforma). Contexto exclusivamente administrativo — não deve ser utilizado pelo ambiente cliente. Este recurso é um proxy transparente para o admin-endpoint equivalente do subscription-service, único dono da tabela profile_module_subscriptions.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminSubscriptionResource {

    @Inject
    @RestClient
    SubscriptionServiceClient subscriptionServiceClient;

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
