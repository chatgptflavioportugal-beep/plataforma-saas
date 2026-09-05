package com.saas.payment.provider.stripe;

import com.saas.payment.enums.PaymentMethod;
import com.saas.payment.enums.PaymentStatus;

/**
 * Converte o vocabulário de status do Stripe para o {@link PaymentStatus}
 * interno. Único lugar do payment-service que conhece os literais de status
 * do Stripe — nenhuma outra classe deve comparar strings do Stripe diretamente.
 */
final class StripeStatusMapper {

    private StripeStatusMapper() {
    }

    static PaymentStatus fromPaymentIntentStatus(String status) {
        if (status == null) return PaymentStatus.PENDING;
        return switch (status) {
            case "succeeded" -> PaymentStatus.PAID;
            case "processing" -> PaymentStatus.PROCESSING;
            case "requires_capture" -> PaymentStatus.AUTHORIZED;
            case "canceled" -> PaymentStatus.CANCELLED;
            case "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentStatus.PENDING;
            default -> PaymentStatus.PENDING;
        };
    }

    static PaymentStatus fromSubscriptionStatus(String status) {
        if (status == null) return PaymentStatus.PENDING;
        return switch (status) {
            case "active" -> PaymentStatus.PAID;
            case "trialing", "incomplete", "paused" -> PaymentStatus.PENDING;
            case "past_due" -> PaymentStatus.OVERDUE;
            case "canceled" -> PaymentStatus.CANCELLED;
            case "unpaid" -> PaymentStatus.FAILED;
            case "incomplete_expired" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.PENDING;
        };
    }

    static PaymentStatus fromEventType(String eventType, PaymentStatus fallback) {
        if (eventType == null) return fallback;
        return switch (eventType) {
            case "payment_intent.succeeded", "invoice.paid", "checkout.session.completed" -> PaymentStatus.PAID;
            case "payment_intent.payment_failed", "invoice.payment_failed" -> PaymentStatus.FAILED;
            case "payment_intent.canceled", "customer.subscription.deleted" -> PaymentStatus.CANCELLED;
            case "charge.refunded" -> PaymentStatus.REFUNDED;
            default -> fallback;
        };
    }

    static PaymentMethod fromPaymentMethodType(String type) {
        if (type == null) return PaymentMethod.UNKNOWN;
        return switch (type) {
            case "card" -> PaymentMethod.CREDIT_CARD;
            case "boleto" -> PaymentMethod.BOLETO;
            case "pix" -> PaymentMethod.PIX;
            case "us_bank_account", "sepa_debit", "bacs_debit" -> PaymentMethod.BANK_TRANSFER;
            default -> PaymentMethod.UNKNOWN;
        };
    }
}
