package com.saas.resource;

import com.saas.security.TenantContext;
import com.saas.service.InvitationService;
import com.saas.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
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

    // ─── Contexto do tenant ───────────────────────────────────────────────────

    @GET
    @Path("/context")
    public Response tenantContext(@PathParam("tenantId") UUID tenantId,
                                  @Context SecurityContext ctx) {
        TenantContext tenantCtx = TenantContext.from(ctx);
        if (!tenantCtx.getTenantId().equals(tenantId)) {
            return Response.status(403).build();
        }
        return Response.ok(tenantService.getTenantContext(tenantId)).build();
    }

    // ─── Membros ──────────────────────────────────────────────────────────────

    @GET
    @Path("/members")
    public Response listMembers(@PathParam("tenantId") UUID tenantId,
                                @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        return Response.ok(invitationService.listMembers(tenantId)).build();
    }

    @DELETE
    @Path("/members/{userId}")
    public Response removeMember(@PathParam("tenantId") UUID tenantId,
                                 @PathParam("userId") UUID targetUserId,
                                 @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);
        invitationService.removeMember(tenantId, targetUserId, tc.getUserId(), tc.getUserRole());
        return Response.noContent().build();
    }

    // ─── Convites ─────────────────────────────────────────────────────────────

    @GET
    @Path("/invitations")
    public Response listInvitations(@PathParam("tenantId") UUID tenantId,
                                    @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);
        return Response.ok(invitationService.listInvitations(tenantId)).build();
    }

    @POST
    @Path("/invitations")
    public Response sendInvitation(@PathParam("tenantId") UUID tenantId,
                                   Map<String, String> body,
                                   @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);

        String email = body.get("email");
        String role = body.getOrDefault("role", "member");

        if (email == null || email.isBlank()) {
            return Response.status(400).entity(Map.of("error", "E-mail é obrigatório")).build();
        }

        var result = invitationService.sendInvitation(tenantId, email, role, tc.getUserId());
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/invitations/{invId}")
    public Response cancelInvitation(@PathParam("tenantId") UUID tenantId,
                                     @PathParam("invId") UUID invId,
                                     @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);
        invitationService.cancelInvitation(tenantId, invId);
        return Response.noContent().build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private TenantContext resolveAndCheckAccess(SecurityContext ctx, UUID tenantId) {
        TenantContext tc = TenantContext.from(ctx);
        if (!tc.getTenantId().equals(tenantId)) {
            throw new ForbiddenException("Acesso negado ao tenant");
        }
        return tc;
    }

    private void ensureOwnerOrAdmin(TenantContext tc) {
        if (!List.of("owner", "admin").contains(tc.getUserRole())) {
            throw new ForbiddenException("Apenas proprietários e administradores podem gerenciar membros e convites");
        }
    }
}
