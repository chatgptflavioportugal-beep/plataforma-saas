package com.saas.catalog;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Metadados gerais expostos em /q/openapi e /q/swagger-ui.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SaaS Platform — Module Catalog Service",
        version = "1.0.0",
        description = "Catálogo somente-leitura dos módulos e serviços disponíveis na " +
            "plataforma (platform_modules / platform_module_services / grupos de serviço). " +
            "Resolve rotas de front-end para o serviço de catálogo correspondente, sem " +
            "checar assinatura, plano ou permissão — essas checagens são de responsabilidade " +
            "do subscription-service e do profile-service. Não expõe operações de escrita; " +
            "a administração do catálogo (criação/edição de módulos e serviços) é feita " +
            "exclusivamente pelo admin-service."
    )
)
public class OpenApiConfig {
}
