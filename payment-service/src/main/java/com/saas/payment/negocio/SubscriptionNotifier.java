package com.saas.payment.negocio;

import com.saas.payment.entity.Payment;
import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.enums.PaymentStatus;
import com.saas.payment.repository.SubscriptionServiceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Notifica o subscription-service quando o status financeiro de um pagamento
 * muda. Best-effort: uma falha de rede/indisponibilidade do subscription-service
 * é logada, mas não desfaz nem reverte a operação de pagamento já concluída —
 * `payments` continua sendo a fonte de verdade financeira; o subscription-service
 * pode reconciliar consultando o Payment Service depois.
 */
@ApplicationScoped
public class SubscriptionNotifier {

    private static final Logger LOG = Logger.getLogger(SubscriptionNotifier.class);

    @Inject
    @RestClient
    SubscriptionServiceRepository subscriptionServiceRepository;

    @ConfigProperty(name = "app.internal.service-token")
    String internalToken;

    public void notifyStatusChange(Payment payment) {
        if (payment.subscriptionId == null) {
            return;
        }
        try {
            var notification = new SubscriptionServiceRepository.PaymentStatusNotification(
                    payment.id,
                    PaymentGateway.valueOf(payment.gateway),
                    payment.gatewayPaymentId,
                    PaymentStatus.valueOf(payment.status),
                    Instant.now()
            );
            subscriptionServiceRepository.notifyPaymentStatus(internalToken, payment.subscriptionId, notification);
        } catch (WebApplicationException | ProcessingException e) {
            LOG.errorf(e, "Falha ao notificar subscription-service [paymentId=%s, subscriptionId=%s, status=%s]",
                    payment.id, payment.subscriptionId, payment.status);
        }
    }
}
