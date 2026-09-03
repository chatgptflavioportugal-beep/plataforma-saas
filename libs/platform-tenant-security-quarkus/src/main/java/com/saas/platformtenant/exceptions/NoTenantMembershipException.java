package com.saas.platformtenant.exceptions;

/** Sem X-Tenant-ID e o usuário não tem nenhum tenant vinculado (nenhum tenant default). */
public class NoTenantMembershipException extends TenantSecurityException {
    public NoTenantMembershipException(String message) {
        super(message, 401);
    }
}
