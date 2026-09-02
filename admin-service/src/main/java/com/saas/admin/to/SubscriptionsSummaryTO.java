package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o resumo agregado de assinaturas de modulo. */
public class SubscriptionsSummaryTO {

    @Column(name = "total_count") private Long total;
    @Column(name = "active_count") private Long active;
    @Column(name = "monthly_count") private Long monthly;
    @Column(name = "annual_count") private Long annual;
    @Column(name = "canceled_count") private Long canceled;
    @Column(name = "expired_count") private Long expired;
    @Column(name = "pending_payment_count") private Long pendingPayment;
    @Column(name = "trial_count") private Long trial;
    @Column(name = "trial_cancelled_count") private Long trialCancelled;

    public long total() { return total != null ? total : 0L; }
    public long active() { return active != null ? active : 0L; }
    public long monthly() { return monthly != null ? monthly : 0L; }
    public long annual() { return annual != null ? annual : 0L; }
    public long canceled() { return canceled != null ? canceled : 0L; }
    public long expired() { return expired != null ? expired : 0L; }
    public long pendingPayment() { return pendingPayment != null ? pendingPayment : 0L; }
    public long trial() { return trial != null ? trial : 0L; }
    public long trialCancelled() { return trialCancelled != null ? trialCancelled : 0L; }
}
