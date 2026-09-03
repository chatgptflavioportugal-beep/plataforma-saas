package com.saas.platformtenant.exceptions;

/**
 * Classe base de todos os erros da biblioteca. Cada subclasse carrega o status HTTP
 * correspondente, usado tanto pelo AbstractTenantResolutionFilter/TenantContext (para
 * requisições abortadas antes do endpoint) quanto pelo TenantSecurityExceptionMapper.
 */
public abstract class TenantSecurityException extends RuntimeException {

    private final int status;

    protected TenantSecurityException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
