package com.saas.resource;

import com.saas.security.TenantContext;
import com.saas.service.InvitationService;
import com.saas.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/tenants/{tenantId}")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvitationResource {

    @Inject
    InvitationService invitationService;

    @Inject
    TenantService tenantService;

    @Inject
    EntityManager em;

    // ─── Perfil do tenant ────────────────────────────────────────────────────

    @GET
    @Path("/profile")
    public Response tenantProfile(@PathParam("tenantId") UUID tenantId,
                                  @Context SecurityContext ctx) {
        TenantContext tenantCtx = TenantContext.from(ctx);
        if (!tenantCtx.getTenantId().equals(tenantId)) {
            return Response.status(403).build();
        }
        return Response.ok(tenantService.getTenantProfile(tenantId)).build();
    }

    // ─── Membros ──────────────────────────────────────────────────────────────

    @GET
    @Path("/members")
    public Response listMembers(@PathParam("tenantId") UUID tenantId,
                                @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.view");
        return Response.ok(invitationService.listMembers(tenantId)).build();
    }

    @DELETE
    @Path("/members/{userId}")
    public Response removeMember(@PathParam("tenantId") UUID tenantId,
                                 @PathParam("userId") UUID targetUserId,
                                 @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.remove");
        invitationService.removeMember(tenantId, targetUserId, tc.getUserId(), tc.getUserRole());
        return Response.noContent().build();
    }

    @PATCH
    @Path("/members/{userId}/access-level")
    public Response changeMemberAccessLevel(@PathParam("tenantId") UUID tenantId,
                                            @PathParam("userId") UUID targetUserId,
                                            Map<String, String> body,
                                            @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.change_access_level");

        String accessLevelId = body != null ? body.get("accessLevelId") : null;
        if (accessLevelId == null || accessLevelId.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nível de acesso é obrigatório")).build();
        }

        invitationService.changeMemberAccessLevel(tenantId, targetUserId, accessLevelId);
        return Response.ok(Map.of("success", true)).build();
    }

    // ─── Convites ─────────────────────────────────────────────────────────────

    @GET
    @Path("/invitations")
    public Response listInvitations(@PathParam("tenantId") UUID tenantId,
                                    @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "invites.view");
        return Response.ok(invitationService.listInvitations(tenantId)).build();
    }

    @POST
    @Path("/invitations")
    public Response sendInvitation(@PathParam("tenantId") UUID tenantId,
                                   Map<String, String> body,
                                   @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.invite");

        String email = body.get("email");
        String accessLevelId = body.get("accessLevelId");

        if (email == null || email.isBlank()) {
            return Response.status(400).entity(Map.of("error", "E-mail é obrigatório")).build();
        }
        if (accessLevelId == null || accessLevelId.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nível de acesso é obrigatório")).build();
        }

        var result = invitationService.sendInvitation(tenantId, email, accessLevelId, tc.getUserId());
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/invitations/{invId}")
    public Response cancelInvitation(@PathParam("tenantId") UUID tenantId,
                                     @PathParam("invId") UUID invId,
                                     @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "invites.cancel");
        invitationService.cancelInvitation(tenantId, invId);
        return Response.noContent().build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasAdminPerm(TenantContext tc, UUID tenantId, String permKey) {
        if (List.of("owner", "admin").contains(tc.getUserRole())) return true;
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM profile_access_level_admin_permissions ap " +
            "JOIN user_tenants ut ON ut.access_level_id = ap.access_level_id " +
            "WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId " +
            "  AND ut.is_active = TRUE AND ap.permission_key = :permKey"
        )
        .setParameter("userId", tc.getUserId())
        .setParameter("tenantId", tenantId)
        .setParameter("permKey", permKey)
        .getSingleResult()).longValue();
        return count > 0;
    }

    private void requireAdminPerm(TenantContext tc, UUID tenantId, String permKey) {
        if (!hasAdminPerm(tc, tenantId, permKey)) {
            throw new ForbiddenException("Permissão necessária: " + permKey);
        }
    }

    private TenantContext resolveAndCheckAccess(SecurityContext ctx, UUID tenantId) {
        TenantContext tc = TenantContext.from(ctx);
        if (!tc.getTenantId().equals(tenantId)) {
            throw new ForbiddenException("Acesso negado ao tenant");
        }
        return tc;
    }
}
