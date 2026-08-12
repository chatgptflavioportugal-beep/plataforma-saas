package com.saas.subscription.controller;

import com.saas.subscription.repository.UserTenantRepository;
import com.saas.subscription.security.AdminAuthService;
import com.saas.subscription.service.AuditService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
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

    @Inject EntityManager em;
    @Inject AdminAuthService adminAuth;
    @Inject AuditService auditService;
    @Inject UserTenantRepository userTenantRepository;

    @POST
    @Path("/{id}/cancel")
    @Transactional
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

        Object[] row;
        try {
            row = (Object[]) em.createNativeQuery(
                "SELECT tenant_id::text, status FROM profile_module_subscriptions WHERE id::text = :id"
            ).setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            return Response.status(404).entity(Map.of("error", "Assinatura não encontrada ou já cancelada")).build();
        }

        String tenantId = (String) row[0];
        String status = (String) row[1];
        if (!"ACTIVE".equals(status) && !"TRIAL".equals(status))
            return Response.status(404).entity(Map.of("error", "Assinatura não encontrada ou já cancelada")).build();

        // TRIAL cancela para TRIAL_CANCELLED (acesso segue até expirar, só a renovação
        // é interrompida) — mesma semântica de ProfileModuleSubscriptionResource.cancel.
        String newStatus = "TRIAL".equals(status) ? "TRIAL_CANCELLED" : "CANCELED";
        em.createNativeQuery(
            "UPDATE profile_module_subscriptions SET status = :newStatus, canceled_at = NOW(), updated_at = NOW() " +
            "WHERE id::text = :id"
        ).setParameter("newStatus", newStatus).setParameter("id", id).executeUpdate();

        userTenantRepository.bumpVersionForTenant(UUID.fromString(tenantId));

        auditService.log(UUID.fromString(tenantId), UUID.fromString(adminAuth.currentUserId()),
            "subscription.admin_cancel", "profile_module_subscriptions", id, (String) null);

        return Response.ok(Map.of("success", true, "id", id, "status", newStatus)).build();
    }

    @POST
    @Path("/{id}/reactivate")
    @Transactional
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

        Object[] row;
        try {
            row = (Object[]) em.createNativeQuery(
                "SELECT tenant_id::text, status FROM profile_module_subscriptions " +
                "WHERE id::text = :id AND (expires_at IS NULL OR expires_at > NOW())"
            ).setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            return Response.status(404).entity(Map.of("error", "Assinatura não encontrada, não está cancelada ou já expirou")).build();
        }

        String tenantId = (String) row[0];
        String status = (String) row[1];
        if (!"CANCELED".equals(status) && !"TRIAL_CANCELLED".equals(status))
            return Response.status(404).entity(Map.of("error", "Assinatura não encontrada, não está cancelada ou já expirou")).build();

        String newStatus = "TRIAL_CANCELLED".equals(status) ? "TRIAL" : "ACTIVE";
        em.createNativeQuery(
            "UPDATE profile_module_subscriptions SET status = :newStatus, canceled_at = NULL, updated_at = NOW() " +
            "WHERE id::text = :id"
        ).setParameter("newStatus", newStatus).setParameter("id", id).executeUpdate();

        userTenantRepository.bumpVersionForTenant(UUID.fromString(tenantId));

        auditService.log(UUID.fromString(tenantId), UUID.fromString(adminAuth.currentUserId()),
            "subscription.admin_reactivate", "profile_module_subscriptions", id, (String) null);

        return Response.ok(Map.of("success", true, "id", id, "status", newStatus)).build();
    }
}
