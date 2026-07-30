package com.saas.platformsecurity.exceptions;

/** Token válido, mas emitido para outro módulo (moduleSlug diferente do esperado). */
public class ModuleMismatchException extends ModuleSecurityException {
    public ModuleMismatchException(String message) {
        super(message, 403);
    }
}
