package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * {@code subscription} é null quando o pagamento não está vinculado a
 * nenhuma linha de profile_module_subscriptions — o frontend deve exibir
 * "Sem assinatura associada" nesse caso, não esconder a seção.
 *
 * {@code metadata} é o JSON bruto (texto) de {@code payments.metadata} —
 * exibido somente-leitura, sem tentar formalizar campos que o gateway possa
 * ter colocado lá (ex.: due date de boleto) como se fossem campos oficiais.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PaymentDetailDTO(
        String id,
        String externalId,
        String customerId,
        String customerName,
        String customerEmail,
        String type,
        BigDecimal amount,
        String currency,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String status,
        String gateway,
        String paymentMethod,
        String gatewayCustomerId,
        String gatewayPaymentId,
        String gatewaySubscriptionId,
        String metadata,
        String checkoutUrl,
        String createdAt,
        String updatedAt,
        PaymentSubscriptionDTO subscription) {
}
