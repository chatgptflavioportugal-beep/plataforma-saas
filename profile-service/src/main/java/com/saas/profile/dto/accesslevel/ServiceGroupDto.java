package com.saas.profile.dto.accesslevel;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ServiceGroupDto(
        String groupId,
        String groupName,
        String groupDescription,
        String groupIconPath,
        Integer sortOrder,
        List<ServiceDto> services
) {}
