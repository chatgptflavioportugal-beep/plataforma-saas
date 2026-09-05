package com.saas.payment.provider.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.math.BigDecimal;

/**
 * Cliente REST cru para a API do Asaas (v3) — sem SDK oficial Java. Os
 * registros abaixo usam {@code @JsonProperty} explícito em todos os campos
 * porque o ObjectMapper da aplicação está configurado globalmente em
 * SNAKE_CASE (para o contrato com o frontend), mas a API do Asaas espera
 * camelCase (ex.: "cpfCnpj", "nextDueDate", "externalReference") — o
 * @JsonProperty sempre prevalece sobre a estratégia de nomenclatura global.
 *
 * A API key é enviada explicitamente em cada chamada (header "access_token")
 * pelo {@link AsaasPaymentProvider}, em vez de via {@code @ClientHeaderParam}
 * estático, porque o valor vem de configuração (payment.asaas.api-key), não
 * de uma constante.
 */
@RegisterRestClient(configKey = "asaas-api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AsaasApiClient {

    @POST
    @Path("/customers")
    AsaasCustomer createCustomer(@HeaderParam("access_token") String apiKey, AsaasCustomerRequest request);

    @POST
    @Path("/payments")
    AsaasPayment createPayment(@HeaderParam("access_token") String apiKey, AsaasPaymentRequest request);

    @GET
    @Path("/payments/{id}")
    AsaasPayment getPayment(@HeaderParam("access_token") String apiKey, @PathParam("id") String id);

    @DELETE
    @Path("/payments/{id}")
    AsaasPayment cancelPayment(@HeaderParam("access_token") String apiKey, @PathParam("id") String id);

    @POST
    @Path("/payments/{id}/refund")
    AsaasPayment refundPayment(@HeaderParam("access_token") String apiKey, @PathParam("id") String id, AsaasRefundRequest request);

    @POST
    @Path("/subscriptions")
    AsaasSubscription createSubscription(@HeaderParam("access_token") String apiKey, AsaasSubscriptionRequest request);

    @GET
    @Path("/subscriptions/{id}")
    AsaasSubscription getSubscription(@HeaderParam("access_token") String apiKey, @PathParam("id") String id);

    @PUT
    @Path("/subscriptions/{id}")
    AsaasSubscription updateSubscription(@HeaderParam("access_token") String apiKey, @PathParam("id") String id, AsaasSubscriptionUpdateRequest request);

    @DELETE
    @Path("/subscriptions/{id}")
    AsaasSubscription cancelSubscription(@HeaderParam("access_token") String apiKey, @PathParam("id") String id);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasCustomerRequest(
            @JsonProperty("name") String name,
            @JsonProperty("cpfCnpj") String cpfCnpj,
            @JsonProperty("externalReference") String externalReference
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasCustomer(
            @JsonProperty("id") String id,
            @JsonProperty("externalReference") String externalReference
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasPaymentRequest(
            @JsonProperty("customer") String customer,
            @JsonProperty("billingType") String billingType,
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("dueDate") String dueDate,
            @JsonProperty("description") String description,
            @JsonProperty("externalReference") String externalReference
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasPayment(
            @JsonProperty("id") String id,
            @JsonProperty("customer") String customer,
            @JsonProperty("subscription") String subscription,
            @JsonProperty("status") String status,
            @JsonProperty("billingType") String billingType,
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("netValue") BigDecimal netValue,
            @JsonProperty("invoiceUrl") String invoiceUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasRefundRequest(
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("description") String description
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasSubscriptionRequest(
            @JsonProperty("customer") String customer,
            @JsonProperty("billingType") String billingType,
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("nextDueDate") String nextDueDate,
            @JsonProperty("cycle") String cycle,
            @JsonProperty("description") String description,
            @JsonProperty("externalReference") String externalReference
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasSubscriptionUpdateRequest(
            @JsonProperty("value") BigDecimal value
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AsaasSubscription(
            @JsonProperty("id") String id,
            @JsonProperty("customer") String customer,
            @JsonProperty("status") String status,
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("cycle") String cycle
    ) {
    }
}
