package com.saas.resource;

import com.saas.security.TenantContext;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/subscriptions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileModuleSubscriptionResource {

    @Inject
    EntityManager em;

    public record ModuleSubscriptionItem(
        String moduleId,
        String planVersionId,
        String billingCycle
    ) {}

    public record ConfirmSubscriptionRequest(
        List<ModuleSubscriptionItem> modules
    ) {}

    /**
     * Confirma a assinatura de módulos para o perfil ativo (tenant resolvido via X-Tenant-ID).
     * Valida que o usuário tem permissão de contratação no perfil.
     * Valida que cada combinação módulo/plano existe e está ativa.
     * Faz upsert para permitir troca de plano ou ciclo de cobrança.
     */
    @POST
    @Path("/modules")
    @Transactional
    public Response confirmModuleSubscriptions(
        ConfirmSubscriptionRequest request,
        @Context SecurityContext secCtx
    ) {
        var ctx = TenantContext.from(secCtx);

        if (!List.of("owner", "admin", "finance").contains(ctx.getUserRole())) {
            return Response.status(403)
                .entity(Map.of("error", "Sem permissão para contratar módulos neste perfil"))
                .build();
        }

        if (request == null || request.modules() == null || request.modules().isEmpty()) {
            return Response.status(400)
                .entity(Map.of("error", "Nenhum módulo selecionado"))
                .build();
        }

        UUID tenantId = ctx.getTenantId();
        UUID userId   = ctx.getUserId();

        for (var item : request.modules()) {
            if (item.moduleId() == null || item.planVersionId() == null || item.billingCycle() == null) {
                return Response.status(400)
                    .entity(Map.of("error", "moduleId, planVersionId e billingCycle são obrigatórios"))
                    .build();
            }

            String billingCycle = item.billingCycle().toUpperCase();
            if (!billingCycle.equals("MONTHLY") && !billingCycle.equals("ANNUAL")) {
                return Response.status(400)
                    .entity(Map.of("error", "billingCycle inválido: " + item.billingCycle()))
                    .build();
            }

            UUID moduleId;
            UUID planVersionId;
            try {
                moduleId      = UUID.fromString(item.moduleId());
                planVersionId = UUID.fromString(item.planVersionId());
            } catch (IllegalArgumentException e) {
                return Response.status(400)
                    .entity(Map.of("error", "ID inválido: " + e.getMessage()))
                    .build();
            }

            // Garante que o plan_version_id referencia o módulo correto e está ativo
            long count = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plan_version_modules " +
                "WHERE id = :pvId AND module_id = :moduleId AND status = 'active'"
            )
                .setParameter("pvId", planVersionId)
                .setParameter("moduleId", moduleId)
                .getSingleResult()).longValue();

            if (count == 0) {
                return Response.status(400)
                    .entity(Map.of("error", "Plano inválido ou inativo para o módulo " + item.moduleId()))
                    .build();
            }

            // Upsert: atualiza se já existir assinatura para este módulo neste perfil
            em.createNativeQuery("""
                INSERT INTO profile_module_subscriptions
                    (tenant_id, module_id, plan_version_id, billing_cycle, status, started_at, created_by_user_id)
                VALUES
                    (:tenantId, :moduleId, :planVersionId, :billingCycle, 'ACTIVE', NOW(), :userId)
                ON CONFLICT (tenant_id, module_id)
                DO UPDATE SET
                    plan_version_id    = EXCLUDED.plan_version_id,
                    billing_cycle      = EXCLUDED.billing_cycle,
                    status             = 'ACTIVE',
                    started_at         = NOW(),
                    updated_at         = NOW()
            """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleId", moduleId)
                .setParameter("planVersionId", planVersionId)
                .setParameter("billingCycle", billingCycle)
                .setParameter("userId", userId)
                .executeUpdate();
        }

        return Response.ok(Map.of(
            "success", true,
            "tenantId", tenantId.toString(),
            "modulesContracted", request.modules().size()
        )).build();
    }
}
