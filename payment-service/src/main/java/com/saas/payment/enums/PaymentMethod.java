package com.saas.payment.enums;

/**
 * Método de pagamento normalizado entre gateways. Nunca representa dados
 * sensíveis do cartão — apenas a categoria do meio de pagamento usado.
 */
public enum PaymentMethod {
    CREDIT_CARD,
    BOLETO,
    PIX,
    BANK_TRANSFER,
    UNKNOWN
}
