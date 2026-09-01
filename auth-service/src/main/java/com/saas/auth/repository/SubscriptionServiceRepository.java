package com.saas.auth.repository;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Cliente REST para a resolução de acesso a módulo (assinatura/plano/limites)
 * do subscription-service — plans e assinaturas são domínio daquele serviço,
 * não de auth-service (ver ModuleTokenNegocio). Repassa o mesmo Authorization
 * (JWT Supabase) e X-Tenant-ID recebidos do frontend; subscription-service
 * resolve o tenant sozinho via seu próprio TenantResolutionFilter.
 *
 * Integração externa real (outro microsserviço) — por isso mantém o sufixo
 * Repository mesmo fora do padrão Resource → negocio → DAO → Banco.
 */
@RegisterRestClient(configKey = "subscription-service-api")
public interface SubscriptionServiceRepository {

    @GET
    @Path("/api/v1/internal/module-access/{moduleSlug}")
    ModuleAccessResolution resolveModuleAccess(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                                                @HeaderParam("X-Tenant-ID") String tenantId,
                                                @PathParam("moduleSlug") String moduleSlug);
}
