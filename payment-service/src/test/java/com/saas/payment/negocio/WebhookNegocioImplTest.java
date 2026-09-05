package com.saas.payment.negocio;

import com.saas.payment.dao.PaymentDAO;
import com.saas.payment.dao.PaymentWebhookEventDAO;
import com.saas.payment.entity.Payment;
import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.enums.PaymentStatus;
import com.saas.payment.exception.PaymentValidationException;
import com.saas.payment.provider.PaymentProvider;
import com.saas.payment.provider.PaymentProviderResolver;
import com.saas.payment.provider.WebhookParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes puros (sem @QuarkusTest) da idempotência e do fluxo de aplicação de
 * webhook — a garantia central pedida: o mesmo evento reentregue pelo gateway
 * nunca é processado duas vezes.
 */
class WebhookNegocioImplTest {

    private PaymentProviderResolver resolver;
    private PaymentProvider provider;
    private PaymentDAO paymentDAO;
    private PaymentWebhookEventDAO webhookEventDAO;
    private SubscriptionNotifier subscriptionNotifier;
    private WebhookNegocioImpl webhookNegocio;

    @BeforeEach
    void setUp() {
        resolver = mock(PaymentProviderResolver.class);
        provider = mock(PaymentProvider.class);
        paymentDAO = mock(PaymentDAO.class);
        webhookEventDAO = mock(PaymentWebhookEventDAO.class);
        subscriptionNotifier = mock(SubscriptionNotifier.class);
        when(resolver.resolve(PaymentGateway.STRIPE)).thenReturn(provider);

        webhookNegocio = new WebhookNegocioImpl();
        webhookNegocio.providerResolver = resolver;
        webhookNegocio.paymentDAO = paymentDAO;
        webhookNegocio.webhookEventDAO = webhookEventDAO;
        webhookNegocio.subscriptionNotifier = subscriptionNotifier;
    }

    @Test
    void rejectsWebhookWithInvalidSignatureBeforeTouchingAnyDao() {
        when(provider.isValidWebhookSignature(anyString(), any())).thenReturn(false);

        assertThrows(PaymentValidationException.class,
                () -> webhookNegocio.processWebhook(PaymentGateway.STRIPE, "{}", Map.of()));

        verify(webhookEventDAO, never()).insertIfNew(any(), any(), any(), any());
        verify(paymentDAO, never()).findByGatewayPaymentId(any(), any());
    }

    @Test
    void ignoresDuplicateEventWithoutReapplyingItToThePayment() {
        when(provider.isValidWebhookSignature(anyString(), any())).thenReturn(true);
        when(provider.parseWebhookEvent(anyString(), any()))
                .thenReturn(new WebhookParseResult("evt_1", "payment_intent.succeeded", "pi_123", null, PaymentStatus.PAID));
        when(webhookEventDAO.insertIfNew("STRIPE", "evt_1", "payment_intent.succeeded", "{}")).thenReturn(false);

        webhookNegocio.processWebhook(PaymentGateway.STRIPE, "{}", Map.of());

        verify(paymentDAO, never()).findByGatewayPaymentId(any(), any());
        verify(subscriptionNotifier, never()).notifyStatusChange(any());
        verify(webhookEventDAO, never()).markProcessed(any(), any());
    }

    @Test
    void appliesNewEventToTheMatchingPaymentAndNotifiesSubscriptionService() {
        UUID subscriptionId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.subscriptionId = subscriptionId;
        payment.gateway = "STRIPE";
        payment.gatewayPaymentId = "pi_123";
        payment.status = PaymentStatus.PENDING.name();

        when(provider.isValidWebhookSignature(anyString(), any())).thenReturn(true);
        when(provider.parseWebhookEvent(anyString(), any()))
                .thenReturn(new WebhookParseResult("evt_2", "payment_intent.succeeded", "pi_123", null, PaymentStatus.PAID));
        when(webhookEventDAO.insertIfNew("STRIPE", "evt_2", "payment_intent.succeeded", "{}")).thenReturn(true);
        when(paymentDAO.findByGatewayPaymentId("STRIPE", "pi_123")).thenReturn(Optional.of(payment));

        webhookNegocio.processWebhook(PaymentGateway.STRIPE, "{}", Map.of());

        assertEquals(PaymentStatus.PAID.name(), payment.status);
        verify(subscriptionNotifier, times(1)).notifyStatusChange(payment);
        verify(webhookEventDAO, times(1)).markProcessed("STRIPE", "evt_2");
    }

    @Test
    void marksEventProcessedEvenWhenNoMatchingPaymentIsFound() {
        when(provider.isValidWebhookSignature(anyString(), any())).thenReturn(true);
        when(provider.parseWebhookEvent(anyString(), any()))
                .thenReturn(new WebhookParseResult("evt_3", "payment_intent.succeeded", "pi_unknown", null, PaymentStatus.PAID));
        when(webhookEventDAO.insertIfNew(eq("STRIPE"), eq("evt_3"), any(), any())).thenReturn(true);
        when(paymentDAO.findByGatewayPaymentId("STRIPE", "pi_unknown")).thenReturn(Optional.empty());

        webhookNegocio.processWebhook(PaymentGateway.STRIPE, "{}", Map.of());

        verify(subscriptionNotifier, never()).notifyStatusChange(any());
        verify(webhookEventDAO, times(1)).markProcessed("STRIPE", "evt_3");
    }
}
