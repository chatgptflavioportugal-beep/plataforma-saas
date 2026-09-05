package com.saas.payment.resource;

import com.saas.payment.dto.request.CreateCheckoutRequest;
import com.saas.payment.dto.request.CreatePaymentRequest;
import com.saas.payment.dto.request.CreateSubscriptionPaymentRequest;
import com.saas.payment.dto.request.RefundPaymentRequest;
import com.saas.payment.negocio.impl.PaymentNegocio;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * Operações financeiras do Payment Service — chamadas exclusivamente pelo
 * subscription-service em nome de um tenant já autorizado (nunca diretamente
 * pelo frontend). Repassa o mesmo Authorization (JWT Supabase) recebido do
 * frontend, mesmo padrão de propagação usado entre os demais microsserviços
 * (ver auth-service → subscription-service). tenantId/subscriptionId/customerId
 * chegam explícitos no corpo — este serviço não resolve membership de tenant,
 * isso é domínio exclusivo do subscription-service.
 */
@Path("/api/v1/payments")
@Tag(name = "Payments", description = "Criação, consulta, cancelamento e reembolso de cobranças, abstraindo o gateway (Stripe/Asaas). Consumido pelo subscription-service.")
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@SecurityRequirement(name = "bearerAuth")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentNegocio paymentNegocio;

    @POST
    @Operation(summary = "Cria uma cobrança avulsa (não recorrente)",
        description = "Cria a cobrança diretamente no gateway informado (PaymentIntent no Stripe, cobrança UNDEFINED no Asaas). " +
            "Idempotente por `idempotencyKey`: reenviar a mesma chave retorna o pagamento já criado sem duplicar a cobrança no gateway.")
    @APIResponse(responseCode = "200", description = "Cobrança criada (ou já existente, se idempotencyKey repetida).")
    public Response createPayment(CreatePaymentRequest request) {
        return Response.ok(paymentNegocio.createPayment(request)).build();
    }

    @POST
    @Path("/checkout")
    @Operation(summary = "Cria uma sessão de checkout hospedada pelo gateway",
        description = "Retorna `checkout_url` para redirecionamento do usuário à página de pagamento do próprio gateway " +
            "(Stripe Checkout Session, link de cobrança do Asaas).")
    @APIResponse(responseCode = "200", description = "Sessão de checkout criada, com a URL de redirecionamento.")
    public Response createCheckout(CreateCheckoutRequest request) {
        return Response.ok(paymentNegocio.createCheckout(request)).build();
    }

    @POST
    @Path("/subscriptions")
    @Operation(summary = "Cria uma cobrança recorrente (assinatura no gateway)",
        description = "Cria a recorrência no gateway (Subscription no Stripe, subscription no Asaas). O `billingCycle` " +
            "(\"MONTHLY\"/\"ANNUAL\") só é usado para configurar o intervalo de cobrança no gateway.")
    @APIResponse(responseCode = "200", description = "Cobrança recorrente criada.")
    public Response createSubscriptionPayment(CreateSubscriptionPaymentRequest request) {
        return Response.ok(paymentNegocio.createSubscriptionPayment(request)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Consulta um pagamento pelo id interno")
    @APIResponse(responseCode = "200", description = "Pagamento encontrado.")
    @APIResponse(responseCode = "404", description = "Pagamento não encontrado.")
    public Response getPayment(@PathParam("id") UUID id) {
        return Response.ok(paymentNegocio.getPayment(id)).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Operation(summary = "Cancela uma cobrança avulsa ainda não capturada/paga")
    @APIResponse(responseCode = "200", description = "Cobrança cancelada no gateway.")
    public Response cancelPayment(@PathParam("id") UUID id) {
        return Response.ok(paymentNegocio.cancelPayment(id)).build();
    }

    @POST
    @Path("/{id}/cancel-subscription")
    @Operation(summary = "Cancela a recorrência (assinatura) no gateway")
    @APIResponse(responseCode = "200", description = "Recorrência cancelada no gateway.")
    @APIResponse(responseCode = "400", description = "O pagamento informado não corresponde a uma cobrança recorrente.")
    public Response cancelSubscription(@PathParam("id") UUID id) {
        return Response.ok(paymentNegocio.cancelSubscription(id)).build();
    }

    @POST
    @Path("/{id}/refund")
    @Operation(summary = "Solicita reembolso (total ou parcial) de uma cobrança")
    @APIResponse(responseCode = "200", description = "Reembolso solicitado ao gateway.")
    public Response refundPayment(@PathParam("id") UUID id, RefundPaymentRequest request) {
        return Response.ok(paymentNegocio.refundPayment(id, request)).build();
    }
}
