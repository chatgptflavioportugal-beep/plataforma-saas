package com.saas.subscription.resource;

import com.saas.subscription.negocio.impl.ProfileModuleSubscriptionNegocio;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.UUID;

/**
 * Notificação de mudança de status financeiro, enviada pelo payment-service
 * quando um pagamento é aprovado/recusado/vencido. Endpoint interno,
 * serviço-a-serviço — o payment-service não tem o JWT do usuário disponível
 * neste fluxo (a notificação nasce de um webhook do gateway, sem contexto de
 * usuário), por isso a autenticação aqui é por segredo compartilhado
 * (X-Internal-Token), diferente do padrão de repasse de Authorization usado
 * em ModuleAccessResource. Rota excluída do TenantResolutionFilter (ver
 * TenantResolutionFilter.isExcluded) — não há tenant a resolver a partir de
 * um JWT que não existe nesta chamada.
 *
 * O Payment Service é dono da parte financeira; este serviço só traduz a
 * notificação em transição de status de profile_module_subscriptions (ver
 * ProfileModuleSubscriptionNegocio.applyPaymentStatus) — nenhuma regra
 * financeira vive aqui.
 */
@Path("/api/v1/internal/payments")
@Tag(name = "Internal Payments", description = "Notificação interna, serviço-a-serviço, de mudança de status de pagamento (payment-service). Não é chamada pelo frontend nem exposta ao subscription-service público.")
@Consumes(MediaType.APPLICATION_JSON)
public class InternalPaymentResource {

    @Inject
    ProfileModuleSubscriptionNegocio subscriptionNegocio;

    @ConfigProperty(name = "app.internal.service-token")
    String internalToken;

    @POST
    @Path("/{subscriptionId}/status")
    @Operation(
        summary = "Recebe do payment-service uma mudança de status de pagamento",
        description = "Chamado pelo payment-service (nunca pelo frontend) sempre que o status financeiro de um " +
            "pagamento muda — criação, webhook do gateway, cancelamento ou reembolso. Autenticado por segredo " +
            "compartilhado (header X-Internal-Token), não por JWT de usuário. Idempotente: reenviar a mesma " +
            "notificação não tem efeito colateral além da primeira aplicação bem-sucedida."
    )
    @APIResponse(responseCode = "204", description = "Notificação recebida e aplicada (ou ignorada, se a assinatura não estava no status esperado para a transição).")
    @APIResponse(responseCode = "401", description = "X-Internal-Token ausente ou inválido.")
    public Response updateStatus(
            @HeaderParam("X-Internal-Token") String receivedToken,
            @Parameter(description = "id de profile_module_subscriptions ao qual o pagamento se refere.", required = true)
            @PathParam("subscriptionId") UUID subscriptionId,
            PaymentStatusNotification notification) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(receivedToken)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("error", "X-Internal-Token inválido"))
                    .build();
        }

        subscriptionNegocio.applyPaymentStatus(subscriptionId, notification.status());
        return Response.noContent().build();
    }

    public record PaymentStatusNotification(
            UUID paymentId,
            String gateway,
            String gatewayPaymentId,
            String status,
            Instant occurredAt
    ) {
    }
}
