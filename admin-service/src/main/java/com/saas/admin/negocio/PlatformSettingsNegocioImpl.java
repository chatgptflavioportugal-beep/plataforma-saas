package com.saas.admin.negocio;

import com.saas.admin.dao.PlatformSettingsDAO;
import com.saas.admin.dto.PlatformSettingDTO;
import com.saas.admin.negocio.impl.PlatformSettingsNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class PlatformSettingsNegocioImpl implements PlatformSettingsNegocio {

    @Inject
    PlatformSettingsDAO dao;

    @Override
    public List<PlatformSettingDTO> list() {
        return dao.findAll();
    }

    @Override
    @Transactional
    public String updateValue(String key, String rawValue, String userId) {
        if (rawValue == null || rawValue.isBlank())
            throw new BadRequestException("value é obrigatório");

        String value = rawValue.trim();

        if ("trial_reuse_cooldown_days".equals(key)) {
            try {
                int days = Integer.parseInt(value);
                if (days < 0) throw new BadRequestException("trial_reuse_cooldown_days não pode ser negativo");
            } catch (NumberFormatException e) {
                throw new BadRequestException("trial_reuse_cooldown_days deve ser um número inteiro");
            }
        }

        int updated = dao.updateValue(key, value, userId);
        if (updated == 0) throw new NotFoundException("Configuração não encontrada: " + key);
        return value;
    }
}
