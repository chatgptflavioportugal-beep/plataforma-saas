package com.saas.profile.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code accessLevelId} chega em camelCase do frontend — ver AccessLevelRequest. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ChangeAccessLevelRequest(String accessLevelId) {}
