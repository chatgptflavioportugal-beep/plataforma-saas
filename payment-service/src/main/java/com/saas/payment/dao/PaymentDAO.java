package com.saas.payment.dao;

import com.saas.payment.entity.Payment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PaymentDAO implements PanacheRepositoryBase<Payment, UUID> {

    @Transactional
    public void save(Payment payment) {
        payment.persist();
    }

    public Optional<Payment> findByGatewayPaymentId(String gateway, String gatewayPaymentId) {
        return find("gateway = ?1 and gatewayPaymentId = ?2", gateway, gatewayPaymentId).firstResultOptional();
    }

    public Optional<Payment> findByGatewaySubscriptionId(String gateway, String gatewaySubscriptionId) {
        return find("gateway = ?1 and gatewaySubscriptionId = ?2", gateway, gatewaySubscriptionId)
                .firstResultOptional();
    }

    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return find("idempotencyKey", idempotencyKey).firstResultOptional();
    }
}
