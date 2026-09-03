package com.saas.platformtenant.exceptions;

/** X-Tenant-ID presente, porém não é um UUID válido. */
public class InvalidTenantIdException extends TenantSecurityException {
    public InvalidTenantIdException(String message) {
        super(message, 401);
    }
}
