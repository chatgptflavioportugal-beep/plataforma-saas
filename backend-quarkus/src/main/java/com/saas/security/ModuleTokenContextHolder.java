package com.saas.security;

import jakarta.enterprise.context.RequestScoped;

/**
 * Transporta o ModuleTokenContext validado pelo ModuleTokenFilter para os Resources.
 * Escopo de requisição: cada request tem sua própria instância.
 */
@RequestScoped
public class ModuleTokenContextHolder {

    private ModuleTokenContext context;

    public void set(ModuleTokenContext ctx) {
        this.context = ctx;
    }

    public ModuleTokenContext get() {
        if (context == null) {
            throw new IllegalStateException("ModuleTokenContext não disponível. Endpoint requer ModuleAccessToken.");
        }
        return context;
    }

    public boolean isAvailable() {
        return context != null;
    }
}
