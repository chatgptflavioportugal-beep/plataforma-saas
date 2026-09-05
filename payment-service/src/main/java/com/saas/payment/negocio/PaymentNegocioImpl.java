package com.saas.payment.negocio;

import com.saas.payment.dao.PaymentDAO;
import com.saas.payment.dto.request.CreateCheckoutRequest;
import com.saas.payment.dto.request.CreatePaymentRequest;
import com.saas.payment.dto.request.CreateSubscriptionPaymentRequest;
import com.saas.payment.dto.request.RefundPaymentRequest;
import com.saas.payment.dto.response.PaymentResponse;
import com.saas.payment.entity.Payment;
import com.saas.payment.exception.PaymentNotFoundException;
import com.saas.payment.exception.PaymentValidationException;
import com.saas.payment.negocio.impl.PaymentNegocio;
import com.saas.payment.provider.PaymentProviderResolver;
import com.saas.payment.provider.PaymentProviderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PaymentNegocioImpl implements PaymentNegocio {

    @Inject
    PaymentDAO paymentDAO;

    @Inject
    PaymentProviderResolver providerResolver;

    @Inject
    SubscriptionNotifier subscriptionNotifier;

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        var existing = paymentDAO.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }
        if (request.gateway() == null) {
            throw new PaymentValidationException("gateway é obrigatório");
        }

        var provider = providerResolver.resolve(request.gateway());
        PaymentProviderResult result = provider.createPayment(request);

        Payment payment = new Payment();
        payment.subscriptionId = request.subscriptionId();
        payment.customerId = request.customerId();
        payment.gateway = request.gateway().name();
        payment.amount = request.amount();
        payment.currency = request.currency() != null ? request.currency() : "BRL";
        payment.idempotencyKey = request.idempotencyKey();
        payment.metadata = request.metadata() != null ? request.metadata() : Map.of();
        applyResult(payment, result);

        paymentDAO.save(payment);
        subscriptionNotifier.notifyStatusChange(payment);
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse createCheckout(CreateCheckoutRequest request) {
        var existing = paymentDAO.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }
        if (request.gateway() == null) {
            throw new PaymentValidationException("gateway é obrigatório");
        }

        var provider = providerResolver.resolve(request.gateway());
        PaymentProviderResult result = provider.createCheckout(request);

        Payment payment = new Payment();
        payment.subscriptionId = request.subscriptionId();
        payment.customerId = request.customerId();
        payment.gateway = request.gateway().name();
        payment.amount = request.amount();
        payment.currency = request.currency() != null ? request.currency() : "BRL";
        payment.idempotencyKey = request.idempotencyKey();
        payment.metadata = request.metadata() != null ? request.metadata() : Map.of();
        applyResult(payment, result);

        paymentDAO.save(payment);
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse createSubscriptionPayment(CreateSubscriptionPaymentRequest request) {
        var existing = paymentDAO.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }
        if (request.gateway() == null) {
            throw new PaymentValidationException("gateway é obrigatório");
        }

        var provider = providerResolver.resolve(request.gateway());
        PaymentProviderResult result = provider.createSubscription(request);

        Payment payment = new Payment();
        payment.subscriptionId = request.subscriptionId();
        payment.customerId = request.customerId();
        payment.gateway = request.gateway().name();
        payment.amount = request.amount();
        payment.currency = request.currency() != null ? request.currency() : "BRL";
        payment.idempotencyKey = request.idempotencyKey();
        payment.metadata = request.metadata() != null ? request.metadata() : Map.of();
        applyResult(payment, result);

        paymentDAO.save(payment);
        subscriptionNotifier.notifyStatusChange(payment);
        return PaymentResponse.from(payment);
    }

    @Override
    public PaymentResponse getPayment(UUID paymentId) {
        return PaymentResponse.from(findOrThrow(paymentId));
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        var provider = providerResolver.resolve(com.saas.payment.enums.PaymentGateway.valueOf(payment.gateway));
        PaymentProviderResult result = provider.cancelPayment(payment.gatewayPaymentId);
        applyResult(payment, result);
        payment.updatedAt = OffsetDateTime.now();
        subscriptionNotifier.notifyStatusChange(payment);
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse cancelSubscription(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        if (payment.gatewaySubscriptionId == null) {
            throw new PaymentValidationException("Pagamento não corresponde a uma cobrança recorrente");
        }
        var provider = providerResolver.resolve(com.saas.payment.enums.PaymentGateway.valueOf(payment.gateway));
        PaymentProviderResult result = provider.cancelSubscription(payment.gatewaySubscriptionId);
        applyResult(payment, result);
        payment.updatedAt = OffsetDateTime.now();
        subscriptionNotifier.notifyStatusChange(payment);
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(UUID paymentId, RefundPaymentRequest request) {
        Payment payment = findOrThrow(paymentId);
        if (payment.gatewayPaymentId == null) {
            throw new PaymentValidationException("Pagamento ainda não possui cobrança confirmada no gateway");
        }
        var provider = providerResolver.resolve(com.saas.payment.enums.PaymentGateway.valueOf(payment.gateway));
        PaymentProviderResult result = provider.refund(payment.gatewayPaymentId,
                request != null ? request.amount() : null, request != null ? request.reason() : null);
        applyResult(payment, result);
        payment.updatedAt = OffsetDateTime.now();
        subscriptionNotifier.notifyStatusChange(payment);
        return PaymentResponse.from(payment);
    }

    private Payment findOrThrow(UUID paymentId) {
        return paymentDAO.findByIdOptional(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Pagamento não encontrado: " + paymentId));
    }

    private void applyResult(Payment payment, PaymentProviderResult result) {
        if (result.gatewayPaymentId() != null) payment.gatewayPaymentId = result.gatewayPaymentId();
        if (result.gatewayCustomerId() != null) payment.gatewayCustomerId = result.gatewayCustomerId();
        if (result.gatewaySubscriptionId() != null) payment.gatewaySubscriptionId = result.gatewaySubscriptionId();
        if (result.status() != null) payment.status = result.status().name();
        if (result.paymentMethod() != null) payment.paymentMethod = result.paymentMethod().name();
        if (result.amount() != null) payment.amount = result.amount();
        if (result.currency() != null) payment.currency = result.currency();
        if (result.feeAmount() != null) payment.feeAmount = result.feeAmount();
        if (result.netAmount() != null) payment.netAmount = result.netAmount();
        if (result.checkoutUrl() != null) payment.checkoutUrl = result.checkoutUrl();
        if (payment.status == null) payment.status = com.saas.payment.enums.PaymentStatus.PENDING.name();
    }
}
