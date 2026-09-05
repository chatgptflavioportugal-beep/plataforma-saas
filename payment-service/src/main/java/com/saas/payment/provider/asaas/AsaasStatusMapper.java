package com.saas.payment.provider.asaas;

import com.saas.payment.enums.PaymentMethod;
import com.saas.payment.enums.PaymentStatus;

/**
 * Converte o vocabulário de status do Asaas para o {@link PaymentStatus}
 * interno. Único lugar do payment-service que conhece os literais de status
 * do Asaas.
 */
final class AsaasStatusMapper {

    private AsaasStatusMapper() {
    }

    static PaymentStatus fromPaymentStatus(String status) {
        if (status == null) return PaymentStatus.PENDING;
        return switch (status) {
            case "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> PaymentStatus.PAID;
            case "OVERDUE" -> PaymentStatus.OVERDUE;
            case "REFUNDED", "REFUND_REQUESTED" -> PaymentStatus.REFUNDED;
            case "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE" -> PaymentStatus.FAILED;
            case "AWAITING_RISK_ANALYSIS" -> PaymentStatus.PROCESSING;
            case "PENDING", "AWAITING_CHARGEBACK_REVERSAL", "DUNNING_REQUESTED", "DUNNING_RECEIVED" -> PaymentStatus.PENDING;
            default -> PaymentStatus.PENDING;
        };
    }

    static PaymentStatus fromSubscriptionStatus(String status) {
        if (status == null) return PaymentStatus.PENDING;
        return switch (status) {
            case "ACTIVE" -> PaymentStatus.PAID;
            case "EXPIRED" -> PaymentStatus.EXPIRED;
            case "INACTIVE" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.PENDING;
        };
    }

    static PaymentMethod fromBillingType(String billingType) {
        if (billingType == null) return PaymentMethod.UNKNOWN;
        return switch (billingType) {
            case "CREDIT_CARD" -> PaymentMethod.CREDIT_CARD;
            case "BOLETO" -> PaymentMethod.BOLETO;
            case "PIX" -> PaymentMethod.PIX;
            case "DEPOSIT", "TRANSFER" -> PaymentMethod.BANK_TRANSFER;
            default -> PaymentMethod.UNKNOWN;
        };
    }
}
