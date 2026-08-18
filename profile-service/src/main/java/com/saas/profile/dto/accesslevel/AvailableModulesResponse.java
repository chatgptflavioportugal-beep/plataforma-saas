package com.saas.profile.dto.accesslevel;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AvailableModulesResponse(List<ModuleTreeDto> modules, List<AdminPermissionGroupDto> adminPermissions) {}
