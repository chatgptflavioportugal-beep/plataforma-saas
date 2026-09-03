package com.saas.subscription.resource;

import com.saas.subscription.dto.response.SubscriptionActionResponse;
import com.saas.platformadmin.PlatformAdminAuthService;
import com.saas.subscription.negocio.impl.AdminSubscriptionNegocio;
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

import java.util.UUID;

/**
 * Ações administrativas sobre o ciclo de vida de assinaturas — cancelar/
 * reativar em nome de um administrador da plataforma agindo sobre a
 * assinatura de qualquer tenant. Distinto de ProfileModuleSubscriptionResource
 * (tenant-scoped, acionado pelo próprio cliente sobre a própria assinatura).
 *
 * Movido de admin-service, que escrevia diretamente em
 * profile_module_subscriptions — tabela pertencente a este serviço.
 * frontend-admin não pode consumir subscription-service diretamente — chama
 * admin-service, que repassa para cá via SubscriptionServiceClient (ver
 * com.saas.admin.controller.AdminSubscriptionResource); a checagem de
 * permissão administrativa é feita localmente por AdminAuthService com o
 * mesmo Authorization repassado pelo admin-service.
 */
@Path("/api/v1/admin/subscriptions")
@Tag(name = "Admin Subscriptions", description = "Ações administrativas sobre o ciclo de vida de assinaturas de módulo, executadas por um administrador da plataforma (SUPER_ADMIN/ADMIN_USER) em nome de qualquer tenant. Distinto de /api/v1/subscriptions (ProfileModuleSubscriptionResource), que é acionado pelo próprio cliente sobre a própria assinatura.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminSubscriptionResource {

    @Inject
    PlatformAdminAuthService adminAuth;

    @Inject
    AdminSubscriptionNegocio adminSubscriptionNegocio;

    @POST
    @Path("/{id}/cancel")
    @Operation(
        summary = "Cancela (como administrador) a assinatura de um módulo de qualquer tenant",
        description = "Exige a permissão administrativa `admin.subscriptions.cancel` (SUPER_ADMIN " +
            "sempre passa; ADMIN_USER precisa da permissão no seu nível de acesso administrativo). " +
            "Assinaturas em TRIAL viram TRIAL_CANCELLED (acesso segue liberado até expires_at, " +
            "apenas a renovação é interrompida); assinaturas ACTIVE viram CANCELED. Registra evento " +
            "de auditoria (subscription.admin_cancel) e invalida (bump de versão) o PAT/MAT em " +
            "cache de todos os membros do tenant afetado."
    )
    @APIResponse(responseCode = "200", description = "Assinatura cancelada com sucesso; retorna o novo status (CANCELED ou TRIAL_CANCELLED).")
    @APIResponse(responseCode = "401", description = "JWT ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.subscriptions.cancel`.")
    @APIResponse(responseCode = "404", description = "Assinatura não encontrada para o `id` informado, ou já está em um status diferente de ACTIVE/TRIAL (já cancelada).")
    public Response cancel(
        @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a cancelar.", required = true)
        @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.subscriptions.cancel");
        var result = adminSubscriptionNegocio.cancel(id, UUID.fromString(adminAuth.currentUserId()));
        return Response.ok(new SubscriptionActionResponse(true, id, result.status())).build();
    }

    @POST
    @Path("/{id}/reactivate")
    @Operation(
        summary = "Reativa (como administrador) uma assinatura de módulo cancelada de qualquer tenant",
        description = "Exige a permissão administrativa `admin.subscriptions.reactivate` (SUPER_ADMIN " +
            "sempre passa; ADMIN_USER precisa da permissão no seu nível de acesso administrativo). " +
            "Só reativa assinaturas ainda dentro da validade (`expires_at` nulo ou futuro): " +
            "CANCELED volta para ACTIVE, TRIAL_CANCELLED volta para TRIAL. Registra evento de " +
            "auditoria (subscription.admin_reactivate) e invalida (bump de versão) o PAT/MAT em " +
            "cache de todos os membros do tenant afetado."
    )
    @APIResponse(responseCode = "200", description = "Assinatura reativada com sucesso; retorna o novo status (ACTIVE ou TRIAL).")
    @APIResponse(responseCode = "401", description = "JWT ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.subscriptions.reactivate`.")
    @APIResponse(responseCode = "404", description = "Assinatura não encontrada para o `id` informado, não está em status CANCELED/TRIAL_CANCELLED, ou já expirou.")
    public Response reactivate(
        @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a reativar.", required = true)
        @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.subscriptions.reactivate");
        var result = adminSubscriptionNegocio.reactivate(id, UUID.fromString(adminAuth.currentUserId()));
        return Response.ok(new SubscriptionActionResponse(true, id, result.status())).build();
    }
}
