package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o detalhe de um evento de webhook, incluindo o payload bruto. */
public class PaymentEventDetailTO {

    @Column(name = "id") private String id;
    @Column(name = "payment_id") private String paymentId;
    @Column(name = "gateway") private String gateway;
    @Column(name = "external_event_id") private String externalEventId;
    @Column(name = "event_type") private String eventType;
    @Column(name = "status") private String status;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "attempts") private Integer attempts;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "processed_at") private String processedAt;
    @Column(name = "payload") private String payload;

    public String id() { return id; }
    public String paymentId() { return paymentId; }
    public String gateway() { return gateway; }
    public String externalEventId() { return externalEventId; }
    public String eventType() { return eventType; }
    public String status() { return status; }
    public String errorMessage() { return errorMessage; }
    public int attempts() { return attempts != null ? attempts : 0; }
    public String createdAt() { return createdAt; }
    public String processedAt() { return processedAt; }
    public String payload() { return payload; }
}
