package com.saas.catalog.controller;

import com.saas.catalog.dto.ServiceRouteResolutionDTO;
import com.saas.catalog.service.CatalogService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/services")
@Tag(name = "Module Catalog", description = "Consulta do catálogo de módulos e serviços da plataforma.")
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
public class ServiceRouteResource {

    @Inject
    CatalogService catalogService;

    @GET
    @Path("/resolve-route/{routeKey}")
    @Operation(
        summary = "Resolve um serviço do catálogo a partir da rota do frontend",
        description = "Operação exclusivamente de consulta ao catálogo (platform_modules / " +
            "platform_module_services). Dado o `routeKey` usado pelo frontend para uma tela, " +
            "retorna o serviço, o módulo ao qual pertence e a `permissionKey` correspondente " +
            "(formato `modulo.grupo.servico` ou `modulo.servico`, quando o serviço não " +
            "pertence a nenhum grupo). Não verifica se o tenant tem assinatura ativa nem se " +
            "o usuário possui a permissão — essas checagens ficam a cargo do " +
            "subscription-service e do profile-service, consultados separadamente pelo " +
            "frontend."
    )
    @APIResponse(responseCode = "200", description = "Serviço encontrado (`accessStatus=FOUND`) ou não encontrado (`accessStatus=NOT_FOUND`) — em ambos os casos a resposta é HTTP 200.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
    public Response resolveRoute(
            @Parameter(description = "Identificador de rota configurado no serviço do catálogo (ex.: chave usada nas rotas do frontend).", required = true)
            @PathParam("routeKey") String routeKey) {
        ServiceRouteResolutionDTO result = catalogService.resolveRoute(routeKey);
        return Response.ok(result).build();
    }
}
