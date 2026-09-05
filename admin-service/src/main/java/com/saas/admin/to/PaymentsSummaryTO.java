package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;

/** TO da camada de dados para os indicadores agregados de pagamentos. */
public class PaymentsSummaryTO {

    @Column(name = "total_count") private Long totalCount;
    @Column(name = "paid_count") private Long paidCount;
    @Column(name = "pending_count") private Long pendingCount;
    @Column(name = "failed_count") private Long failedCount;
    @Column(name = "overdue_count") private Long overdueCount;
    @Column(name = "amount_received") private BigDecimal amountReceived;
    @Column(name = "amount_pending") private BigDecimal amountPending;
    @Column(name = "with_webhook_error_count") private Long withWebhookErrorCount;

    public long totalCount() { return totalCount != null ? totalCount : 0L; }
    public long paidCount() { return paidCount != null ? paidCount : 0L; }
    public long pendingCount() { return pendingCount != null ? pendingCount : 0L; }
    public long failedCount() { return failedCount != null ? failedCount : 0L; }
    public long overdueCount() { return overdueCount != null ? overdueCount : 0L; }
    public BigDecimal amountReceived() { return amountReceived != null ? amountReceived : BigDecimal.ZERO; }
    public BigDecimal amountPending() { return amountPending != null ? amountPending : BigDecimal.ZERO; }
    public long withWebhookErrorCount() { return withWebhookErrorCount != null ? withWebhookErrorCount : 0L; }
}
