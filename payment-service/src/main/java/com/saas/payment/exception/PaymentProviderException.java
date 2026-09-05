package com.saas.payment.exception;

/**
 * Envelope para qualquer erro vindo do SDK/API de um gateway (StripeException,
 * falha HTTP do Asaas, etc.). Os Providers nunca deixam a exceção nativa do
 * gateway escapar para a camada de negócio — sempre convertem para esta
 * exceção (ou uma das suas especializações), preservando a causa original só
 * para log, nunca no contrato exposto ao consumidor.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
