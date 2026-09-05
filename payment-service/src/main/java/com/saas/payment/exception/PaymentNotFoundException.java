package com.saas.payment.exception;

/** Pagamento não encontrado no Payment Service (não confundir com 404 do gateway). Mapeada para HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
