package com.saas.payment.provider.stripe;

import com.saas.payment.enums.PaymentMethod;
import com.saas.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O restante do payment-service nunca deve conhecer o vocabulário de status
 * do Stripe — estes testes fixam exatamente essa tradução (ex.: Stripe
 * "succeeded" → PaymentStatus.PAID) para que uma mudança acidental no mapping
 * quebre o build em vez de vazar silenciosamente.
 */
class StripeStatusMapperTest {

    @Test
    void mapsPaymentIntentSucceededToPaid() {
        assertEquals(PaymentStatus.PAID, StripeStatusMapper.fromPaymentIntentStatus("succeeded"));
    }

    @Test
    void mapsPaymentIntentRequiresActionToPending() {
        assertEquals(PaymentStatus.PENDING, StripeStatusMapper.fromPaymentIntentStatus("requires_action"));
    }

    @Test
    void mapsPaymentIntentCanceledToCancelled() {
        assertEquals(PaymentStatus.CANCELLED, StripeStatusMapper.fromPaymentIntentStatus("canceled"));
    }

    @Test
    void mapsUnknownPaymentIntentStatusToPending() {
        assertEquals(PaymentStatus.PENDING, StripeStatusMapper.fromPaymentIntentStatus("something_new_from_stripe"));
    }

    @Test
    void mapsSubscriptionActiveToPaid() {
        assertEquals(PaymentStatus.PAID, StripeStatusMapper.fromSubscriptionStatus("active"));
    }

    @Test
    void mapsSubscriptionPastDueToOverdue() {
        assertEquals(PaymentStatus.OVERDUE, StripeStatusMapper.fromSubscriptionStatus("past_due"));
    }

    @Test
    void mapsSubscriptionIncompleteExpiredToExpired() {
        assertEquals(PaymentStatus.EXPIRED, StripeStatusMapper.fromSubscriptionStatus("incomplete_expired"));
    }

    @Test
    void eventTypeOverridesFallbackWhenKnown() {
        assertEquals(PaymentStatus.FAILED, StripeStatusMapper.fromEventType("payment_intent.payment_failed", PaymentStatus.PAID));
    }

    @Test
    void eventTypeFallsBackWhenUnknown() {
        assertEquals(PaymentStatus.PAID, StripeStatusMapper.fromEventType("payment_intent.created", PaymentStatus.PAID));
    }

    @Test
    void mapsCardPaymentMethodType() {
        assertEquals(PaymentMethod.CREDIT_CARD, StripeStatusMapper.fromPaymentMethodType("card"));
    }

    @Test
    void mapsUnknownPaymentMethodTypeToUnknown() {
        assertEquals(PaymentMethod.UNKNOWN, StripeStatusMapper.fromPaymentMethodType("some_future_type"));
        assertEquals(PaymentMethod.UNKNOWN, StripeStatusMapper.fromPaymentMethodType(null));
    }
}
