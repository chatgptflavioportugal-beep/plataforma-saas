package com.saas.profile.dto.accesslevel;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ModuleTreeDto(
        String moduleId,
        String moduleName,
        String moduleSlug,
        String moduleIconPath,
        List<ServiceGroupDto> serviceGroups,
        List<ServiceDto> ungroupedServices
) {}
