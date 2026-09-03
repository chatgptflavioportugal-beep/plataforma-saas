package com.saas.platformtenant.exceptions;

/** X-Tenant-ID informado, mas o usuário não tem vínculo ativo com esse tenant. */
public class TenantAccessDeniedException extends TenantSecurityException {
    public TenantAccessDeniedException(String message) {
        super(message, 401);
    }
}
