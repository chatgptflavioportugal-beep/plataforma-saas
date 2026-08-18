package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SubscriptionsSummaryDTO(
        long total,
        long active,
        long monthly,
        long annual,
        long canceled,
        long expired,
        long pendingPayment,
        long trial,
        long trialCancelled) {
}
