package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;

/** TO da camada de dados para uma linha da listagem administrativa de pagamentos. */
public class PaymentListItemTO {

    @Column(name = "id") private String id;
    @Column(name = "external_id") private String externalId;
    @Column(name = "subscription_id") private String subscriptionId;
    @Column(name = "customer_id") private String customerId;
    @Column(name = "customer_name") private String customerName;
    @Column(name = "customer_email") private String customerEmail;
    @Column(name = "module_id") private String moduleId;
    @Column(name = "module_name") private String moduleName;
    @Column(name = "type") private String type;
    @Column(name = "billing_cycle") private String billingCycle;
    @Column(name = "gateway") private String gateway;
    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "amount") private BigDecimal amount;
    @Column(name = "currency") private String currency;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "last_event_type") private String lastEventType;
    @Column(name = "last_event_status") private String lastEventStatus;
    @Column(name = "last_event_at") private String lastEventAt;
    @Column(name = "has_webhook_error") private Boolean hasWebhookError;
    @Column(name = "total_count") private Long totalCount;

    public String id() { return id; }
    public String externalId() { return externalId; }
    public String subscriptionId() { return subscriptionId; }
    public String customerId() { return customerId; }
    public String customerName() { return customerName; }
    public String customerEmail() { return customerEmail; }
    public String moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String type() { return type; }
    public String billingCycle() { return billingCycle; }
    public String gateway() { return gateway; }
    public String paymentMethod() { return paymentMethod; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public String status() { return status; }
    public String createdAt() { return createdAt; }
    public String lastEventType() { return lastEventType; }
    public String lastEventStatus() { return lastEventStatus; }
    public String lastEventAt() { return lastEventAt; }
    public Boolean hasWebhookError() { return hasWebhookError; }
    public long totalCount() { return totalCount != null ? totalCount : 0L; }
}
