package com.saas.payment.negocio;

import com.saas.payment.dao.PaymentDAO;
import com.saas.payment.dao.PaymentWebhookEventDAO;
import com.saas.payment.entity.Payment;
import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.exception.PaymentValidationException;
import com.saas.payment.negocio.impl.WebhookNegocio;
import com.saas.payment.provider.PaymentProvider;
import com.saas.payment.provider.PaymentProviderResolver;
import com.saas.payment.provider.WebhookParseResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class WebhookNegocioImpl implements WebhookNegocio {

    private static final Logger LOG = Logger.getLogger(WebhookNegocioImpl.class);

    @Inject
    PaymentProviderResolver providerResolver;

    @Inject
    PaymentDAO paymentDAO;

    @Inject
    PaymentWebhookEventDAO webhookEventDAO;

    @Inject
    SubscriptionNotifier subscriptionNotifier;

    @Override
    @Transactional
    public void processWebhook(PaymentGateway gateway, String rawPayload, Map<String, String> headers) {
        PaymentProvider provider = providerResolver.resolve(gateway);

        if (!provider.isValidWebhookSignature(rawPayload, headers)) {
            LOG.warnf("Webhook %s recusado: assinatura/token inválido", gateway);
            throw new PaymentValidationException("Assinatura de webhook inválida");
        }

        WebhookParseResult parsed = provider.parseWebhookEvent(rawPayload, headers);

        boolean isNewEvent = webhookEventDAO.insertIfNew(gateway.name(), parsed.externalEventId(), parsed.eventType(), rawPayload);
        if (!isNewEvent) {
            LOG.infof("Webhook %s duplicado ignorado [eventId=%s, type=%s]", gateway, parsed.externalEventId(), parsed.eventType());
            return;
        }

        try {
            applyToPayment(gateway, parsed);
        } finally {
            webhookEventDAO.markProcessed(gateway.name(), parsed.externalEventId());
        }
    }

    private void applyToPayment(PaymentGateway gateway, WebhookParseResult parsed) {
        Optional<Payment> paymentOpt = parsed.gatewayPaymentId() != null
                ? paymentDAO.findByGatewayPaymentId(gateway.name(), parsed.gatewayPaymentId())
                : Optional.empty();

        if (paymentOpt.isEmpty() && parsed.gatewaySubscriptionId() != null) {
            paymentOpt = paymentDAO.findByGatewaySubscriptionId(gateway.name(), parsed.gatewaySubscriptionId());
        }

        if (paymentOpt.isEmpty()) {
            LOG.infof("Webhook %s [eventId=%s, type=%s] não corresponde a nenhum pagamento conhecido — ignorado",
                    gateway, parsed.externalEventId(), parsed.eventType());
            return;
        }

        Payment payment = paymentOpt.get();
        if (parsed.gatewaySubscriptionId() != null) {
            payment.gatewaySubscriptionId = parsed.gatewaySubscriptionId();
        }
        payment.status = parsed.status().name();
        payment.updatedAt = OffsetDateTime.now();

        subscriptionNotifier.notifyStatusChange(payment);
    }
}
