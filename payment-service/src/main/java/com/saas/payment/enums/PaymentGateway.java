package com.saas.payment.enums;

/**
 * Gateway de pagamento responsável por uma cobrança. Uma cobrança pertence a
 * exatamente um gateway — nunca processada simultaneamente por dois.
 */
public enum PaymentGateway {
    STRIPE,
    ASAAS
}
