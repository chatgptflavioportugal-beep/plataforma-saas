package com.saas.admin.service;

import com.saas.admin.dao.FeatureFlagDAO;
import com.saas.admin.dto.FeatureFlagDTO;
import com.saas.admin.dto.FeatureFlagRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class FeatureFlagService {

    @Inject
    FeatureFlagDAO dao;

    @Inject
    AdminAuditService auditService;

    public List<FeatureFlagDTO> list() {
        return dao.findAll();
    }

    public boolean existsByKey(String key) {
        return dao.existsByKey(key);
    }

    @Transactional
    public UUID create(FeatureFlagRequest req, String userId) {
        UUID id = UUID.randomUUID();
        boolean isEnabled = Boolean.TRUE.equals(req.isEnabled());
        dao.insert(id, req.key(), req.name(), req.description(), isEnabled, userId);

        auditService.log(userId, "feature_flag.created", "feature_flags", id.toString(),
            Map.of("key", req.key(), "isEnabled", isEnabled));

        return id;
    }

    @Transactional
    public boolean update(String id, FeatureFlagRequest req, String userId) {
        int updated = dao.updateNameDescription(id, req.name(), req.description(), userId);
        if (updated == 0) return false;

        auditService.log(userId, "feature_flag.updated", "feature_flags", id, Map.of());
        return true;
    }

    @Transactional
    public boolean toggleStatus(String id, String userId) {
        int updated = dao.toggleStatus(id, userId);
        if (updated == 0) return false;

        auditService.log(userId, "feature_flag.toggled", "feature_flags", id, Map.of());
        return true;
    }
}
