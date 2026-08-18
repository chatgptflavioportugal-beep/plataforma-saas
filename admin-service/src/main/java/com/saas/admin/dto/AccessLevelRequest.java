package com.saas.admin.dto;

import java.util.List;

public record AccessLevelRequest(String name, String description, List<Object> permissionKeys) {
}
