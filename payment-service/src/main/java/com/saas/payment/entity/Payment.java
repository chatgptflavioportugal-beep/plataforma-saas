package com.saas.payment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Cobrança financeira controlada pelo Payment Service. subscriptionId aponta
 * para profile_module_subscriptions.id (subscription-service) apenas como
 * referência de correlação — este serviço não conhece nem duplica regra de
 * assinatura, só o suficiente para notificar mudanças de status de volta.
 */
@Entity
@Table(name = "payments")
public class Payment extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    public UUID id;

    @Column(name = "subscription_id")
    public UUID subscriptionId;

    @Column(name = "customer_id", nullable = false)
    public UUID customerId;

    @Column(nullable = false)
    public String gateway;

    @Column(name = "gateway_payment_id")
    public String gatewayPaymentId;

    @Column(name = "gateway_customer_id")
    public String gatewayCustomerId;

    @Column(name = "gateway_subscription_id")
    public String gatewaySubscriptionId;

    @Column(name = "payment_method")
    public String paymentMethod;

    @Column(nullable = false)
    public BigDecimal amount;

    @Column(nullable = false)
    public String currency = "BRL";

    @Column(nullable = false)
    public String status;

    @Column(name = "fee_amount")
    public BigDecimal feeAmount;

    @Column(name = "net_amount")
    public BigDecimal netAmount;

    @Column(name = "idempotency_key")
    public String idempotencyKey;

    @Column(name = "checkout_url")
    public String checkoutUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public Map<String, Object> metadata = Map.of();

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();
}
