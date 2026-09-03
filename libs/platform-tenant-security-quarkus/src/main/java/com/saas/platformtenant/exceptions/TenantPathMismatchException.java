package com.saas.platformtenant.exceptions;

/** O {tenantId} do path não bate com o tenant resolvido para a requisição. */
public class TenantPathMismatchException extends TenantSecurityException {
    public TenantPathMismatchException(String message) {
        super(message, 403);
    }
}
