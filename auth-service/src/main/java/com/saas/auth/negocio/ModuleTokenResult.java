package com.saas.auth.negocio;

import com.saas.auth.dto.ModuleTokenResponse;

/**
 * Resultado da emissão do ModuleAccessToken. Quem decide o status HTTP a
 * partir daqui é o Resource (ModuleTokenResource) — a negócio só descreve
 * o que aconteceu.
 */
public sealed interface ModuleTokenResult {

    record Issued(ModuleTokenResponse response) implements ModuleTokenResult {}

    record NotFound(String moduleSlug) implements ModuleTokenResult {}

    record Expired(String moduleSlug) implements ModuleTokenResult {}

    record FreePlanNotActivated(String moduleSlug, String moduleId, String planVersionId) implements ModuleTokenResult {}

    record NoAccess(String moduleSlug) implements ModuleTokenResult {}
}
