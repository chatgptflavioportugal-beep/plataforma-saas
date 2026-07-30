package com.saas.platformsecurity;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplica validação de ModuleAccessToken a um endpoint (ou a todos os endpoints de um recurso,
 * se usada na classe). Equivalente ao {@code Depends(module_security(...))} da biblioteca
 * Python.
 *
 * <pre>{@code
 * @POST
 * @Path("/merge")
 * @RequireModuleAccess(moduleSlug = "pdf", permission = "pdf-merge")
 * public Response merge(...) { ... }
 * }</pre>
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireModuleAccess {

    /** Slug do módulo ao qual o token precisa pertencer (ex.: "pdf", "whatsapp"). */
    String moduleSlug();

    /** Permissão exigida do token; deixe vazio para exigir apenas um token válido do módulo. */
    String permission() default "";
}
