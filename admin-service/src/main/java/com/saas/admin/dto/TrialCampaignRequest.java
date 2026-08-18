package com.saas.admin.dto;

public record TrialCampaignRequest(
    String planVersionModuleId,
    String name,
    String status,
    Integer days,
    Integer maxSlots,
    String startDate,
    String endDate,
    String notes,
    Integer priority
) {}
