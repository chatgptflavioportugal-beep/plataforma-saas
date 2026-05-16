package com.saas.resource;

import com.saas.service.InvitationService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * Aceitar convite por token.
 * Requer apenas autenticação (JWT), sem X-Tenant-ID.
 * O TenantResolutionFilter é bypassado para este path.
 */
@Path("/api/v1/invitations/{token}/accept")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AcceptInvitationResource {

    @Inject
    InvitationService invitationService;

    @Inject
    JsonWebToken jwt;

    @POST
    public Response accept(@PathParam("token") String token) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var result = invitationService.acceptInvitation(token, userId);
        return Response.ok(result).build();
    }
}
