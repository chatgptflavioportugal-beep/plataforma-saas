package com.saas.usage.dto;

public record UsageIncrementResponse(
        boolean allowed,
        long count,
        Long limit,
        Long remaining
) {
}
