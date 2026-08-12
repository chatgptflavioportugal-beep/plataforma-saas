package com.saas.usage;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Metadados gerais expostos em /q/openapi e /q/swagger-ui.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SaaS Platform — Usage Service",
        version = "1.0.0",
        description = "Serviço de controle de uso e quotas dos módulos contratados. " +
            "Incrementa contadores de métricas por tenant/módulo, verifica o limite " +
            "definido no plano (recebido diretamente das claims do ModuleAccessToken, sem " +
            "consultar o subscription-service) e registra eventos de auditoria de uso. " +
            "Todas as rotas exigem um ModuleAccessToken válido — não há autenticação por " +
            "JWT do Supabase nem operações administrativas neste serviço."
    )
)
public class OpenApiConfig {
}
