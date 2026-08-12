package com.saas.subscription.controller;

import com.saas.subscription.repository.UserTenantRepository;
import com.saas.subscription.security.TenantContext;
import com.saas.subscription.service.TrialCampaignService;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Assinaturas de módulo do perfil ativo (tenant), acionadas pelo próprio cliente: contratação, ativação de plano gratuito, listagem, cancelamento, reativação e consulta de Trial. Distinto de /api/v1/admin/subscriptions (AdminSubscriptionResource), que é escopo administrativo sobre qualquer tenant.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileModuleSubscriptionResource {

    @Inject
    EntityManager em;

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    TrialCampaignService trialCampaignService;

    // ─── DTOs ────────────────────────────────────────────────────────────────────

    public record ModuleSubscriptionItem(
        String moduleId,
        String planVersionId,
        String billingCycle
    ) {}

    public record ConfirmSubscriptionRequest(
        List<ModuleSubscriptionItem> modules
    ) {}

    public record ActivateFreeModuleRequest(
        String moduleSlug
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
    @Operation(
        summary = "Confirma a contratação (assinatura) de um ou mais módulos para o perfil ativo",
        description = "Restrito a usuários com role owner/admin/finance no tenant. Para cada item " +
            "da lista `modules`, valida que a combinação módulo/plano existe e está ativa " +
            "(`plan_version_modules.status = 'active'`), então faz upsert em " +
            "profile_module_subscriptions (chave tenant_id + module_id — troca de plano ou de " +
            "ciclo de cobrança substitui a assinatura existente do módulo). Se o plano for " +
            "elegível para Trial (TrialCampaignService) e houver vaga disponível, a assinatura " +
            "entra em status TRIAL (expira ao fim do período de Trial); caso contrário entra em " +
            "ACTIVE com `expires_at` calculado pelo ciclo (MONTHLY = +1 mês, ANNUAL = +1 ano). " +
            "Uma resubmissão do mesmo Trial já em andamento apenas atualiza a preferência de " +
            "ciclo de cobrança, sem reclamar nova vaga. Ao final, invalida (bump de versão) o " +
            "PAT/MAT em cache de todos os membros do tenant."
    )
    @APIResponse(responseCode = "200", description = "Todos os módulos da lista foram confirmados (contratados ou com Trial iniciado) com sucesso.")
    @APIResponse(responseCode = "400", description = "Lista `modules` ausente/vazia, item com campo obrigatório faltando (moduleId, planVersionId, billingCycle), `billingCycle` inválido, IDs em formato inválido, ou combinação módulo/plano inexistente ou inativa.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não possui role owner, admin ou finance no perfil ativo.")
    @SuppressWarnings("unchecked")
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

            // Garante que o plan_version_id referencia o módulo correto e está ativo.
            long pvmExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plan_version_modules " +
                "WHERE id = :pvId AND module_id = :moduleId AND status = 'active'"
            )
                .setParameter("pvId", planVersionId)
                .setParameter("moduleId", moduleId)
                .getSingleResult()).longValue();

            if (pvmExists == 0) {
                return Response.status(400)
                    .entity(Map.of("error", "Plano inválido ou inativo para o módulo " + item.moduleId()))
                    .build();
            }

            // Estado atual da assinatura (se houver), para detectar resubmissão do
            // mesmo Trial em andamento (não deve consumir uma segunda vaga) e para
            // detectar conversão de Trial em cliente pagante (para o relatório).
            Object[] existing;
            try {
                existing = (Object[]) em.createNativeQuery(
                    "SELECT status, plan_version_id::text, trial_history_id::text, " +
                    "(status IN ('TRIAL', 'TRIAL_CANCELLED') AND expires_at IS NOT NULL AND expires_at > NOW()) AS trial_ongoing " +
                    "FROM profile_module_subscriptions WHERE tenant_id = :tenantId AND module_id = :moduleId"
                )
                    .setParameter("tenantId", tenantId)
                    .setParameter("moduleId", moduleId)
                    .getSingleResult();
            } catch (NoResultException e) {
                existing = null;
            }

            String existingStatus         = existing != null ? (String) existing[0] : null;
            String existingPlanVersionId  = existing != null ? (String) existing[1] : null;
            String existingTrialHistoryId = existing != null ? (String) existing[2] : null;
            boolean trialOngoing          = existing != null && Boolean.TRUE.equals(existing[3]);
            boolean sameOngoingTrial      = trialOngoing && planVersionId.toString().equals(existingPlanVersionId);

            if (sameOngoingTrial) {
                // Resubmissão do mesmo Trial já em andamento (ex.: usuário confirma de
                // novo a mesma tela) — não reclama outra vaga nem reinicia o histórico,
                // só atualiza a preferência de ciclo de cobrança para depois do Trial.
                em.createNativeQuery(
                    "UPDATE profile_module_subscriptions SET billing_cycle = :billingCycle, updated_at = NOW() " +
                    "WHERE tenant_id = :tenantId AND module_id = :moduleId"
                )
                    .setParameter("billingCycle", billingCycle)
                    .setParameter("tenantId", tenantId)
                    .setParameter("moduleId", moduleId)
                    .executeUpdate();
                continue;
            }

            var eligibility = trialCampaignService.checkEligibility(tenantId, moduleId, planVersionId);
            boolean trialEnabled = eligibility.eligible();
            UUID claimedCampaignId = null;
            Integer trialDays = null;

            if (trialEnabled) {
                try {
                    claimedCampaignId = trialCampaignService.claimSlotOrThrow(tenantId, moduleId, planVersionId);
                    trialDays = eligibility.days();
                } catch (BadRequestException raceLost) {
                    // Perdeu a corrida pela última vaga para outra requisição concorrente —
                    // segue o fluxo normal de contratação (ACTIVE) em vez de falhar.
                    trialEnabled = false;
                }
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            String status;
            OffsetDateTime trialStartAt;
            OffsetDateTime trialEndAt;
            OffsetDateTime billingStartsAt;
            OffsetDateTime expiresAt;

            if (trialEnabled) {
                status          = "TRIAL";
                trialStartAt    = now;
                trialEndAt      = now.plusDays(trialDays);
                billingStartsAt = trialEndAt;
                expiresAt       = trialEndAt;
            } else {
                status          = "ACTIVE";
                trialStartAt    = null;
                trialEndAt      = null;
                billingStartsAt = null;
                // Calcula expires_at: MONTHLY = agora + 1 mês, ANNUAL = agora + 1 ano
                expiresAt = billingCycle.equals("MONTHLY") ? now.plusMonths(1) : now.plusYears(1);
            }

            // Perfil estava em Trial e esta contratação o substitui — ou vira cliente
            // pagante (não concedeu novo Trial: sem vagas/cooldown), ou troca para um
            // Trial diferente (outro plano/campanha). Em ambos os casos o Trial
            // anterior está encerrando agora; fecha o registro de histórico
            // correspondente (senão fica orfão, com trial_finished_at nulo para
            // sempre, já que pms.trial_history_id está prestes a apontar para outro
            // registro). "Virou cliente" só quando a substituição NÃO é por outro Trial.
            boolean wasTrial = "TRIAL".equals(existingStatus) || "TRIAL_CANCELLED".equals(existingStatus);
            if (wasTrial && existingTrialHistoryId != null) {
                em.createNativeQuery(
                    "UPDATE module_trial_history SET " +
                    "became_customer = became_customer OR :becameCustomer, " +
                    "trial_finished_at = COALESCE(trial_finished_at, NOW()) WHERE id::text = :id"
                )
                    .setParameter("becameCustomer", !trialEnabled)
                    .setParameter("id", existingTrialHistoryId)
                    .executeUpdate();
            }

            // Registra o início deste Trial no ledger permanente (histórico de
            // participação, nunca removido) — usado tanto para o cooldown de
            // reutilização quanto para o relatório de Participantes de cada campanha.
            UUID historyId = null;
            if (trialEnabled) {
                historyId = UUID.randomUUID();
                em.createNativeQuery(
                    "INSERT INTO module_trial_history " +
                    "(id, tenant_id, module_id, plan_version_module_id, trial_campaign_id, started_by_user_id, trial_started_at) " +
                    "VALUES (:id, :tenantId, :moduleId, :planVersionId, :campaignId, :userId, :trialStartAt)"
                )
                    .setParameter("id", historyId)
                    .setParameter("tenantId", tenantId)
                    .setParameter("moduleId", moduleId)
                    .setParameter("planVersionId", planVersionId)
                    .setParameter("campaignId", claimedCampaignId)
                    .setParameter("userId", userId)
                    .setParameter("trialStartAt", trialStartAt)
                    .executeUpdate();
            }

            // Upsert: atualiza se já existir assinatura para este módulo neste perfil.
            // Trocar de plano durante um Trial cancela o Trial atual e inicia o do novo
            // plano (mesma linha, mesmo comportamento de upsert já usado para preços/limites).
            em.createNativeQuery("""
                INSERT INTO profile_module_subscriptions
                    (tenant_id, module_id, plan_version_id, billing_cycle, status,
                     started_at, expires_at, canceled_at, created_by_user_id,
                     trial_days, trial_start_at, trial_end_at, billing_starts_at,
                     trial_campaign_id, trial_history_id)
                VALUES
                    (:tenantId, :moduleId, :planVersionId, :billingCycle, :status,
                     NOW(), :expiresAt, NULL, :userId,
                     :trialDays, :trialStartAt, :trialEndAt, :billingStartsAt,
                     :trialCampaignId, :trialHistoryId)
                ON CONFLICT (tenant_id, module_id)
                DO UPDATE SET
                    plan_version_id    = EXCLUDED.plan_version_id,
                    billing_cycle      = EXCLUDED.billing_cycle,
                    status             = EXCLUDED.status,
                    started_at         = NOW(),
                    expires_at         = EXCLUDED.expires_at,
                    canceled_at        = NULL,
                    trial_days         = EXCLUDED.trial_days,
                    trial_start_at     = EXCLUDED.trial_start_at,
                    trial_end_at       = EXCLUDED.trial_end_at,
                    billing_starts_at  = EXCLUDED.billing_starts_at,
                    trial_campaign_id  = EXCLUDED.trial_campaign_id,
                    trial_history_id   = EXCLUDED.trial_history_id,
                    updated_at         = NOW()
            """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleId", moduleId)
                .setParameter("planVersionId", planVersionId)
                .setParameter("billingCycle", billingCycle)
                .setParameter("status", status)
                .setParameter("expiresAt", expiresAt)
                .setParameter("userId", userId)
                .setParameter("trialDays", trialDays)
                .setParameter("trialStartAt", trialStartAt)
                .setParameter("trialEndAt", trialEndAt)
                .setParameter("billingStartsAt", billingStartsAt)
                .setParameter("trialCampaignId", claimedCampaignId)
                .setParameter("trialHistoryId", historyId)
                .executeUpdate();
        }

        // Assinatura de módulo mudou — invalida PAT/MAT em cache de todos os membros do tenant.
        userTenantRepository.bumpVersionForTenant(tenantId);

        return Response.ok(Map.of(
            "success", true,
            "tenantId", tenantId.toString(),
            "modulesContracted", request.modules().size()
        )).build();
    }

    // ─── POST /free — ativação sob demanda de módulo com plano Free ─────────────

    /**
     * Ativa (cria ou reativa) a assinatura Free de um módulo para o perfil ativo,
     * no primeiro acesso do usuário ao módulo (lazy activation).
     *
     * Perfil Individual: qualquer usuário do perfil pode ativar.
     * Perfil Empresarial: owner/admin, ou membro com a permissão "plans.subscribe".
     *
     * Idempotente: se já existir assinatura ACTIVE não expirada, não faz nada.
     */
    @POST
    @Path("/free")
    @Transactional
    @Operation(
        summary = "Ativa sob demanda a assinatura gratuita de um módulo para o perfil ativo",
        description = "Ativação preguiçosa (lazy activation), tipicamente disparada no primeiro " +
            "acesso do usuário a um módulo com plano Free. Regra de permissão: em perfil " +
            "Individual, qualquer usuário do perfil pode ativar; em perfil Empresarial, requer " +
            "role owner/admin ou a permissão administrativa `plans.subscribe` no nível de acesso " +
            "do membro. Idempotente — se já existir assinatura ACTIVE não expirada para o " +
            "módulo, a chamada não faz nada e apenas confirma o estado atual. Ao ativar de fato, " +
            "invalida (bump de versão) o PAT/MAT em cache de todos os membros do tenant."
    )
    @APIResponse(responseCode = "200", description = "Módulo gratuito ativado (ou já estava ativo — idempotente); retorna o módulo e o plano gratuito associado.")
    @APIResponse(responseCode = "400", description = "`moduleSlug` ausente/em branco, ou o módulo não possui nenhum plano com preço zero (`monthly_price = 0`) ativo.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    @APIResponse(responseCode = "403", description = "Perfil Empresarial e usuário não é owner/admin nem possui a permissão `plans.subscribe`.")
    @APIResponse(responseCode = "404", description = "`moduleSlug` não corresponde a nenhum módulo ativo do catálogo.")
    @SuppressWarnings("unchecked")
    public Response activateFreeModule(
        ActivateFreeModuleRequest request,
        @Context SecurityContext secCtx
    ) {
        var ctx = TenantContext.from(secCtx);
        UUID tenantId = ctx.getTenantId();
        UUID userId   = ctx.getUserId();

        if (request == null || request.moduleSlug() == null || request.moduleSlug().isBlank()) {
            return Response.status(400)
                .entity(Map.of("error", "moduleSlug é obrigatório"))
                .build();
        }

        if (!canActivateFreeModule(ctx)) {
            return Response.status(403)
                .entity(Map.of("error", "Sem permissão para ativar módulos gratuitos neste perfil"))
                .build();
        }

        List<UUID> moduleRows = em.createNativeQuery(
            "SELECT id FROM platform_modules WHERE slug = :slug AND is_active = TRUE"
        ).setParameter("slug", request.moduleSlug()).getResultList();

        if (moduleRows.isEmpty()) {
            return Response.status(404)
                .entity(Map.of("error", "Módulo não encontrado: " + request.moduleSlug()))
                .build();
        }
        UUID moduleId = moduleRows.get(0);

        List<Object[]> freeRows = em.createNativeQuery(
            "SELECT pvm.id, p.name FROM plan_version_modules pvm " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "WHERE pvm.module_id = :moduleId AND pvm.status = 'active' AND pvm.monthly_price = 0 " +
            "LIMIT 1"
        ).setParameter("moduleId", moduleId).getResultList();

        if (freeRows.isEmpty()) {
            return Response.status(400)
                .entity(Map.of("error", "Este módulo não possui plano gratuito"))
                .build();
        }
        UUID planVersionId = (UUID) freeRows.get(0)[0];
        String planName    = (String) freeRows.get(0)[1];

        // Idempotência: já ativo e não expirado — não faz nada
        long activeCount = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM profile_module_subscriptions " +
            "WHERE tenant_id = :tenantId AND module_id = :moduleId AND status = 'ACTIVE' " +
            "  AND (expires_at IS NULL OR expires_at > NOW())"
        )
            .setParameter("tenantId", tenantId)
            .setParameter("moduleId", moduleId)
            .getSingleResult()).longValue();

        if (activeCount == 0) {
            em.createNativeQuery("""
                INSERT INTO profile_module_subscriptions
                    (tenant_id, module_id, plan_version_id, billing_cycle, status,
                     started_at, expires_at, canceled_at, created_by_user_id)
                VALUES
                    (:tenantId, :moduleId, :planVersionId, 'FREE', 'ACTIVE',
                     NOW(), NULL, NULL, :userId)
                ON CONFLICT (tenant_id, module_id)
                DO UPDATE SET
                    plan_version_id    = EXCLUDED.plan_version_id,
                    billing_cycle      = 'FREE',
                    status             = 'ACTIVE',
                    started_at         = NOW(),
                    expires_at         = NULL,
                    canceled_at        = NULL,
                    updated_at         = NOW()
            """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleId", moduleId)
                .setParameter("planVersionId", planVersionId)
                .setParameter("userId", userId)
                .executeUpdate();

            // Módulo Free ativado — invalida PAT/MAT em cache de todos os membros do tenant.
            userTenantRepository.bumpVersionForTenant(tenantId);
        }

        return Response.ok(Map.of(
            "success",       true,
            "moduleId",      moduleId.toString(),
            "moduleSlug",    request.moduleSlug(),
            "planVersionId", planVersionId.toString(),
            "planName",      planName
        )).build();
    }

    private boolean canActivateFreeModule(TenantContext ctx) {
        String rawType = (String) em.createNativeQuery(
            "SELECT type FROM tenants WHERE id = :id"
        ).setParameter("id", ctx.getTenantId()).getSingleResult();

        if ("individual".equals(rawType)) {
            return true;
        }

        String role = ctx.getUserRole();
        if (List.of("owner", "admin").contains(role)) {
            return true;
        }

        List<?> permRows = em.createNativeQuery("""
            SELECT 1
            FROM user_tenants ut
            JOIN profile_access_level_admin_permissions ap ON ap.access_level_id = ut.access_level_id
            WHERE ut.user_id = :userId
              AND ut.tenant_id = :tenantId
              AND ut.is_active = TRUE
              AND ap.permission_key = 'plans.subscribe'
            LIMIT 1
        """)
            .setParameter("userId", ctx.getUserId())
            .setParameter("tenantId", ctx.getTenantId())
            .getResultList();

        return !permRows.isEmpty();
    }

    // ─── GET /modules — listar assinaturas do perfil ativo ──────────────────────

    /**
     * Lista todas as assinaturas de módulos do perfil ativo (tenant resolvido via X-Tenant-ID).
     * Retorna módulo, plano, ciclo, preços, status e datas.
     */
    @GET
    @Path("/modules")
    @Operation(
        summary = "Lista todas as assinaturas de módulo do perfil ativo",
        description = "Operação exclusivamente de consulta. Retorna, para cada assinatura do " +
            "tenant (independente de status), o módulo, o plano contratado (nome, código, versão), " +
            "o ciclo de cobrança, preços mensal/anual, status, datas (início, expiração, " +
            "cancelamento, período de Trial) e os limites do plano contratado. Ordenado por " +
            "`started_at` decrescente (mais recentes primeiro)."
    )
    @APIResponse(responseCode = "200", description = "Lista de assinaturas de módulo do perfil ativo (pode ser vazia se o tenant não tiver nenhuma).")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
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
            "  p.code AS plan_code, " +
            "  p.version AS plan_version, " +
            "  p.sort_order AS plan_sort_order, " +
            "  pms.billing_cycle, " +
            "  pvm.monthly_price, " +
            "  pvm.annual_monthly_price, " +
            "  pms.status, " +
            "  pms.started_at::text, " +
            "  pms.expires_at::text, " +
            "  pms.canceled_at::text, " +
            "  pms.trial_days, " +
            "  pms.trial_start_at::text, " +
            "  pms.trial_end_at::text, " +
            "  pms.billing_starts_at::text, " +
            "  pms.trial_campaign_id::text, " +
            "  COALESCE((SELECT json_agg(json_build_object(" +
            "    'title', pvml.title, 'description', pvml.description," +
            "    'limit_value', pvml.limit_value, 'unit', pvml.unit, 'sort_order', pvml.sort_order" +
            "  ) ORDER BY pvml.sort_order) FROM plan_version_module_limits pvml" +
            "  WHERE pvml.plan_version_module_id = pvm.id), '[]'::json)::text AS limits_json " +
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
            m.put("planCode",             row[9]);
            m.put("planVersion",          row[10] != null ? ((Number) row[10]).intValue() : null);
            m.put("planSortOrder",        row[11] != null ? ((Number) row[11]).intValue() : null);
            m.put("billingCycle",         row[12]);
            m.put("monthlyPrice",         row[13]);
            m.put("annualMonthlyPrice",   row[14]);
            m.put("status",               row[15]);
            m.put("startedAt",            row[16]);
            m.put("expiresAt",            row[17]);
            m.put("canceledAt",           row[18]);
            m.put("trialDays",            row[19] != null ? ((Number) row[19]).intValue() : null);
            m.put("trialStartAt",         row[20]);
            m.put("trialEndAt",           row[21]);
            m.put("billingStartsAt",      row[22]);
            m.put("trialCampaignId",      row[23]);
            m.put("limitsJson",           row[24]);
            return m;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // ─── POST /modules/{id}/cancel — cancelar assinatura ────────────────────────

    /**
     * Cancela uma assinatura de módulo do perfil ativo.
     * Assinaturas ACTIVE viram CANCELED — acesso mantido até expires_at, como antes.
     * Assinaturas em TRIAL viram TRIAL_CANCELLED — cancela apenas a renovação
     * automática; o acesso permanece liberado até trial_end_at (expires_at), quando
     * o scheduler expira o módulo automaticamente, sem cobrança.
     * Valida que a assinatura pertence ao perfil ativo e que o usuário tem permissão.
     */
    @POST
    @Path("/modules/{id}/cancel")
    @Transactional
    @Operation(
        summary = "Cancela uma assinatura de módulo do perfil ativo",
        description = "Restrito a usuários com role owner/admin/finance no tenant. Assinaturas " +
            "ACTIVE viram CANCELED — acesso permanece liberado até `expires_at`. Assinaturas em " +
            "TRIAL viram TRIAL_CANCELLED — cancela apenas a renovação automática; o acesso segue " +
            "liberado até o fim do Trial (`expires_at`), quando o scheduler expira o módulo " +
            "automaticamente sem cobrança. O mesmo endpoint serve tanto para \"Cancelar " +
            "assinatura\" quanto para \"Cancelar Trial\". Invalida (bump de versão) o PAT/MAT em " +
            "cache de todos os membros do tenant."
    )
    @APIResponse(responseCode = "200", description = "Assinatura cancelada com sucesso; retorna o novo status (CANCELED ou TRIAL_CANCELLED).")
    @APIResponse(responseCode = "400", description = "`id` não é um UUID válido.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não possui role owner, admin ou finance no perfil ativo.")
    @APIResponse(responseCode = "404", description = "Assinatura não encontrada para o perfil ativo, ou já não está em status ACTIVE/TRIAL (já cancelada).")
    @SuppressWarnings("unchecked")
    public Response cancelModuleSubscription(
        @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a cancelar.", required = true)
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

        // Valida que a assinatura pertence ao perfil ativo e está ativa ou em Trial
        // ("Cancelar Trial" usa o mesmo endpoint — apenas cancela a renovação).
        List<Object[]> rows = em.createNativeQuery(
            "SELECT status, trial_history_id::text FROM profile_module_subscriptions " +
            "WHERE id = :id AND tenant_id = :tenantId AND status IN ('ACTIVE', 'TRIAL')"
        )
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .getResultList();

        if (rows.isEmpty()) {
            return Response.status(404)
                .entity(Map.of("error", "Assinatura não encontrada ou já cancelada"))
                .build();
        }

        boolean wasTrial = "TRIAL".equals(rows.get(0)[0]);
        String trialHistoryId = (String) rows.get(0)[1];
        String newStatus = wasTrial ? "TRIAL_CANCELLED" : "CANCELED";

        // Cancela: muda status, preenche canceled_at, mantém histórico.
        // Nunca mexe em expires_at — o acesso permanece válido até lá em ambos os casos.
        em.createNativeQuery(
            "UPDATE profile_module_subscriptions " +
            "SET status = :status, canceled_at = NOW(), updated_at = NOW() " +
            "WHERE id = :id AND tenant_id = :tenantId"
        )
            .setParameter("status", newStatus)
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .executeUpdate();

        if (wasTrial && trialHistoryId != null) {
            em.createNativeQuery(
                "UPDATE module_trial_history SET trial_canceled_at = NOW() WHERE id::text = :id"
            )
                .setParameter("id", trialHistoryId)
                .executeUpdate();
        }

        userTenantRepository.bumpVersionForTenant(tenantId);

        return Response.ok(Map.of(
            "success", true,
            "id", subscriptionId,
            "status", newStatus
        )).build();
    }

    // ─── POST /modules/{id}/reactivate — reativar assinatura cancelada ───────────

    /**
     * Reativa uma assinatura cancelada (ou um Trial cancelado) do perfil ativo.
     * Valida que a assinatura pertence ao perfil ativo e que o usuário tem permissão.
     * Valida que a assinatura está cancelada e ainda não expirou.
     * CANCELED volta para ACTIVE; TRIAL_CANCELLED volta para TRIAL.
     */
    @POST
    @Path("/modules/{id}/reactivate")
    @Transactional
    @Operation(
        summary = "Reativa uma assinatura de módulo cancelada (ou um Trial cancelado) do perfil ativo",
        description = "Restrito a usuários com role owner/admin/finance no tenant. Só reativa " +
            "assinaturas ainda dentro da validade (`expires_at` nulo ou futuro): CANCELED volta " +
            "para ACTIVE, TRIAL_CANCELLED volta para TRIAL (retomando a renovação automática ao " +
            "fim do período). Invalida (bump de versão) o PAT/MAT em cache de todos os membros " +
            "do tenant."
    )
    @APIResponse(responseCode = "200", description = "Assinatura reativada com sucesso; retorna o novo status (ACTIVE ou TRIAL).")
    @APIResponse(responseCode = "400", description = "`id` não é um UUID válido.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não possui role owner, admin ou finance no perfil ativo.")
    @APIResponse(responseCode = "404", description = "Assinatura não encontrada para o perfil ativo, não está em status CANCELED/TRIAL_CANCELLED, ou já expirou.")
    @SuppressWarnings("unchecked")
    public Response reactivateModuleSubscription(
        @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a reativar.", required = true)
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

        // Valida que a assinatura pertence ao perfil ativo, está cancelada (ACTIVE ou
        // Trial) e ainda não expirou.
        List<Object[]> rows = em.createNativeQuery(
            "SELECT status, trial_history_id::text FROM profile_module_subscriptions " +
            "WHERE id = :id AND tenant_id = :tenantId " +
            "  AND status IN ('CANCELED', 'TRIAL_CANCELLED') " +
            "  AND (expires_at IS NULL OR expires_at > NOW())"
        )
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .getResultList();

        if (rows.isEmpty()) {
            return Response.status(404)
                .entity(Map.of("error", "Assinatura não encontrada, já ativa ou expirada"))
                .build();
        }

        boolean wasTrialCancelled = "TRIAL_CANCELLED".equals(rows.get(0)[0]);
        String trialHistoryId = (String) rows.get(0)[1];
        String restoredStatus = wasTrialCancelled ? "TRIAL" : "ACTIVE";

        // Reativa: restaura status (TRIAL ou ACTIVE), limpa canceled_at
        em.createNativeQuery(
            "UPDATE profile_module_subscriptions " +
            "SET status = :status, canceled_at = NULL, updated_at = NOW() " +
            "WHERE id = :id AND tenant_id = :tenantId"
        )
            .setParameter("status", restoredStatus)
            .setParameter("id", subId)
            .setParameter("tenantId", tenantId)
            .executeUpdate();

        if (wasTrialCancelled && trialHistoryId != null) {
            em.createNativeQuery(
                "UPDATE module_trial_history SET trial_canceled_at = NULL WHERE id::text = :id"
            )
                .setParameter("id", trialHistoryId)
                .executeUpdate();
        }

        userTenantRepository.bumpVersionForTenant(tenantId);

        return Response.ok(Map.of(
            "success", true,
            "id", subscriptionId,
            "status", restoredStatus
        )).build();
    }

    // ─── GET /trial-history — histórico de participação em Trial do perfil ──────

    @GET
    @Path("/trial-history")
    @Operation(
        summary = "Lista o histórico de participação em Trial do perfil ativo",
        description = "Operação exclusivamente de consulta ao ledger permanente " +
            "(module_trial_history) de Trials iniciados pelo tenant, por módulo — usado tanto " +
            "para exibir o histórico ao usuário quanto, internamente, para o cálculo de cooldown " +
            "de reutilização de Trial (TrialCampaignService). Cada registro indica se o Trial " +
            "terminou (`trialFinishedAt`), se foi cancelado (`trialCanceledAt`) e se resultou em " +
            "conversão para cliente pagante (`becameCustomer`). Ordenado por início do Trial " +
            "decrescente."
    )
    @APIResponse(responseCode = "200", description = "Histórico de Trials do perfil ativo (pode ser vazio se o tenant nunca participou de nenhum).")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    @SuppressWarnings("unchecked")
    public Response listTrialHistory(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        UUID tenantId = ctx.getTenantId();

        List<Object[]> rows = em.createNativeQuery(
            "SELECT module_id::text, trial_campaign_id::text, trial_started_at::text, " +
            "trial_finished_at::text, trial_canceled_at::text, became_customer " +
            "FROM module_trial_history WHERE tenant_id = :tenantId ORDER BY trial_started_at DESC"
        ).setParameter("tenantId", tenantId).getResultList();

        List<Map<String, Object>> result = rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("moduleId",        row[0]);
            m.put("trialCampaignId", row[1]);
            m.put("trialStartedAt",  row[2]);
            m.put("trialFinishedAt", row[3]);
            m.put("trialCanceledAt", row[4]);
            m.put("becameCustomer",  Boolean.TRUE.equals(row[5]));
            return m;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // ─── GET /trial-eligibility — elegibilidade de Trial por módulo/plano ───────

    @GET
    @Path("/trial-eligibility")
    @Operation(
        summary = "Consulta a elegibilidade de Trial do perfil ativo para cada plano de módulo vigente",
        description = "Operação exclusivamente de consulta. Para cada combinação módulo/plano " +
            "corrente e ativa no catálogo (plan_version_modules com status 'active', do plano " +
            "ativo e na versão corrente), delega a TrialCampaignService.checkEligibility a " +
            "decisão de elegibilidade do tenant a um Trial daquele plano — considerando campanha " +
            "vigente, vagas disponíveis e cooldown de reutilização. Usado pelo frontend para " +
            "exibir (ou ocultar) a oferta de Trial na tela de contratação antes de o usuário " +
            "confirmar a assinatura."
    )
    @APIResponse(responseCode = "200", description = "Lista com a elegibilidade de Trial (elegível ou não, dias, campanha, motivo, fim do cooldown) para cada plano de módulo vigente no catálogo.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    @SuppressWarnings("unchecked")
    public Response listTrialEligibility(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        UUID tenantId = ctx.getTenantId();

        List<Object[]> pvmRows = em.createNativeQuery(
            "SELECT pvm.id, pvm.module_id FROM plan_version_modules pvm " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "WHERE pvm.status = 'active' AND p.is_active = TRUE AND p.is_current_version = TRUE"
        ).getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : pvmRows) {
            UUID pvmId    = (UUID) row[0];
            UUID moduleId = (UUID) row[1];
            var elig = trialCampaignService.checkEligibility(tenantId, moduleId, pvmId);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("planVersionModuleId", pvmId.toString());
            m.put("moduleId",            moduleId.toString());
            m.put("eligible",            elig.eligible());
            m.put("days",                elig.days());
            m.put("campaignName",        elig.campaignName());
            m.put("reasonCode",          elig.reasonCode());
            m.put("cooldownEndsAt",      elig.cooldownEndsAt());
            result.add(m);
        }

        return Response.ok(result).build();
    }
}
