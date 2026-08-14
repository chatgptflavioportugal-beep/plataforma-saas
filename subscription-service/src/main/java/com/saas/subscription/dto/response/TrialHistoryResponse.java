package com.saas.subscription.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record TrialHistoryResponse(
    UUID moduleId,
    UUID trialCampaignId,
    OffsetDateTime trialStartedAt,
    OffsetDateTime trialFinishedAt,
    OffsetDateTime trialCanceledAt,
    boolean becameCustomer
) {}
