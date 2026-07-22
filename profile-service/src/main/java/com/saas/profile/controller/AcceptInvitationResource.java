package com.saas.profile.controller;

import com.saas.profile.service.InvitationService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
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
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AcceptInvitationResource {

    @Inject
    InvitationService invitationService;

    @Inject
    JsonWebToken jwt;

    @Inject
    EntityManager em;

    @POST
    public Response accept(@PathParam("token") String token) {
        UUID userId = UUID.fromString(jwt.getSubject());

        try {
            String role = (String) em.createNativeQuery(
                "SELECT system_role FROM user_profiles WHERE id::text = :id"
            ).setParameter("id", userId.toString()).getSingleResult();
            if ("SUPER_ADMIN".equals(role) || "ADMIN_USER".equals(role)) {
                return Response.status(403)
                    .entity(Map.of("error", "Usuários administrativos não podem ser membros de empresas clientes"))
                    .build();
            }
        } catch (NoResultException ignored) {}

        String userEmail = jwt.getClaim("email");
        var result = invitationService.acceptInvitation(token, userId, userEmail);
        return Response.ok(result).build();
    }
}
