package com.saas.profile.resource;

import com.saas.profile.negocio.impl.AdminAccessNegocio;
import com.saas.profile.negocio.impl.InvitationNegocio;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.UUID;

/**
 * Aceitar convite por token.
 * Requer apenas autenticação (JWT), sem X-Tenant-ID.
 * O TenantResolutionFilter é bypassado para este path.
 */
@Path("/api/v1/invitations/{token}/accept")
@Tag(name = "Invitations", description = "Aceitação de convites enviados por e-mail a novos membros de uma empresa.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
public class AcceptInvitationResource {

    @Inject
    InvitationNegocio invitationService;

    @Inject
    AdminAccessNegocio adminAccessService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Operation(
        summary = "Aceita um convite de membro pelo token recebido por e-mail",
        description = "Vincula o usuário autenticado ao tenant do convite, criando o registro " +
            "em `user_tenants` com o nível de acesso definido pelo convite. Requer apenas o " +
            "JWT do Supabase (login) — não exige o header X-Tenant-ID, já que o usuário ainda " +
            "não tem um tenant ativo selecionado; este path é explicitamente excluído da " +
            "resolução de tenant feita pelo TenantResolutionFilter. Regras aplicadas: (1) " +
            "usuários com system_role SUPER_ADMIN ou ADMIN_USER (perfil administrativo) não " +
            "podem aceitar convites de empresas clientes; (2) o e-mail do convite precisa " +
            "corresponder exatamente ao e-mail do usuário autenticado (claim `email` do JWT); " +
            "(3) o convite precisa existir, estar com status `pending` e não ter expirado. A " +
            "vinculação em si é feita pela função de banco `accept_invitation`, que também " +
            "encerra outros convites pendentes do mesmo e-mail para o tenant, se houver."
    )
    @APIResponse(responseCode = "200", description = "Convite aceito; retorna o `tenant_id` do convite e o `role` atribuído ao usuário.")
    @APIResponse(responseCode = "400", description = "Convite inválido, expirado ou já utilizado, ou a função de banco recusou a aceitação.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado é administrativo (SUPER_ADMIN/ADMIN_USER) e não pode ser membro de empresa cliente, ou o e-mail do usuário autenticado (`user_email`) diverge do e-mail para o qual o convite (`invitation_email`) foi emitido (`error=wrong_email`).")
    @APIResponse(responseCode = "404", description = "Nenhum convite corresponde ao `token` informado.")
    public Response accept(
            @Parameter(description = "Token único do convite, recebido no link enviado por e-mail.", required = true)
            @PathParam("token") String token) {
        UUID userId = UUID.fromString(jwt.getSubject());

        if (adminAccessService.isPlatformAdmin(userId)) {
            return Response.status(403)
                    .entity(Map.of("error", "Usuários administrativos não podem ser membros de empresas clientes"))
                    .build();
        }

        String userEmail = jwt.getClaim("email");
        var result = invitationService.acceptInvitation(token, userId, userEmail);
        return Response.ok(result).build();
    }
}
