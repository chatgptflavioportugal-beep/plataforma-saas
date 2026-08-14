package com.saas.subscription.controller;

import com.saas.subscription.dto.request.ActivateFreeModuleRequest;
import com.saas.subscription.dto.request.ConfirmSubscriptionRequest;
import com.saas.subscription.dto.response.ActivateFreeModuleResponse;
import com.saas.subscription.dto.response.ConfirmSubscriptionResponse;
import com.saas.subscription.dto.response.SubscriptionActionResponse;
import com.saas.subscription.security.TenantContext;
import com.saas.subscription.service.ProfileModuleSubscriptionService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Assinaturas de módulo do perfil ativo (tenant), acionadas pelo próprio cliente: contratação, ativação de plano gratuito, listagem, cancelamento, reativação e consulta de Trial. Distinto de /api/v1/admin/subscriptions (AdminSubscriptionResource), que é escopo administrativo sobre qualquer tenant.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileModuleSubscriptionResource {

    @Inject
    ProfileModuleSubscriptionService subscriptionService;

    // ─── POST /modules — confirmar assinatura ────────────────────────────────────

    @POST
    @Path("/modules")
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
    public Response confirmModuleSubscriptions(ConfirmSubscriptionRequest request, @Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        var result = subscriptionService.confirmModuleSubscriptions(request, ctx);
        return Response.ok(new ConfirmSubscriptionResponse(true, result.tenantId().toString(), result.modulesContracted())).build();
    }

    // ─── POST /free — ativação sob demanda de módulo com plano Free ─────────────

    @POST
    @Path("/free")
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
    public Response activateFreeModule(ActivateFreeModuleRequest request, @Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        String moduleSlug = request != null ? request.moduleSlug() : null;
        var result = subscriptionService.activateFreeModule(moduleSlug, ctx);
        return Response.ok(new ActivateFreeModuleResponse(
            true, result.moduleId().toString(), moduleSlug, result.planVersionId().toString(), result.planName()
        )).build();
    }

    // ─── GET /modules — listar assinaturas do perfil ativo ──────────────────────

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
    public Response listModuleSubscriptions(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        return Response.ok(subscriptionService.listModuleSubscriptions(ctx.getTenantId())).build();
    }

    // ─── POST /modules/{id}/cancel — cancelar assinatura ────────────────────────

    @POST
    @Path("/modules/{id}/cancel")
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
    public Response cancelModuleSubscription(
        @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a cancelar.", required = true)
        @PathParam("id") String subscriptionId,
        @Context SecurityContext secCtx
    ) {
        var ctx = TenantContext.from(secCtx);
        var result = subscriptionService.cancelModuleSubscription(subscriptionId, ctx);
        return Response.ok(new SubscriptionActionResponse(true, subscriptionId, result.status())).build();
    }

    // ─── POST /modules/{id}/reactivate — reativar assinatura cancelada ───────────

    @POST
    @Path("/modules/{id}/reactivate")
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
    public Response reactivateModuleSubscription(
        @Parameter(description = "ID (UUID) da assinatura de módulo (profile_module_subscriptions.id) a reativar.", required = true)
        @PathParam("id") String subscriptionId,
        @Context SecurityContext secCtx
    ) {
        var ctx = TenantContext.from(secCtx);
        var result = subscriptionService.reactivateModuleSubscription(subscriptionId, ctx);
        return Response.ok(new SubscriptionActionResponse(true, subscriptionId, result.status())).build();
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
    public Response listTrialHistory(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        return Response.ok(subscriptionService.listTrialHistory(ctx.getTenantId())).build();
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
    public Response listTrialEligibility(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        return Response.ok(subscriptionService.listTrialEligibility(ctx.getTenantId())).build();
    }
}
