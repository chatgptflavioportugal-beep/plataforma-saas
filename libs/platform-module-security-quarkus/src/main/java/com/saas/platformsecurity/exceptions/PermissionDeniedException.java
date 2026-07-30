package com.saas.platformsecurity.exceptions;

/** Token válido, mas sem a permissão exigida pela rota. */
public class PermissionDeniedException extends ModuleSecurityException {
    public PermissionDeniedException(String message) {
        super(message, 403);
    }
}
