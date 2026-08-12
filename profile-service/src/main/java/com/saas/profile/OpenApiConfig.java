package com.saas.profile;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Metadados gerais expostos em /q/openapi e /q/swagger-ui.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SaaS Platform — Profile Service",
        version = "1.0.0",
        description = "Serviço responsável pelo domínio de perfil/tenant do cliente: " +
            "onboarding de novas contas (empresa ou individual), convites e gerenciamento " +
            "de membros do tenant, níveis de acesso personalizados com permissões " +
            "granulares por módulo/serviço, e consulta dos dados do tenant ativo do usuário " +
            "autenticado. A autenticação é feita via JWT do Supabase, com o tenant ativo " +
            "resolvido pelo header X-Tenant-ID (TenantResolutionFilter); um pequeno conjunto " +
            "de rotas de onboarding e de preview de convite é público. Não trata assinaturas " +
            "de módulo (subscription-service) nem operações administrativas da plataforma " +
            "(admin-service)."
    )
)
public class OpenApiConfig {
}
