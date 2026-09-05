package com.saas.payment.resource;

import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.negocio.impl.WebhookNegocio;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoints públicos chamados pelos gateways (Stripe/Asaas), nunca pelo
 * frontend nem pelo subscription-service. Sem {@code @Authenticated} — os
 * gateways não carregam nenhum JWT da plataforma; a autenticidade de cada
 * requisição é validada internamente por cada
 * {@link com.saas.payment.provider.PaymentProvider} (assinatura HMAC no
 * Stripe, token compartilhado no Asaas) antes de qualquer processamento.
 * Idempotente: reenvios do mesmo evento (comportamento normal de retry dos
 * gateways) são identificados e ignorados (ver payment_webhook_events).
 */
@Path("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Recebimento de eventos dos gateways de pagamento. Não requer autenticação da plataforma — a autenticidade é validada por assinatura/token próprio de cada gateway.")
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentWebhookResource {

    @Inject
    WebhookNegocio webhookNegocio;

    @POST
    @Path("/stripe")
    @Operation(summary = "Recebe eventos de webhook do Stripe",
        description = "Valida a assinatura em `Stripe-Signature` antes de processar. Sempre responde 200 quando o " +
            "evento é aceito (mesmo que não corresponda a nenhum pagamento conhecido) para evitar reenvio " +
            "desnecessário pelo Stripe; assinatura inválida responde 400.")
    @APIResponse(responseCode = "200", description = "Evento aceito e processado (ou ignorado por já ter sido processado/não corresponder a um pagamento conhecido).")
    @APIResponse(responseCode = "400", description = "Assinatura do webhook inválida.")
    public Response stripe(String rawPayload, @Context HttpHeaders headers) {
        webhookNegocio.processWebhook(PaymentGateway.STRIPE, rawPayload, toMap(headers));
        return Response.ok().build();
    }

    @POST
    @Path("/asaas")
    @Operation(summary = "Recebe eventos de webhook do Asaas",
        description = "Valida o token compartilhado no header `asaas-access-token` antes de processar. Mesmo " +
            "contrato de resposta do endpoint do Stripe.")
    @APIResponse(responseCode = "200", description = "Evento aceito e processado (ou ignorado por já ter sido processado/não corresponder a um pagamento conhecido).")
    @APIResponse(responseCode = "400", description = "Token do webhook inválido.")
    public Response asaas(String rawPayload, @Context HttpHeaders headers) {
        webhookNegocio.processWebhook(PaymentGateway.ASAAS, rawPayload, toMap(headers));
        return Response.ok().build();
    }

    private Map<String, String> toMap(HttpHeaders headers) {
        Map<String, String> map = new HashMap<>();
        headers.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                map.put(key, values.get(0));
            }
        });
        return map;
    }
}
