package com.saas.admin.negocio;

import com.saas.admin.dao.FeatureFlagDAO;
import com.saas.admin.dto.FeatureFlagDTO;
import com.saas.admin.dto.FeatureFlagRequest;
import com.saas.admin.negocio.impl.AdminAuditNegocio;
import com.saas.admin.negocio.impl.FeatureFlagNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class FeatureFlagNegocioImpl implements FeatureFlagNegocio {

    @Inject
    FeatureFlagDAO dao;

    @Inject
    AdminAuditNegocio auditNegocio;

    @Override
    public List<FeatureFlagDTO> list() {
        return dao.findAll();
    }

    @Override
    public boolean existsByKey(String key) {
        return dao.existsByKey(key);
    }

    @Override
    @Transactional
    public UUID create(FeatureFlagRequest req, String userId) {
        UUID id = UUID.randomUUID();
        boolean isEnabled = Boolean.TRUE.equals(req.isEnabled());
        dao.insert(id, req.key(), req.name(), req.description(), isEnabled, userId);

        auditNegocio.log(userId, "feature_flag.created", "feature_flags", id.toString(),
            Map.of("key", req.key(), "isEnabled", isEnabled));

        return id;
    }

    @Override
    @Transactional
    public boolean update(String id, FeatureFlagRequest req, String userId) {
        int updated = dao.updateNameDescription(id, req.name(), req.description(), userId);
        if (updated == 0) return false;

        auditNegocio.log(userId, "feature_flag.updated", "feature_flags", id, Map.of());
        return true;
    }

    @Override
    @Transactional
    public boolean toggleStatus(String id, String userId) {
        int updated = dao.toggleStatus(id, userId);
        if (updated == 0) return false;

        auditNegocio.log(userId, "feature_flag.toggled", "feature_flags", id, Map.of());
        return true;
    }
}
