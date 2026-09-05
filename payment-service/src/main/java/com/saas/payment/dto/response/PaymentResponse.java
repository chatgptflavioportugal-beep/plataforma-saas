package com.saas.payment.dto.response;

import com.saas.payment.entity.Payment;
import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID subscriptionId,
        UUID customerId,
        PaymentGateway gateway,
        String gatewayPaymentId,
        String gatewayCustomerId,
        String gatewaySubscriptionId,
        String paymentMethod,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String checkoutUrl,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.id,
                payment.subscriptionId,
                payment.customerId,
                PaymentGateway.valueOf(payment.gateway),
                payment.gatewayPaymentId,
                payment.gatewayCustomerId,
                payment.gatewaySubscriptionId,
                payment.paymentMethod,
                payment.amount,
                payment.currency,
                PaymentStatus.valueOf(payment.status),
                payment.feeAmount,
                payment.netAmount,
                payment.checkoutUrl,
                payment.metadata,
                payment.createdAt,
                payment.updatedAt
        );
    }
}
