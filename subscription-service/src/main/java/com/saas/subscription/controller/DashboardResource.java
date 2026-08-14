package com.saas.subscription.controller;

import com.saas.subscription.dto.response.DashboardModuleResponse;
import com.saas.subscription.security.TenantContext;
import com.saas.subscription.service.DashboardService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;

@Path("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Consulta agregada, orientada ao perfil ativo, do status de acesso do tenant a todos os módulos da plataforma e seus serviços.")
@Authenticated
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT emitido pelo Supabase Auth (login do usuário). Enviar como 'Authorization: Bearer <token>'."
)
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    DashboardService dashboardService;

    @GET
    @Path("/modules")
    @Operation(
        summary = "Lista todos os módulos da plataforma com o status de acesso do perfil ativo",
        description = "Operação exclusivamente de consulta. Tenant é resolvido via JWT do Supabase " +
            "+ header X-Tenant-ID (TenantResolutionFilter). Para cada módulo ativo calcula o " +
            "`accessStatus`: SUBSCRIBED (assinatura ativa dentro da validade), TRIAL (perfil em " +
            "período de Trial dentro da validade), EXPIRED (assinatura ou Trial já vencidos), " +
            "FREE (existe plano gratuito disponível e sem assinatura ativa) ou LOCKED (sem acesso " +
            "e sem plano gratuito — nesse caso ainda informa se há Trial disponível/encerrado via " +
            "TrialCampaignService). A lista de serviços de cada módulo só é retornada para " +
            "SUBSCRIBED, TRIAL e FREE; para usuários com role \"member\", os serviços de módulos " +
            "SUBSCRIBED são filtrados pelas permissões do nível de acesso do membro. Resultado " +
            "ordenado por accessStatus: SUBSCRIBED → TRIAL → EXPIRED → FREE → LOCKED."
    )
    @APIResponse(responseCode = "200", description = "Lista de módulos com status de acesso, badge, contagem de serviços e (quando aplicável) serviços habilitados para o perfil.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    public Response getDashboardModules(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        List<DashboardModuleResponse> result = dashboardService.listModulesWithAccessStatus(
            ctx.getTenantId(), ctx.getUserId(), ctx.getUserRole());
        return Response.ok(result).build();
    }
}
