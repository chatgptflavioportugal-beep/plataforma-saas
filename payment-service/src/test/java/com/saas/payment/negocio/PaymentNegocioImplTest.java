package com.saas.payment.negocio;

import com.saas.payment.dao.PaymentDAO;
import com.saas.payment.dto.request.CreatePaymentRequest;
import com.saas.payment.dto.request.RefundPaymentRequest;
import com.saas.payment.entity.Payment;
import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.enums.PaymentMethod;
import com.saas.payment.enums.PaymentStatus;
import com.saas.payment.exception.PaymentNotFoundException;
import com.saas.payment.exception.PaymentValidationException;
import com.saas.payment.provider.PaymentProvider;
import com.saas.payment.provider.PaymentProviderResolver;
import com.saas.payment.provider.PaymentProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentNegocioImplTest {

    private PaymentDAO paymentDAO;
    private PaymentProviderResolver resolver;
    private PaymentProvider provider;
    private SubscriptionNotifier subscriptionNotifier;
    private PaymentNegocioImpl negocio;

    @BeforeEach
    void setUp() {
        paymentDAO = mock(PaymentDAO.class);
        resolver = mock(PaymentProviderResolver.class);
        provider = mock(PaymentProvider.class);
        subscriptionNotifier = mock(SubscriptionNotifier.class);
        when(resolver.resolve(PaymentGateway.STRIPE)).thenReturn(provider);

        negocio = new PaymentNegocioImpl();
        negocio.paymentDAO = paymentDAO;
        negocio.providerResolver = resolver;
        negocio.subscriptionNotifier = subscriptionNotifier;
    }

    private CreatePaymentRequest requestWithIdempotencyKey(String idempotencyKey) {
        return new CreatePaymentRequest(UUID.randomUUID(), UUID.randomUUID(), null, PaymentGateway.STRIPE,
                BigDecimal.valueOf(99.90), "BRL", null, "Assinatura módulo PDF", idempotencyKey, null);
    }

    @Test
    void createPaymentDelegatesToTheResolvedProviderAndPersistsTheResult() {
        CreatePaymentRequest request = requestWithIdempotencyKey("idem-1");
        when(paymentDAO.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(provider.createPayment(request)).thenReturn(new PaymentProviderResult(
                "pi_123", "cus_123", null, PaymentStatus.PAID, PaymentMethod.CREDIT_CARD,
                request.amount(), "brl", BigDecimal.valueOf(2.90), BigDecimal.valueOf(97.00), null));

        var response = negocio.createPayment(request);

        assertEquals("pi_123", response.gatewayPaymentId());
        assertEquals(PaymentStatus.PAID, response.status());
        verify(paymentDAO, times(1)).save(any(Payment.class));
        verify(subscriptionNotifier, times(1)).notifyStatusChange(any(Payment.class));
    }

    @Test
    void reusesTheExistingPaymentWhenTheIdempotencyKeyWasAlreadyUsed() {
        CreatePaymentRequest request = requestWithIdempotencyKey("idem-repeated");
        Payment existing = new Payment();
        existing.id = UUID.randomUUID();
        existing.gateway = PaymentGateway.STRIPE.name();
        existing.status = PaymentStatus.PAID.name();
        existing.amount = request.amount();
        when(paymentDAO.findByIdempotencyKey("idem-repeated")).thenReturn(Optional.of(existing));

        var response = negocio.createPayment(request);

        assertEquals(existing.id, response.id());
        verify(provider, never()).createPayment(any());
        verify(paymentDAO, never()).save(any());
    }

    @Test
    void createPaymentRequiresAGateway() {
        CreatePaymentRequest request = new CreatePaymentRequest(UUID.randomUUID(), UUID.randomUUID(), null, null,
                BigDecimal.TEN, "BRL", null, null, "idem-x", null);
        when(paymentDAO.findByIdempotencyKey("idem-x")).thenReturn(Optional.empty());

        assertThrows(PaymentValidationException.class, () -> negocio.createPayment(request));
    }

    @Test
    void getPaymentThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(paymentDAO.findByIdOptional(id)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> negocio.getPayment(id));
    }

    @Test
    void refundPaymentUpdatesStatusAndNotifiesSubscriptionService() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment();
        payment.id = id;
        payment.subscriptionId = UUID.randomUUID();
        payment.gateway = PaymentGateway.STRIPE.name();
        payment.gatewayPaymentId = "pi_999";
        payment.status = PaymentStatus.PAID.name();
        payment.amount = BigDecimal.valueOf(50);
        when(paymentDAO.findByIdOptional(id)).thenReturn(Optional.of(payment));
        when(provider.refund(eq("pi_999"), any(), any())).thenReturn(new PaymentProviderResult(
                "pi_999", null, null, PaymentStatus.REFUNDED, PaymentMethod.CREDIT_CARD,
                BigDecimal.valueOf(50), "brl", null, null, null));

        var response = negocio.refundPayment(id, new RefundPaymentRequest(null, "cliente desistiu"));

        assertEquals(PaymentStatus.REFUNDED, response.status());
        verify(subscriptionNotifier, times(1)).notifyStatusChange(payment);
    }

    @Test
    void refundPaymentFailsWhenPaymentHasNoGatewayId() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment();
        payment.id = id;
        payment.gateway = PaymentGateway.STRIPE.name();
        payment.status = PaymentStatus.PENDING.name();
        when(paymentDAO.findByIdOptional(id)).thenReturn(Optional.of(payment));

        assertThrows(PaymentValidationException.class,
                () -> negocio.refundPayment(id, new RefundPaymentRequest(null, null)));
        verify(provider, never()).refund(any(), any(), any());
    }
}
