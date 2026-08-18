package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record TrialCampaignParticipantDTO(
        String tenantId,
        String tenantName,
        String tenantType,
        String userName,
        String userEmail,
        String startedAt,
        String finishedAt,
        String canceledAt,
        String status,
        Boolean becameCustomer) {
}
