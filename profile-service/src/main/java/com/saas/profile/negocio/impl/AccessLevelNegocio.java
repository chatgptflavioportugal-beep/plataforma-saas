package com.saas.profile.negocio.impl;

import com.saas.profile.dto.accesslevel.AccessLevelDto;
import com.saas.profile.dto.accesslevel.AvailableModulesResponse;
import com.saas.profile.dto.request.AccessLevelRequest;

import java.util.List;
import java.util.UUID;

/**
 * Níveis de acesso: papéis customizados por tenant, com permissões granulares
 * por serviço de módulo assinado e por um conjunto fixo de permissões
 * administrativas do próprio profile-service.
 */
public interface AccessLevelNegocio {

    AvailableModulesResponse availableModules(UUID tenantId);

    List<AccessLevelDto> listAccessLevels(UUID tenantId);

    String createAccessLevel(UUID tenantId, AccessLevelRequest request);

    void updateAccessLevel(UUID tenantId, UUID alId, AccessLevelRequest request);

    String updateStatus(UUID tenantId, UUID alId, String status);

    void deleteAccessLevel(UUID tenantId, UUID alId);
}
