package com.saas.admin.negocio.impl;

import com.saas.admin.dto.AccessLevelDTO;
import com.saas.admin.dto.AccessLevelDetailDTO;
import com.saas.admin.dto.PermissionGroupDTO;

import java.util.List;
import java.util.Optional;

public interface AccessLevelNegocio {

    List<PermissionGroupDTO> getPermissionTree();

    List<String> getMyPermissionKeys(String userId);

    List<AccessLevelDTO> list(String status);

    Optional<AccessLevelDetailDTO> get(String id);

    long countActiveAdminUsers(String accessLevelId);

    AccessLevelDetailDTO create(String name, String description, List<Object> rawPermissionKeys, String actorUserId);

    Optional<List<String>> update(String id, String name, String description, List<Object> rawPermissionKeys, String actorUserId);

    boolean updateStatus(String id, String status, String actorUserId);
}
