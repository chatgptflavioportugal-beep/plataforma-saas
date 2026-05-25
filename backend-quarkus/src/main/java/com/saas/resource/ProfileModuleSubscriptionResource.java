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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/subscriptions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileModuleSubscriptionResource {

    @Inject
    EntityManager em;

    // ─── DTOs ────────────────────────────────────────────────────────────────────

    public record ModuleSubscriptionItem(
        String moduleId,
        String planVersionId,
        String billingCycle
    ) {}

    public record ConfirmSubscriptionRequest(
        List<ModuleSubscriptionItem> modules
    ) {}

    // ─── POST /modules — confirmar assinatura ────────────────────────────────────

    /**
     * Confirma a assinatura de módulos para o perfil ativo (tenant resolvido via X-Tenant-ID).
     * Valida que o usuário tem permissão de contratação no perfil.
     * Valida que cada combinação módulo/plano existe e está ativa.
     * Faz upsert para permitir troca de plano ou ciclo de cobrança.
     * Calcula expires_at conforme o ciclo: MONTHLY = +1 mês, ANNUAL = +1 ano.
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

            // Calcula expires_at: MONTHLY = agora + 1 mês, ANNUAL = agora + 1 ano
            OffsetDateTime now       = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime expiresAt = billingCycle.equals("MONTHLY")
                ? now.plusMonths(1)
                : now.plusYears(1);

            // Upsert: atualiza se já existir assinatura para este módulo neste perfil
            em.createNativeQuery("""
                INSERT INTO profile_module_subscriptions
                    (tenant_id, module_id, plan_version_id, billing_cycle, status,
                     started_at, expires_at, canceled_at, created_by_user_id)
                VALUES
                    (:tenantId, :moduleId, :planVersionId, :billingCycle, 'ACTIVE',
                     NOW(), :expiresAt, NULL, :userId)
                ON CONFLICT (tenant_id, module_id)
                DO UPDATE SET
                    plan_version_id    = EXCLUDED.plan_version_id,
                    billing_cycle      = EXCLUDED.billing_cycle,
                    status             = 'ACTIVE',
                    started_at         = NOW(),
                    expires_at         = EXCLUDED.expires_at,
                    canceled_at        = NULL,
                    updated_at         = NOW()
            """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleId", moduleId)
                .setParameter("planVersionId", planVersionId)
                .setParameter("billingCycle", billingCycle)
                .setParameter("expiresAt", expiresAt)
                .setParameter("userId", userId)
                .executeUpdate();
        }

        return Response.ok(Map.of(
            "success", true,
            "tenantId", tenantId.toString(),
            "modulesContracted", request.modules().size()
        )).build();
    }

    // ─── GET /modules — listar assinaturas do perfil ativo ──────────────────────

    /**
     * Lista todas as assinaturas de módulos do perfil ativo (tenant resolvido via X-Tenant-ID).
     * Retorna módulo, plano, ciclo, preços, status e datas.
     */
    @GET
    @Path("/modules")
    @SuppressWarnings("unchecked")
    public Response listModuleSubscriptions(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        UUID tenantId = ctx.getTenantId();

        List<Object[]> rows = em.createNativeQuery(
            "SELECT " +
            "  pms.id::text, " +
            "  pms.tenant_id::text, " +
            "  t.type AS profile_type, " +
            "  pms.module_id::text, " +
            "  pm.name AS module_name, " +
            "  pm.icon_path AS module_icon_path, " +
            "  pms.plan_version_id::text, " +
            "  p.id::text AS plan_id, " +
            "  p.name AS plan_name, " +
            "  p.version AS plan_version, " +
            "  pms.billing_cycle, " +
            "  pvm.monthly_price, " +
            "  pvm.annual_monthly_price, " +
            "  pms.status, " +
            "  pms.started_at::text, " +
            "  pms.expires_at::text, " +
            "  pms.canceled_at::text " +
            "FROM profile_module_subscriptions pms " +
            "JOIN platform_modules pm ON pm.id = pms.module_id " +
            "JOIN plan_version_modules pvm ON pvm.id = pms.plan_version_id " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "JOIN tenants t ON t.id = pms.tenant_id " +
            "WHERE pms.tenant_id = :tenantId " +
            "ORDER BY pms.started_at DESC"
        ).setParameter("tenantId", tenantId).getResultList();

        List<Map<String, Object>> result = rows.stream().map(row -> {
            String rawType = (String) row[2];
            String profileType = rawType == null ? null
                : rawType.equalsIgnoreCase("individual") ? "INDIVIDUAL" : "COMPANY";

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",                   row[0]);
            m.put("profileId",            row[1]);
            m.put("profileType",          profileType);
            m.put("moduleId",             row[3]);
            m.put("moduleName",           row[4]);
            m.put("moduleIconPath",       row[5]);
            m.put("planVersionId",        row[6]);
            m.put("planId",               row[7]);
            m.put("planName",             row[8]);
            m.put("planVersion",          row[9] != null ? ((Number) row[9]).intValue() : null);
            m.put("billingCycle",         row[10]);
            m.put("monthlyPrice",         row[11]);
            m.put("annualMonthlyPrice",   row[12]);
            m.put("status",               row[13]);
            m.put("startedAt",            row[14]);
            m.put("expiresAt",            row[15]);
            m.put("canceledAt",           row[16]);
            return m;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // ─── POST /modules/{id}/cancel — cancelar assinatura ────────────────────────

    /**
     * Cancela uma assinatura de módulo do perfil ativo.
     * Não remove o registro — apenas muda status para CANCELED e preenche canceled_at.
     * Valida que a assinatura pertence ao perfil ativo e que o usuário tem permissão.
     */
    @POST
    @Path("/modules/{id}/cancel")
    @Transactional
    public Response cancelModuleSubscription(
        @PathParam("id") String subscriptionId,
        @Context SecurityContext secCtx
    ) {
        var ctx = TenantContext.from(secCtx);

        if (!List.of("owner", "admin", "finance").contains(ctx.getUserRole())) {
            return Response.status(403)
                .entity(Map.of("error", "Sem permissão para cancelar assinaturas neste perfil"))
                .build();
        }

        UUID tenantId = ctx.getTenantId();
        UUID subId;
        try {
            subId = UUID.fromString(subscriptionId);
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("error", "ID inválido")).build();
        }

        // Valida que a assinatura pertence ao perfil ativo e está ativa
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM profile_module_subscriptions " +
            "WHERE id = :id AND tenant_id = :tenantId AND status = 'ACTIVE'"
        )
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .getSingleResult()).longValue();

        if (count == 0) {
            return Response.status(404)
                .entity(Map.of("error", "Assinatura não encontrada ou já cancelada"))
                .build();
        }

        // Cancela: muda status, preenche canceled_at, mantém histórico
        em.createNativeQuery(
            "UPDATE profile_module_subscriptions " +
            "SET status = 'CANCELED', canceled_at = NOW(), updated_at = NOW() " +
            "WHERE id = :id AND tenant_id = :tenantId"
        )
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .executeUpdate();

        return Response.ok(Map.of(
            "success", true,
            "id", subscriptionId,
            "status", "CANCELED"
        )).build();
    }

    // ─── POST /modules/{id}/reactivate — reativar assinatura cancelada ───────────

    /**
     * Reativa uma assinatura cancelada do perfil ativo.
     * Valida que a assinatura pertence ao perfil ativo e que o usuário tem permissão.
     * Valida que a assinatura está cancelada e ainda não expirou.
     * Restaura status para ACTIVE e limpa canceled_at.
     */
    @POST
    @Path("/modules/{id}/reactivate")
    @Transactional
    public Response reactivateModuleSubscription(
        @PathParam("id") String subscriptionId,
        @Context SecurityContext secCtx
    ) {
        var ctx = TenantContext.from(secCtx);

        if (!List.of("owner", "admin", "finance").contains(ctx.getUserRole())) {
            return Response.status(403)
                .entity(Map.of("error", "Sem permissão para reativar assinaturas neste perfil"))
                .build();
        }

        UUID tenantId = ctx.getTenantId();
        UUID subId;
        try {
            subId = UUID.fromString(subscriptionId);
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("error", "ID inválido")).build();
        }

        // Valida que a assinatura pertence ao perfil ativo, está cancelada e ainda não expirou
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM profile_module_subscriptions " +
            "WHERE id = :id AND tenant_id = :tenantId " +
            "  AND status = 'CANCELED' " +
            "  AND (expires_at IS NULL OR expires_at > NOW())"
        )
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .getSingleResult()).longValue();

        if (count == 0) {
            return Response.status(404)
                .entity(Map.of("error", "Assinatura não encontrada, já ativa ou expirada"))
                .build();
        }

        // Reativa: restaura status ACTIVE, limpa canceled_at
        em.createNativeQuery(
            "UPDATE profile_module_subscriptions " +
            "SET status = 'ACTIVE', canceled_at = NULL, updated_at = NOW() " +
            "WHERE id = :id AND tenant_id = :tenantId"
        )
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .executeUpdate();

        return Response.ok(Map.of(
            "success", true,
            "id", subscriptionId,
            "status", "ACTIVE"
        )).build();
    }
}
