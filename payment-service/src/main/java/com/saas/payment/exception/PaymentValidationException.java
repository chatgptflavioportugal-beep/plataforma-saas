package com.saas.payment.exception;

/**
 * Requisição rejeitada pelo gateway ou pela validação local por dado
 * inválido (cartão recusado, moeda não suportada, etc.). Mapeada para HTTP 400.
 */
public class PaymentValidationException extends PaymentProviderException {

    public PaymentValidationException(String message) {
        super(message);
    }

    public PaymentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
