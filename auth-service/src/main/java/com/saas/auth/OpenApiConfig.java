package com.saas.auth;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Metadados gerais expostos em /q/openapi e /q/swagger-ui.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SaaS Platform — Auth Service",
        version = "1.0.0",
        description = "Serviço responsável pela emissão dos tokens internos da plataforma. " +
            "Não realiza o login do usuário — isso é feito pelo Supabase Auth, fora deste " +
            "serviço. A partir de um JWT do Supabase já autenticado (mais o header " +
            "X-Tenant-ID, resolvido pelo TenantResolutionFilter), este serviço emite o " +
            "ProfileAccessToken (PAT), usado pelo frontend para operações administrativas " +
            "no contexto do perfil/tenant selecionado, e o ModuleAccessToken (MAT), usado " +
            "para autorizar chamadas dentro de um módulo específico contratado pelo tenant " +
            "(a checagem de assinatura/plano do módulo é delegada ao subscription-service)."
    )
)
public class OpenApiConfig {
}
