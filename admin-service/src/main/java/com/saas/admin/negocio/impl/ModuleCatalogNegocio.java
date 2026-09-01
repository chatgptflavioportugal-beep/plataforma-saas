package com.saas.admin.negocio.impl;

import com.saas.admin.dto.ModuleRequest;
import com.saas.admin.dto.ModuleServiceGroupRequest;
import com.saas.admin.dto.ModuleServiceRequest;
import com.saas.admin.dto.PlatformModuleDTO;
import com.saas.admin.dto.PlatformModuleServiceDTO;
import com.saas.admin.dto.PlatformModuleServiceGroupDTO;

import java.util.List;
import java.util.Map;

/**
 * CRUD administrativo do catálogo de módulos/serviços/grupos de serviços.
 * Persistência isolada em ModuleCatalogDAO.
 */
public interface ModuleCatalogNegocio {

    /**
     * Resultado genérico para operações com múltiplas ramificações de erro
     * (400/404/409), preservando o corpo `{"error": ...}` exato do endpoint
     * original em vez de depender do GenericExceptionMapper.
     */
    record OpResult(int status, Object body) {
        public static OpResult ok(int status, Object body) {
            return new OpResult(status, body);
        }
        public static OpResult error(int status, String message) {
            return new OpResult(status, Map.of("error", message));
        }
    }

    boolean isValidSlug(String slug);

    // ─── Módulos ───────────────────────────────────────────────────────────

    List<PlatformModuleDTO> listModules(String search, Boolean isActive);

    OpResult createModule(ModuleRequest req);

    OpResult updateModule(String id, ModuleRequest req);

    boolean toggleModuleStatus(String id);

    // ─── Serviços ──────────────────────────────────────────────────────────

    List<PlatformModuleServiceDTO> listServices(String moduleId);

    OpResult createService(String moduleId, ModuleServiceRequest req);

    OpResult updateService(String moduleId, String id, ModuleServiceRequest req);

    boolean toggleServiceStatus(String moduleId, String id);

    // ─── Grupos de serviços ────────────────────────────────────────────────

    List<PlatformModuleServiceGroupDTO> listServiceGroups(String moduleId);

    OpResult createServiceGroup(String moduleId, ModuleServiceGroupRequest req);

    OpResult updateServiceGroup(String moduleId, String id, ModuleServiceGroupRequest req);

    OpResult updateServiceGroupStatus(String moduleId, String id, String newStatus);
}
