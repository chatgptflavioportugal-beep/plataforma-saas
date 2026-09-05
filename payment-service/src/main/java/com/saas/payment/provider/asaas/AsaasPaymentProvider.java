package com.saas.payment.provider.asaas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.payment.dto.request.CreateCheckoutRequest;
import com.saas.payment.dto.request.CreatePaymentRequest;
import com.saas.payment.dto.request.CreateSubscriptionPaymentRequest;
import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.enums.PaymentMethod;
import com.saas.payment.enums.PaymentStatus;
import com.saas.payment.exception.PaymentProviderException;
import com.saas.payment.exception.PaymentUnavailableException;
import com.saas.payment.exception.PaymentValidationException;
import com.saas.payment.provider.PaymentProvider;
import com.saas.payment.provider.PaymentProviderResult;
import com.saas.payment.provider.WebhookParseResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Implementação Asaas do {@link PaymentProvider}. Não existe SDK Java
 * oficial do Asaas — a integração é feita via {@link AsaasApiClient} (REST
 * cru), mas nenhum tipo desse cliente escapa desta classe: a camada de
 * negócio só enxerga {@link PaymentProviderResult}/{@link WebhookParseResult},
 * exatamente como na implementação Stripe.
 */
@ApplicationScoped
public class AsaasPaymentProvider implements PaymentProvider {

