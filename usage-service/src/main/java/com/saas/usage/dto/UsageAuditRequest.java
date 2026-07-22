package com.saas.usage.dto;

import java.util.Map;

public record UsageAuditRequest(
        String action,
        String metricCode,
        Map<String, Object> metadata
) {
}
