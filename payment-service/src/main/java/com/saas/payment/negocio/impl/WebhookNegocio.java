package com.saas.payment.negocio.impl;

import com.saas.payment.enums.PaymentGateway;

import java.util.Map;

/**
 * Recebimento e processamento de webhooks dos gateways. Sempre: (1) valida
 * autenticidade, (2) verifica duplicidade (idempotência), (3) localiza o
 * pagamento correspondente, (4) atualiza o status interno, (5) notifica o
 * subscription-service quando houver subscriptionId associado.
 */
public interface WebhookNegocio {

    void processWebhook(PaymentGateway gateway, String rawPayload, Map<String, String> headers);
}