    @Inject
    @RestClient
    AsaasApiClient client;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "payment.asaas.api-key")
    String apiKey;

    @ConfigProperty(name = "payment.asaas.webhook-token")
    String webhookToken;

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.ASAAS;
    }

    @Override
    public PaymentProviderResult createPayment(CreatePaymentRequest request) {
        try {
            String customerId = resolveOrCreateCustomer(request.customerId().toString(), request.gatewayCustomerId());
            var payload = new AsaasApiClient.AsaasPaymentRequest(
                    customerId,
                    "UNDEFINED",
                    request.amount(),
                    LocalDate.now().toString(),
                    request.description(),
                    externalReference(request.subscriptionId(), request.customerId())
            );
            AsaasApiClient.AsaasPayment payment = client.createPayment(apiKey, payload);
            return toResult(payment);
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult createCheckout(CreateCheckoutRequest request) {
        try {
            String customerId = resolveOrCreateCustomer(request.customerId().toString(), request.gatewayCustomerId());
            var payload = new AsaasApiClient.AsaasPaymentRequest(
                    customerId,
                    "UNDEFINED",
                    request.amount(),
                    LocalDate.now().toString(),
                    request.description(),
                    externalReference(request.subscriptionId(), request.customerId())
            );
            AsaasApiClient.AsaasPayment payment = client.createPayment(apiKey, payload);
            return new PaymentProviderResult(
                    payment.id(), payment.customer(), payment.subscription(),
                    PaymentStatus.PENDING, PaymentMethod.UNKNOWN,
                    payment.value(), "BRL", null, null, payment.invoiceUrl()
            );
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult createSubscription(CreateSubscriptionPaymentRequest request) {
        try {
            String customerId = resolveOrCreateCustomer(request.customerId().toString(), request.gatewayCustomerId());
            String cycle = "ANNUAL".equalsIgnoreCase(request.billingCycle()) ? "YEARLY" : "MONTHLY";
            var payload = new AsaasApiClient.AsaasSubscriptionRequest(
                    customerId,
                    "UNDEFINED",
                    request.amount(),
                    LocalDate.now().plusDays(1).toString(),
                    cycle,
                    request.description(),
                    externalReference(request.subscriptionId(), request.customerId())
            );
            AsaasApiClient.AsaasSubscription subscription = client.createSubscription(apiKey, payload);
            return new PaymentProviderResult(
                    null, subscription.customer(), subscription.id(),
                    AsaasStatusMapper.fromSubscriptionStatus(subscription.status()), PaymentMethod.UNKNOWN,
                    subscription.value(), "BRL", null, null, null
            );
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult cancelPayment(String gatewayPaymentId) {
        try {
            return toResult(client.cancelPayment(apiKey, gatewayPaymentId));
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult cancelSubscription(String gatewaySubscriptionId) {
        try {
            AsaasApiClient.AsaasSubscription subscription = client.cancelSubscription(apiKey, gatewaySubscriptionId);
            return new PaymentProviderResult(
                    null, subscription.customer(), subscription.id(),
                    PaymentStatus.CANCELLED, PaymentMethod.UNKNOWN, subscription.value(), "BRL", null, null, null
            );
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult changeSubscription(String gatewaySubscriptionId, BigDecimal newAmount, Map<String, Object> metadata) {
        try {
            AsaasApiClient.AsaasSubscription updated = client.updateSubscription(
                    apiKey, gatewaySubscriptionId, new AsaasApiClient.AsaasSubscriptionUpdateRequest(newAmount));
            return new PaymentProviderResult(
                    null, updated.customer(), updated.id(),
                    AsaasStatusMapper.fromSubscriptionStatus(updated.status()), PaymentMethod.UNKNOWN,
                    updated.value(), "BRL", null, null, null
            );
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult refund(String gatewayPaymentId, BigDecimal amount, String reason) {
        try {
            AsaasApiClient.AsaasPayment refunded = client.refundPayment(
                    apiKey, gatewayPaymentId, new AsaasApiClient.AsaasRefundRequest(amount, reason));
            return toResult(refunded);
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult getPayment(String gatewayPaymentId) {
        try {
            return toResult(client.getPayment(apiKey, gatewayPaymentId));
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult getSubscription(String gatewaySubscriptionId) {
        try {
            AsaasApiClient.AsaasSubscription subscription = client.getSubscription(apiKey, gatewaySubscriptionId);
            return new PaymentProviderResult(
                    null, subscription.customer(), subscription.id(),
                    AsaasStatusMapper.fromSubscriptionStatus(subscription.status()), PaymentMethod.UNKNOWN,
                    subscription.value(), "BRL", null, null, null
            );
        } catch (WebApplicationException | ProcessingException e) {
            throw translate(e);
        }
    }

    /**
     * O Asaas não assina o payload (sem HMAC) — a autenticidade é garantida
     * pelo token de acesso configurado no webhook (painel Asaas), reenviado
     * pelo Asaas no header "asaas-access-token" em toda chamada. Comparação
     * feita contra o mesmo segredo configurado no cadastro do webhook.
     */
    @Override
    public boolean isValidWebhookSignature(String rawPayload, Map<String, String> headers) {
        String received = headers.get("asaas-access-token");
        return webhookToken != null && !webhookToken.isBlank() && webhookToken.equals(received);
    }

    @Override
    public WebhookParseResult parseWebhookEvent(String rawPayload, Map<String, String> headers) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.path("event").asText(null);
            JsonNode paymentNode = root.path("payment");
            JsonNode subscriptionNode = root.path("subscription");

            String externalEventId = root.hasNonNull("id")
                    ? root.get("id").asText()
                    : eventType + ":" + paymentNode.path("id").asText(subscriptionNode.path("id").asText(""));

            String gatewayPaymentId = paymentNode.isMissingNode() ? null : paymentNode.path("id").asText(null);
            String gatewaySubscriptionId = !paymentNode.isMissingNode() && paymentNode.hasNonNull("subscription")
                    ? paymentNode.path("subscription").asText(null)
                    : (subscriptionNode.isMissingNode() ? null : subscriptionNode.path("id").asText(null));

            PaymentStatus status = !paymentNode.isMissingNode()
                    ? AsaasStatusMapper.fromPaymentStatus(paymentNode.path("status").asText(null))
                    : AsaasStatusMapper.fromSubscriptionStatus(subscriptionNode.path("status").asText(null));

            return new WebhookParseResult(externalEventId, eventType, gatewayPaymentId, gatewaySubscriptionId, status);
        } catch (Exception e) {
            throw new PaymentValidationException("Payload de webhook Asaas inválido: " + e.getMessage(), e);
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private String resolveOrCreateCustomer(String customerId, String gatewayCustomerId) {
        if (gatewayCustomerId != null && !gatewayCustomerId.isBlank()) {
            return gatewayCustomerId;
        }
        AsaasApiClient.AsaasCustomer customer = client.createCustomer(
                apiKey, new AsaasApiClient.AsaasCustomerRequest("Cliente " + customerId, null, customerId));
        return customer.id();
    }

    private static String externalReference(java.util.UUID subscriptionId, java.util.UUID customerId) {
        return subscriptionId != null ? subscriptionId.toString() : customerId.toString();
    }

    private PaymentProviderResult toResult(AsaasApiClient.AsaasPayment payment) {
        return new PaymentProviderResult(
                payment.id(),
                payment.customer(),
                payment.subscription(),
                AsaasStatusMapper.fromPaymentStatus(payment.status()),
                AsaasStatusMapper.fromBillingType(payment.billingType()),
                payment.value(),
                "BRL",
                null,
                payment.netValue(),
                payment.invoiceUrl()
        );
    }

    private PaymentProviderException translate(RuntimeException e) {
        if (e instanceof WebApplicationException wae) {
            int status = wae.getResponse() != null ? wae.getResponse().getStatus() : 500;
            if (status == 400 || status == 404 || status == 422) {
                return new PaymentValidationException("Asaas rejeitou a requisição: " + e.getMessage(), e);
            }
            return new PaymentUnavailableException("Asaas indisponível (HTTP " + status + ")", e);
        }
        return new PaymentUnavailableException("Falha de comunicação com o Asaas: " + e.getMessage(), e);
    }
}
