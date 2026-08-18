package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record TrialCampaignDetailDTO(
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
        String moduleSlug,
        String moduleIcon,
        String planName,
        String planCode,
        Integer planVersion,
        BigDecimal planMonthlyPrice,
        BigDecimal planAnnualPrice,
        Long totalParticipants,
        Double conversionPercent,
        Long participantsActive,
        Long participantsExpired,
        Long participantsCancelled) {
}
