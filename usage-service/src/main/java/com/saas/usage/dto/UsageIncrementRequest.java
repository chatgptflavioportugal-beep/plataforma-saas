package com.saas.usage.dto;

public record UsageIncrementRequest(
        String metricCode,
        Long amount
) {
    public long amountOrDefault() {
        return amount != null && amount > 0 ? amount : 1L;
    }
}
