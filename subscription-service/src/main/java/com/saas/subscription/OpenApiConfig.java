package com.saas.subscription;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Metadados gerais expostos em /q/openapi e /q/swagger-ui.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SaaS Platform — Subscription Service",
        version = "1.0.0",
        description = "Serviço responsável pelas assinaturas de módulo do tenant: consulta " +
            "de planos e opções de contratação disponíveis (rotas públicas), contratação, " +
            "ativação de plano gratuito, cancelamento e reativação de assinaturas de módulo, " +
            "histórico e elegibilidade de free trial, resolução de acesso a um módulo " +
            "(consumida pelo auth-service ao emitir o ModuleAccessToken) e dados agregados " +
            "para o dashboard do cliente. Também expõe um pequeno conjunto de rotas " +
            "administrativas (cancelamento/reativação forçados), usadas exclusivamente como " +
            "destino de um proxy vindo do admin-service — não devem ser chamadas diretamente " +
            "pelo frontend cliente. A definição de planos, módulos e limites em si é " +
            "responsabilidade exclusiva do admin-service; este serviço apenas consulta e " +
            "aplica essas definições no contexto do tenant."
    )
)
public class OpenApiConfig {
}
