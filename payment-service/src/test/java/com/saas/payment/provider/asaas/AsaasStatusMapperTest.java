package com.saas.payment.provider.asaas;

import com.saas.payment.enums.PaymentMethod;
import com.saas.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsaasStatusMapperTest {

    @Test
    void mapsReceivedToPaid() {
        assertEquals(PaymentStatus.PAID, AsaasStatusMapper.fromPaymentStatus("RECEIVED"));
    }

    @Test
    void mapsConfirmedToPaid() {
        assertEquals(PaymentStatus.PAID, AsaasStatusMapper.fromPaymentStatus("CONFIRMED"));
    }

    @Test
    void mapsOverdueToOverdue() {
        assertEquals(PaymentStatus.OVERDUE, AsaasStatusMapper.fromPaymentStatus("OVERDUE"));
    }

    @Test
    void mapsRefundedToRefunded() {
        assertEquals(PaymentStatus.REFUNDED, AsaasStatusMapper.fromPaymentStatus("REFUNDED"));
    }

    @Test
    void mapsChargebackRequestedToFailed() {
        assertEquals(PaymentStatus.FAILED, AsaasStatusMapper.fromPaymentStatus("CHARGEBACK_REQUESTED"));
    }

    @Test
    void mapsUnknownPaymentStatusToPending() {
        assertEquals(PaymentStatus.PENDING, AsaasStatusMapper.fromPaymentStatus("SOME_FUTURE_ASAAS_STATUS"));
        assertEquals(PaymentStatus.PENDING, AsaasStatusMapper.fromPaymentStatus(null));
    }

    @Test
    void mapsSubscriptionActiveToPaid() {
        assertEquals(PaymentStatus.PAID, AsaasStatusMapper.fromSubscriptionStatus("ACTIVE"));
    }

    @Test
    void mapsSubscriptionInactiveToCancelled() {
        assertEquals(PaymentStatus.CANCELLED, AsaasStatusMapper.fromSubscriptionStatus("INACTIVE"));
    }

    @Test
    void mapsBillingTypePix() {
        assertEquals(PaymentMethod.PIX, AsaasStatusMapper.fromBillingType("PIX"));
    }

    @Test
    void mapsUnknownBillingTypeToUnknown() {
        assertEquals(PaymentMethod.UNKNOWN, AsaasStatusMapper.fromBillingType("UNDEFINED"));
    }
}
