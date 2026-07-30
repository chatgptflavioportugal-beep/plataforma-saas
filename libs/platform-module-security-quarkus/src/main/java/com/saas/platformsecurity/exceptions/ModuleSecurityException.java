package com.saas.platformsecurity.exceptions;

/**
 * Classe base de todos os erros da biblioteca. Cada subclasse carrega o status HTTP
 * correspondente, usado tanto pelo ModuleAccessFilter (para requisições abortadas antes do
 * endpoint) quanto pelo ModuleSecurityExceptionMapper (para chamadas a
 * ModuleContext#requirePermission feitas de dentro da lógica de negócio do endpoint).
 */
public abstract class ModuleSecurityException extends RuntimeException {

    private final int status;

    protected ModuleSecurityException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
