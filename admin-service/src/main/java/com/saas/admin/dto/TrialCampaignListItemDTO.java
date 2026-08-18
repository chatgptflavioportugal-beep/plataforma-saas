package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record TrialCampaignListItemDTO(
        String id,
        String planVersionModuleId,
        String moduleId,
        String moduleName,
        String name,
        String status,
        Integer days,
        Integer maxSlots,
        Integer usedSlots,
        String startDate,
        String endDate,
        String notes,
        Integer priority,
        String createdAt,
        String updatedAt,
        String createdByUserId,
        String createdByName,
        String updatedByUserId,
        String updatedByName,
        Boolean expired,
        String planName,
        String planCode,
        Integer planVersion) {
}
