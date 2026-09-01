package com.saas.admin.negocio.impl;

import com.saas.admin.dto.FeatureFlagDTO;
import com.saas.admin.dto.FeatureFlagRequest;

import java.util.List;
import java.util.UUID;

public interface FeatureFlagNegocio {

    List<FeatureFlagDTO> list();

    boolean existsByKey(String key);

    UUID create(FeatureFlagRequest req, String userId);

    boolean update(String id, FeatureFlagRequest req, String userId);

    boolean toggleStatus(String id, String userId);
}
