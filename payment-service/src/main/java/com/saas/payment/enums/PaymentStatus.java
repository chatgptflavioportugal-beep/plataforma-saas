package com.saas.payment.enums;

/**
 * Status interno do pagamento, independente do gateway. Cada
 * {@link com.saas.payment.provider.PaymentProvider} é responsável por
 * converter o status bruto do gateway (ex.: Stripe "payment_intent.succeeded",
 * Asaas "PAYMENT_RECEIVED") para um destes valores — o restante da aplicação
 * nunca deve conhecer o vocabulário de status de um gateway específico.
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    AUTHORIZED,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED,
    EXPIRED,
    OVERDUE
}
