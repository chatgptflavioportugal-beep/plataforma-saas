package com.saas.admin.resource;

import com.saas.admin.dto.PaymentDetailDTO;
import com.saas.admin.dto.PaymentEventDetailDTO;
import com.saas.admin.dto.PaymentEventListItemDTO;
import com.saas.admin.dto.PaymentPageDTO;
import com.saas.admin.dto.PaymentSummaryDTO;
import com.saas.admin.dto.PaymentTimelineEventDTO;
import com.saas.admin.negocio.impl.AdminPaymentNegocio;
import com.saas.platformadmin.PlatformAdminAuthService;
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

import java.util.List;

/**
 * Visão administrativa (100% leitura) de pagamentos de qualquer tenant:
 * listagem/indicadores/detalhe/histórico de webhooks/timeline. Lê
 * payments/payment_webhook_events diretamente (tabelas do payment-service) —
 * mesmo padrão já usado para profile_module_subscriptions em
 * AdminSubscriptionResource. Não há nenhuma ação administrativa de escrita
 * aqui (reembolso, reprocessamento e alteração manual de status ficam fora
 * do escopo desta área, por decisão explícita do pedido original).
 */
@Path("/api/v1/admin/payments")
@Tag(name = "Payments", description = "Visão administrativa de pagamentos de qualquer tenant: listagem com filtros, indicadores agregados, detalhe, histórico de eventos de webhook e timeline. Contexto exclusivamente administrativo e somente leitura — não deve ser utilizado pelo ambiente cliente e não expõe nenhuma ação de escrita.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminPaymentResource {

    @Inject
    PlatformAdminAuthService adminAuth;

    @Inject
    AdminPaymentNegocio paymentNegocio;

    @GET
    @Path("/summary")
    @Operation(
        summary = "Retorna indicadores agregados de pagamentos, respeitando os filtros informados",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo " +
            "ambiente cliente. Aceita exatamente os mesmos filtros de `GET /api/v1/admin/payments` " +
            "e agrega, sobre o mesmo conjunto de resultados: total, pagos, pendentes (PENDING/" +
            "PROCESSING/AUTHORIZED), falhos (FAILED/CANCELLED), vencidos (OVERDUE/EXPIRED), valor " +
            "recebido (soma dos pagos), valor pendente (soma dos pendentes) e quantidade de " +
            "pagamentos com algum evento de webhook em status FAILED. Requer a permissão granular " +
            "'admin.payments.view'."
    )
    @APIResponse(responseCode = "200", description = "Indicadores agregados sobre os pagamentos que atendem aos filtros informados.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.payments.view'.")
    public Response getSummary(
            @QueryParam("id") String id,
            @QueryParam("externalId") String externalId,
            @QueryParam("subscriptionId") String subscriptionId,
            @QueryParam("customerId") String customerId,
            @QueryParam("search") String search,
            @QueryParam("moduleId") String moduleId,
            @QueryParam("gateway") String gateway,
            @QueryParam("paymentMethod") String paymentMethod,
            @QueryParam("type") String type,
            @QueryParam("billingCycle") String billingCycle,
            @QueryParam("status") String status,
            @QueryParam("amountMin") String amountMin,
            @QueryParam("amountMax") String amountMax,
            @QueryParam("dateFrom") String dateFrom,
            @QueryParam("dateTo") String dateTo,
            @QueryParam("hasWebhookError") Boolean hasWebhookError,
            @QueryParam("lastWebhookStatus") String lastWebhookStatus,
            @QueryParam("lastWebhookDateFrom") String lastWebhookDateFrom,
            @QueryParam("lastWebhookDateTo") String lastWebhookDateTo
    ) {
        adminAuth.requireAdminPermission("admin.payments.view");

        PaymentSummaryDTO summary = paymentNegocio.getSummary(
            id, externalId, subscriptionId, customerId, search, moduleId, gateway, paymentMethod, type, billingCycle,
            status, amountMin, amountMax, dateFrom, dateTo, hasWebhookError, lastWebhookStatus, lastWebhookDateFrom, lastWebhookDateTo);

        return Response.ok(summary).build();
    }

    @GET
    @Operation(
        summary = "Lista, com paginação, os pagamentos sob a visão administrativa",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo " +
            "ambiente cliente. Lista registros de payments com dados agregados do cliente/perfil, " +
            "do módulo/plano da assinatura associada (quando existir) e do último evento de webhook " +
            "recebido. `type` é derivado (ONE_TIME quando não há gateway_subscription_id, RECURRING " +
            "caso contrário) — não existe pagamento 'gratuito' nesta lista, pois ativações de módulo " +
            "grátis nunca passam pelo payment-service. Paginação via 'page'/'size' (size limitado a " +
            "1..100). Requer a permissão granular 'admin.payments.view'."
    )
    @APIResponse(responseCode = "200", description = "Página de pagamentos que atendem aos filtros informados, com total de itens.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.payments.view'.")
    public Response listPayments(
            @Parameter(description = "Filtra pelo ID (UUID) interno do pagamento.") @QueryParam("id") String id,
            @Parameter(description = "Filtra pelo ID externo do pagamento no gateway (gateway_payment_id).") @QueryParam("externalId") String externalId,
            @Parameter(description = "Filtra por ID (UUID) da assinatura (profile_module_subscriptions.id) associada.") @QueryParam("subscriptionId") String subscriptionId,
            @Parameter(description = "Filtra por ID (UUID) do cliente/perfil (payments.customer_id).") @QueryParam("customerId") String customerId,
            @Parameter(description = "Busca textual por nome do perfil, nome ou e-mail do responsável.") @QueryParam("search") String search,
            @Parameter(description = "Filtra por ID (UUID) do módulo da assinatura associada.") @QueryParam("moduleId") String moduleId,
            @Parameter(description = "Filtra pelo gateway (ex.: STRIPE, ASAAS).") @QueryParam("gateway") String gateway,
            @Parameter(description = "Filtra pelo método de pagamento (ex.: CREDIT_CARD, PIX, BOLETO).") @QueryParam("paymentMethod") String paymentMethod,
            @Parameter(description = "Filtra pelo tipo derivado: ONE_TIME ou RECURRING.") @QueryParam("type") String type,
            @Parameter(description = "Filtra pelo ciclo de cobrança da assinatura associada: MONTHLY ou ANNUAL. Só se aplica a pagamentos RECURRING.") @QueryParam("billingCycle") String billingCycle,
            @Parameter(description = "Filtra pelo status do pagamento (ex.: PENDING, PAID, FAILED, REFUNDED).") @QueryParam("status") String status,
            @Parameter(description = "Valor mínimo do pagamento (filtro >=).") @QueryParam("amountMin") String amountMin,
            @Parameter(description = "Valor máximo do pagamento (filtro <=).") @QueryParam("amountMax") String amountMax,
            @Parameter(description = "Data/hora mínima de criação do pagamento (filtro >=).") @QueryParam("dateFrom") String dateFrom,
            @Parameter(description = "Data/hora máxima de criação do pagamento (filtro <=).") @QueryParam("dateTo") String dateTo,
            @Parameter(description = "Quando true, retorna só pagamentos com algum evento de webhook FAILED; quando false, exclui esses.") @QueryParam("hasWebhookError") Boolean hasWebhookError,
            @Parameter(description = "Filtra pelo status do último evento de webhook recebido.") @QueryParam("lastWebhookStatus") String lastWebhookStatus,
            @Parameter(description = "Data/hora mínima do último evento de webhook (filtro >=).") @QueryParam("lastWebhookDateFrom") String lastWebhookDateFrom,
            @Parameter(description = "Data/hora máxima do último evento de webhook (filtro <=).") @QueryParam("lastWebhookDateTo") String lastWebhookDateTo,
            @Parameter(description = "Número da página, começando em 0. Padrão: 0.") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Quantidade de itens por página (limitado a 1..100). Padrão: 20.") @QueryParam("size") @DefaultValue("20") int size
    ) {
        adminAuth.requireAdminPermission("admin.payments.view");

        PaymentPageDTO result = paymentNegocio.listPayments(
            id, externalId, subscriptionId, customerId, search, moduleId, gateway, paymentMethod, type, billingCycle,
            status, amountMin, amountMax, dateFrom, dateTo, hasWebhookError, lastWebhookStatus, lastWebhookDateFrom, lastWebhookDateTo,
            page, size);

        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Detalha um pagamento (dados do pagamento, do gateway e da assinatura associada)",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo " +
            "ambiente cliente. `subscription` vem null quando o pagamento não está vinculado a " +
            "nenhuma assinatura (profile_module_subscriptions) — nesse caso o frontend deve indicar " +
            "'sem assinatura associada', não omitir a seção. `metadata` é o JSON bruto gravado pelo " +
            "gateway, exposto somente para consulta. Requer a permissão granular 'admin.payments.view'."
    )
    @APIResponse(responseCode = "200", description = "Detalhe do pagamento.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.payments.view'.")
    @APIResponse(responseCode = "404", description = "Nenhum pagamento encontrado para o `id` informado.")
    public Response getDetail(
            @Parameter(description = "ID (UUID) do pagamento.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.payments.view");

        PaymentDetailDTO detail = paymentNegocio.getDetail(id)
            .orElseThrow(() -> new NotFoundException("Pagamento não encontrado"));
        return Response.ok(detail).build();
    }

    @GET
    @Path("/{id}/events")
    @Operation(
        summary = "Lista o histórico de eventos de webhook de um pagamento",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo " +
            "ambiente cliente. Lista, mais recente primeiro, cada PaymentWebhookEvent associado ao " +
            "pagamento (histórico imutável — eventos nunca são sobrescritos). Não inclui o payload " +
            "bruto (ver `GET /{id}/events/{eventId}` para isso). Aceita filtros por tipo de evento, " +
            "status (RECEIVED/PROCESSED/FAILED/IGNORED) e período. Requer a permissão granular " +
            "'admin.payments.view'."
    )
    @APIResponse(responseCode = "200", description = "Lista de eventos de webhook do pagamento (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.payments.view'.")
    public Response listEvents(
            @Parameter(description = "ID (UUID) do pagamento.", required = true) @PathParam("id") String id,
            @Parameter(description = "Filtra pelo tipo do evento (ex.: payment_intent.succeeded).") @QueryParam("eventType") String eventType,
            @Parameter(description = "Filtra pelo status do evento: RECEIVED, PROCESSED, FAILED ou IGNORED.") @QueryParam("status") String status,
            @Parameter(description = "Data/hora mínima de recebimento do evento (filtro >=).") @QueryParam("dateFrom") String dateFrom,
            @Parameter(description = "Data/hora máxima de recebimento do evento (filtro <=).") @QueryParam("dateTo") String dateTo
    ) {
        adminAuth.requireAdminPermission("admin.payments.view");

        List<PaymentEventListItemDTO> events = paymentNegocio.listEvents(id, eventType, status, dateFrom, dateTo);
        return Response.ok(events).build();
    }

    @GET
    @Path("/{id}/events/{eventId}")
    @Operation(
        summary = "Detalha um evento de webhook de um pagamento, incluindo o payload bruto",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo " +
            "ambiente cliente. Retorna o mesmo evento de `GET /{id}/events` acrescido do payload " +
            "bruto recebido do gateway (somente leitura, para troubleshooting). Requer a permissão " +
            "granular 'admin.payments.view'."
    )
    @APIResponse(responseCode = "200", description = "Detalhe do evento, com payload.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.payments.view'.")
    @APIResponse(responseCode = "404", description = "Nenhum evento encontrado para o `eventId` informado.")
    public Response getEventDetail(
            @Parameter(description = "ID (UUID) do pagamento.", required = true) @PathParam("id") String id,
            @Parameter(description = "ID (UUID) do evento de webhook.", required = true) @PathParam("eventId") String eventId) {
        adminAuth.requireAdminPermission("admin.payments.view");

        PaymentEventDetailDTO event = paymentNegocio.getEventDetail(eventId)
            .orElseThrow(() -> new NotFoundException("Evento de webhook não encontrado"));
        return Response.ok(event).build();
    }

    @GET
    @Path("/{id}/timeline")
    @Operation(
        summary = "Retorna a timeline do pagamento, construída a partir do histórico real",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo " +
            "ambiente cliente. Combina a criação do pagamento com cada evento de webhook recebido, " +
            "em ordem cronológica — nenhum passo é fabricado além desses dois fatos reais. Requer a " +
            "permissão granular 'admin.payments.view'."
    )
    @APIResponse(responseCode = "200", description = "Timeline do pagamento (lista vazia se o pagamento não existir).")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.payments.view'.")
    public Response getTimeline(
            @Parameter(description = "ID (UUID) do pagamento.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.payments.view");

        List<PaymentTimelineEventDTO> timeline = paymentNegocio.getTimeline(id);
        return Response.ok(timeline).build();
    }
}
