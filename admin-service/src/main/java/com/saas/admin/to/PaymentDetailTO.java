package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;

/** TO da camada de dados para o detalhe administrativo de um pagamento (com assinatura associada, quando existir). */
public class PaymentDetailTO {

    @Column(name = "id") private String id;
    @Column(name = "external_id") private String externalId;
    @Column(name = "customer_id") private String customerId;
    @Column(name = "customer_name") private String customerName;
    @Column(name = "customer_email") private String customerEmail;
    @Column(name = "type") private String type;
    @Column(name = "amount") private BigDecimal amount;
    @Column(name = "currency") private String currency;
    @Column(name = "fee_amount") private BigDecimal feeAmount;
    @Column(name = "net_amount") private BigDecimal netAmount;
    @Column(name = "status") private String status;
    @Column(name = "gateway") private String gateway;
    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "gateway_customer_id") private String gatewayCustomerId;
    @Column(name = "gateway_payment_id") private String gatewayPaymentId;
    @Column(name = "gateway_subscription_id") private String gatewaySubscriptionId;
    @Column(name = "metadata") private String metadata;
    @Column(name = "checkout_url") private String checkoutUrl;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
    @Column(name = "subscription_id") private String subscriptionId;
    @Column(name = "subscription_module_id") private String subscriptionModuleId;
    @Column(name = "subscription_module_name") private String subscriptionModuleName;
    @Column(name = "subscription_plan_id") private String subscriptionPlanId;
    @Column(name = "subscription_plan_name") private String subscriptionPlanName;
    @Column(name = "subscription_billing_cycle") private String subscriptionBillingCycle;
    @Column(name = "subscription_status") private String subscriptionStatus;
    @Column(name = "subscription_started_at") private String subscriptionStartedAt;
    @Column(name = "subscription_expires_at") private String subscriptionExpiresAt;

    public String id() { return id; }
    public String externalId() { return externalId; }
    public String customerId() { return customerId; }
    public String customerName() { return customerName; }
    public String customerEmail() { return customerEmail; }
    public String type() { return type; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public BigDecimal feeAmount() { return feeAmount; }
    public BigDecimal netAmount() { return netAmount; }
    public String status() { return status; }
    public String gateway() { return gateway; }
    public String paymentMethod() { return paymentMethod; }
    public String gatewayCustomerId() { return gatewayCustomerId; }
    public String gatewayPaymentId() { return gatewayPaymentId; }
    public String gatewaySubscriptionId() { return gatewaySubscriptionId; }
    public String metadata() { return metadata; }
    public String checkoutUrl() { return checkoutUrl; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
    public String subscriptionId() { return subscriptionId; }
    public String subscriptionModuleId() { return subscriptionModuleId; }
    public String subscriptionModuleName() { return subscriptionModuleName; }
    public String subscriptionPlanId() { return subscriptionPlanId; }
    public String subscriptionPlanName() { return subscriptionPlanName; }
    public String subscriptionBillingCycle() { return subscriptionBillingCycle; }
    public String subscriptionStatus() { return subscriptionStatus; }
    public String subscriptionStartedAt() { return subscriptionStartedAt; }
    public String subscriptionExpiresAt() { return subscriptionExpiresAt; }
}
