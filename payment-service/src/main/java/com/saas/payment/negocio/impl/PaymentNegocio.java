package com.saas.payment.negocio.impl;

import com.saas.payment.dto.request.CreateCheckoutRequest;
import com.saas.payment.dto.request.CreatePaymentRequest;
import com.saas.payment.dto.request.CreateSubscriptionPaymentRequest;
import com.saas.payment.dto.request.RefundPaymentRequest;
import com.saas.payment.dto.response.PaymentResponse;

import java.util.UUID;

/**
 * Regras financeiras do Payment Service: criação de cobrança avulsa,
 * checkout hospedado, cobrança recorrente, cancelamento, reembolso e
 * consulta. Nunca decide regra de assinatura/plano — isso é domínio do
 * subscription-service, apenas notificado (ver SubscriptionServiceRepository)
 * quando o status financeiro muda.
 */
public interface PaymentNegocio {

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse createCheckout(CreateCheckoutRequest request);

    PaymentResponse createSubscriptionPayment(CreateSubscriptionPaymentRequest request);

    PaymentResponse getPayment(UUID paymentId);

    PaymentResponse cancelPayment(UUID paymentId);

    PaymentResponse cancelSubscription(UUID paymentId);

    PaymentResponse refundPayment(UUID paymentId, RefundPaymentRequest request);
}
