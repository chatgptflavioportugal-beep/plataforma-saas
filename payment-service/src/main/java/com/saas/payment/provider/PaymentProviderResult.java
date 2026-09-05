package com.saas.payment.provider;

import com.saas.payment.enums.PaymentMethod;
import com.saas.payment.enums.PaymentStatus;

import java.math.BigDecimal;

/**
 * Resultado normalizado de uma operação de gateway — o que qualquer
 * {@link PaymentProvider} devolve para a camada de negócio. Nunca carrega
 * tipos da SDK do gateway (PaymentIntent do Stripe, DTO do Asaas, etc.).
 */
public record PaymentProviderResult(
        String gatewayPaymentId,
        String gatewayCustomerId,
        String gatewaySubscriptionId,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        BigDecimal amount,
        String currency,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String checkoutUrl
) {
}
