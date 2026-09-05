package com.saas.payment.exception;

/**
 * O gateway está indisponível/instável (timeout, 5xx, erro de rede) — falha
 * transitória, distinta de PaymentValidationException. Mapeada para HTTP 503.
 */
public class PaymentUnavailableException extends PaymentProviderException {

    public PaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
