package com.saas.admin;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Metadados gerais expostos em /q/openapi e /q/swagger-ui.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SaaS Platform — Admin Service",
        version = "1.0.0",
        description = "Serviço administrativo da plataforma. Concentra toda a operação " +
            "interna: gestão de tenants e clientes, administradores do sistema e seus " +
            "níveis de acesso administrativo, catálogo de módulos/serviços/grupos de " +
            "serviço, planos e suas versões/limites, campanhas de free trial, visão " +
            "administrativa de assinaturas e configurações globais da plataforma. Todas as " +
            "rotas deste serviço pertencem exclusivamente ao contexto administrativo e não " +
            "devem ser utilizadas pelo ambiente cliente — a criação/edição/exclusão de " +
            "planos, módulos, limites e demais configurações da plataforma é responsabilidade " +
            "exclusiva do admin-service."
    )
)
public class OpenApiConfig {
}
