package com.saas.platformsecurity;

import jakarta.enterprise.context.RequestScoped;

/**
 * Transporta o ModuleContext validado pelo ModuleAccessFilter para o endpoint. Escopo de
 * requisição: cada request tem sua própria instância.
 *
 * <p>Uso no endpoint:
 * <pre>{@code
 * @Inject CurrentModuleContext current;
 *
 * @POST
 * @RequireModuleAccess(moduleSlug = "pdf", permission = "pdf-merge")
 * public Response merge(...) {
 *     ModuleContext auth = current.get();
 *     ...
 * }
 * }</pre>
 */
@RequestScoped
public class CurrentModuleContext {

    private ModuleContext context;

    public void set(ModuleContext context) {
        this.context = context;
    }

    public ModuleContext get() {
        if (context == null) {
            throw new IllegalStateException(
                    "ModuleContext não disponível. Endpoint requer @RequireModuleAccess.");
        }
        return context;
    }
}
