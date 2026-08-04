package com.saas.profile.controller;

import com.saas.profile.entity.Tenant;
import com.saas.profile.service.InvitationService;
import com.saas.profile.service.TenantService;
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
 * Endpoints públicos do domínio de perfil: onboarding (criação de tenant),
 * criação do tenant individual, e preview público de convite.
 *
 * A listagem de planos/módulos de billing (/api/v1/public/plans,
 * /api/v1/public/modules/billing-options) já migrou para o subscription-service.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject
    TenantService tenantService;

    @Inject
    InvitationService invitationService;

    @Inject
    JsonWebToken jwt;

    @Inject
    EntityManager em;

    private boolean isAdminUser(String userId) {
        try {
            String role = (String) em.createNativeQuery(
                "SELECT system_role FROM user_profiles WHERE id::text = :id"
            ).setParameter("id", userId).getSingleResult();
            return "SUPER_ADMIN".equals(role) || "ADMIN_USER".equals(role);
        } catch (NoResultException e) {
            return false;
        }
    }

    /**
     * Onboarding: cria um tenant do tipo BUSINESS.
     * Requer usuário autenticado — não exige tenant ativo (sem X-Tenant-ID).
     */
    @POST
    @Path("/onboarding")
    @Authenticated
    public Response onboarding(Map<String, String> body) {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (isAdminUser(userId.toString()))
            return Response.status(403).entity(Map.of("error", "Usuários administrativos não podem criar perfis cliente")).build();

        String name = body.get("name");
        String slug = body.get("slug");

        if (name == null || slug == null) {
            return Response.status(400).entity(Map.of("error", "name e slug obrigatórios")).build();
        }

        try {
            Tenant tenant = tenantService.createTenant(name, slug, userId, "business");
            return Response.ok(Map.of("id", tenant.id, "slug", tenant.slug, "type", "business")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(409).entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Cria (ou retorna) o tenant individual do usuário autenticado.
     * Idempotente — seguro chamar múltiplas vezes.
     * Requer usuário autenticado — não exige tenant ativo (sem X-Tenant-ID).
     */
    @POST
    @Path("/individual-tenant")
    @Authenticated
    public Response createIndividualTenant() {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (isAdminUser(userId.toString()))
            return Response.status(403).entity(Map.of("error", "Usuários administrativos não podem criar perfis cliente")).build();

        try {
            Map<String, Object> result = tenantService.ensureIndividualTenant(userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Preview público de um convite (sem autenticação).
     * Mostra nome da empresa, papel e status do convite.
     */
    @GET
    @Path("/invitations/{token}")
    public Response previewInvitation(@PathParam("token") String token) {
        try {
            return Response.ok(invitationService.getInvitationPreview(token)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", "Convite não encontrado")).build();
        }
    }
}
