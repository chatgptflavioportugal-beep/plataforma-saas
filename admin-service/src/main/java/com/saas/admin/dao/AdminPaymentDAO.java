package com.saas.admin.dao;

import com.saas.admin.to.PaymentDetailTO;
import com.saas.admin.to.PaymentEventDetailTO;
import com.saas.admin.to.PaymentEventTO;
import com.saas.admin.to.PaymentListItemTO;
import com.saas.admin.to.PaymentsSummaryTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import com.saas.platformdatabase.query.NativeQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leitura administrativa de payments/payment_webhook_events — tabelas de
 * propriedade do payment-service, lidas diretamente pelo admin-service (mesmo
 * padrão já usado para profile_module_subscriptions em AdminGeneralDAO): não
 * há nenhuma ação de escrita nesta área (seção 38 do pedido a exclui
 * explicitamente), então não existe motivo para um proxy REST ao
 * payment-service — role_admin_service já tem SELECT sobre todas as tabelas
 * (migration 0051_least_privilege_roles.sql).
 */
@ApplicationScoped
public class AdminPaymentDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    // "Tipo" (ONE_TIME/RECURRING) não é um campo persistido — é derivado da
    // presença de gateway_subscription_id. "Gratuito" não existe como valor
    // possível: ativações de módulo grátis nunca passam pelo payment-service
    // (POST /api/v1/subscriptions/free, no subscription-service, não chama
    // payment-service) — não há filtro para isso porque sempre retornaria zero.
    private static final String TYPE_EXPRESSION =
        "CASE WHEN p.gateway_subscription_id IS NULL THEN 'ONE_TIME' ELSE 'RECURRING' END";

    private static final String BASE_FROM =
        "FROM payments p " +
        "LEFT JOIN profile_module_subscriptions pms ON pms.id = p.subscription_id " +
        "LEFT JOIN platform_modules pm ON pm.id = pms.module_id " +
        "LEFT JOIN plan_version_modules pvm ON pvm.id = pms.plan_version_id " +
        "LEFT JOIN plans pl ON pl.id = pvm.plan_id " +
        "LEFT JOIN tenants t ON t.id = p.customer_id " +
        "LEFT JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.role = 'owner' AND ut.is_active = TRUE " +
        "LEFT JOIN user_profiles up ON up.id = ut.user_id " +
        "LEFT JOIN auth.users au ON au.id = ut.user_id " +
        "LEFT JOIN LATERAL (" +
        "  SELECT event_type, status, created_at FROM payment_webhook_events e " +
        "  WHERE e.payment_id = p.id ORDER BY e.created_at DESC LIMIT 1" +
        ") last_evt ON true ";

    public record SearchFilters(
        String id, String externalId, String subscriptionId, String customerId, String search,
        String moduleId, String gateway, String paymentMethod, String type, String billingCycle,
        String status, String amountMin, String amountMax, String dateFrom, String dateTo,
        Boolean hasWebhookError, String lastWebhookStatus, String lastWebhookDateFrom, String lastWebhookDateTo,
        int size, int offset) {
    }

    public record PageResult(List<PaymentListItemTO> items, long total) {
    }

    private void appendCommonFilters(StringBuilder sql, Map<String, Object> params, SearchFilters f) {
        if (f.id() != null && !f.id().isBlank()) {
            sql.append(" AND p.id::text = :id");
            params.put("id", f.id().trim());
        }
        if (f.externalId() != null && !f.externalId().isBlank()) {
            sql.append(" AND p.gateway_payment_id = :externalId");
            params.put("externalId", f.externalId().trim());
        }
        if (f.subscriptionId() != null && !f.subscriptionId().isBlank()) {
            sql.append(" AND p.subscription_id::text = :subscriptionId");
            params.put("subscriptionId", f.subscriptionId().trim());
        }
        if (f.customerId() != null && !f.customerId().isBlank()) {
            sql.append(" AND p.customer_id::text = :customerId");
            params.put("customerId", f.customerId().trim());
        }
        if (f.search() != null && !f.search().isBlank()) {
            sql.append(" AND (LOWER(COALESCE(t.name,'')) LIKE LOWER(:search)" +
                       " OR LOWER(COALESCE(up.full_name,'')) LIKE LOWER(:search)" +
                       " OR LOWER(COALESCE(au.email,'')) LIKE LOWER(:search))");
            params.put("search", "%" + f.search().trim() + "%");
        }
        if (f.moduleId() != null && !f.moduleId().isBlank()) {
            sql.append(" AND pm.id::text = :moduleId");
            params.put("moduleId", f.moduleId().trim());
        }
        if (f.gateway() != null && !f.gateway().isBlank()) {
            sql.append(" AND p.gateway = :gateway");
            params.put("gateway", f.gateway().toUpperCase().trim());
        }
        if (f.paymentMethod() != null && !f.paymentMethod().isBlank()) {
            sql.append(" AND p.payment_method = :paymentMethod");
            params.put("paymentMethod", f.paymentMethod().toUpperCase().trim());
        }
        if (f.type() != null && !f.type().isBlank()) {
            sql.append("RECURRING".equalsIgnoreCase(f.type().trim())
                ? " AND p.gateway_subscription_id IS NOT NULL"
                : " AND p.gateway_subscription_id IS NULL");
        }
        if (f.billingCycle() != null && !f.billingCycle().isBlank()) {
            sql.append(" AND pms.billing_cycle = :billingCycle");
            params.put("billingCycle", f.billingCycle().toUpperCase().trim());
        }
        if (f.status() != null && !f.status().isBlank()) {
            sql.append(" AND p.status = :status");
            params.put("status", f.status().toUpperCase().trim());
        }
        if (f.amountMin() != null && !f.amountMin().isBlank()) {
            sql.append(" AND p.amount >= CAST(:amountMin AS NUMERIC)");
            params.put("amountMin", f.amountMin().trim());
        }
        if (f.amountMax() != null && !f.amountMax().isBlank()) {
            sql.append(" AND p.amount <= CAST(:amountMax AS NUMERIC)");
            params.put("amountMax", f.amountMax().trim());
        }
        if (f.dateFrom() != null && !f.dateFrom().isBlank()) {
            sql.append(" AND p.created_at >= CAST(:dateFrom AS TIMESTAMPTZ)");
            params.put("dateFrom", f.dateFrom().trim());
        }
        if (f.dateTo() != null && !f.dateTo().isBlank()) {
            sql.append(" AND p.created_at <= CAST(:dateTo AS TIMESTAMPTZ)");
            params.put("dateTo", f.dateTo().trim());
        }
        if (Boolean.TRUE.equals(f.hasWebhookError())) {
            sql.append(" AND EXISTS (SELECT 1 FROM payment_webhook_events e WHERE e.payment_id = p.id AND e.status = 'FAILED')");
        } else if (Boolean.FALSE.equals(f.hasWebhookError())) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM payment_webhook_events e WHERE e.payment_id = p.id AND e.status = 'FAILED')");
        }
        if (f.lastWebhookStatus() != null && !f.lastWebhookStatus().isBlank()) {
            sql.append(" AND last_evt.status = :lastWebhookStatus");
            params.put("lastWebhookStatus", f.lastWebhookStatus().toUpperCase().trim());
        }
        if (f.lastWebhookDateFrom() != null && !f.lastWebhookDateFrom().isBlank()) {
            sql.append(" AND last_evt.created_at >= CAST(:lastWebhookDateFrom AS TIMESTAMPTZ)");
            params.put("lastWebhookDateFrom", f.lastWebhookDateFrom().trim());
        }
        if (f.lastWebhookDateTo() != null && !f.lastWebhookDateTo().isBlank()) {
            sql.append(" AND last_evt.created_at <= CAST(:lastWebhookDateTo AS TIMESTAMPTZ)");
            params.put("lastWebhookDateTo", f.lastWebhookDateTo().trim());
        }
    }

    public PageResult findPayments(SearchFilters f) {
        StringBuilder sql = new StringBuilder(
            "SELECT p.id::text AS id, p.gateway_payment_id AS external_id, p.subscription_id::text AS subscription_id, " +
            "p.customer_id::text AS customer_id, t.name AS customer_name, au.email AS customer_email, " +
            "pm.id::text AS module_id, pm.name AS module_name, " + TYPE_EXPRESSION + " AS type, pms.billing_cycle AS billing_cycle, " +
            "p.gateway AS gateway, p.payment_method AS payment_method, p.amount AS amount, p.currency AS currency, " +
            "p.status AS status, p.created_at::text AS created_at, " +
            "last_evt.event_type AS last_event_type, last_evt.status AS last_event_status, last_evt.created_at::text AS last_event_at, " +
            "EXISTS (SELECT 1 FROM payment_webhook_events e WHERE e.payment_id = p.id AND e.status = 'FAILED') AS has_webhook_error, " +
            "COUNT(*) OVER() AS total_count " +
            BASE_FROM + "WHERE 1=1"
        );

        Map<String, Object> params = new LinkedHashMap<>();
        appendCommonFilters(sql, params, f);

        sql.append(" ORDER BY p.created_at DESC LIMIT :size OFFSET :offset");
        params.put("size", f.size());
        params.put("offset", f.offset());

        NativeQuery<PaymentListItemTO> query = databaseQuery.nativeQuery(em, sql.toString(), PaymentListItemTO.class);
        params.forEach(query::setParameter);
        List<PaymentListItemTO> rows = query.getResultList();

        long total = rows.isEmpty() ? 0L : rows.get(0).totalCount();
        return new PageResult(rows, total);
    }

    public PaymentsSummaryTO fetchPaymentsSummary(SearchFilters f) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*)::bigint AS total_count, " +
            "COUNT(*) FILTER (WHERE p.status = 'PAID')::bigint AS paid_count, " +
            "COUNT(*) FILTER (WHERE p.status IN ('PENDING','PROCESSING','AUTHORIZED'))::bigint AS pending_count, " +
            "COUNT(*) FILTER (WHERE p.status IN ('FAILED','CANCELLED'))::bigint AS failed_count, " +
            "COUNT(*) FILTER (WHERE p.status IN ('OVERDUE','EXPIRED'))::bigint AS overdue_count, " +
            "COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'PAID'), 0) AS amount_received, " +
            "COALESCE(SUM(p.amount) FILTER (WHERE p.status IN ('PENDING','PROCESSING','AUTHORIZED')), 0) AS amount_pending, " +
            "COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM payment_webhook_events e WHERE e.payment_id = p.id AND e.status = 'FAILED'))::bigint AS with_webhook_error_count " +
            BASE_FROM + "WHERE 1=1"
        );

        Map<String, Object> params = new LinkedHashMap<>();
        appendCommonFilters(sql, params, f);

        NativeQuery<PaymentsSummaryTO> query = databaseQuery.nativeQuery(em, sql.toString(), PaymentsSummaryTO.class);
        params.forEach(query::setParameter);
        return query.getOptionalResult().orElseThrow();
    }

    public Optional<PaymentDetailTO> findPaymentDetail(String id) {
        String sql =
            "SELECT p.id::text AS id, p.gateway_payment_id AS external_id, p.customer_id::text AS customer_id, " +
            "t.name AS customer_name, au.email AS customer_email, " + TYPE_EXPRESSION + " AS type, " +
            "p.amount AS amount, p.currency AS currency, p.fee_amount AS fee_amount, p.net_amount AS net_amount, " +
            "p.status AS status, p.gateway AS gateway, p.payment_method AS payment_method, " +
            "p.gateway_customer_id AS gateway_customer_id, p.gateway_payment_id AS gateway_payment_id, " +
            "p.gateway_subscription_id AS gateway_subscription_id, p.metadata::text AS metadata, p.checkout_url AS checkout_url, " +
            "p.created_at::text AS created_at, p.updated_at::text AS updated_at, " +
            "pms.id::text AS subscription_id, pm.id::text AS subscription_module_id, pm.name AS subscription_module_name, " +
            "pl.id::text AS subscription_plan_id, pl.name AS subscription_plan_name, pms.billing_cycle AS subscription_billing_cycle, " +
            "pms.status AS subscription_status, pms.started_at::text AS subscription_started_at, pms.expires_at::text AS subscription_expires_at " +
            BASE_FROM + "WHERE p.id::text = :id";

        return databaseQuery.nativeQuery(em, sql, PaymentDetailTO.class)
                .setParameter("id", id)
                .getOptionalResult();
    }

    public List<PaymentEventTO> findPaymentEvents(String paymentId, String eventType, String status, String dateFrom, String dateTo) {
        StringBuilder sql = new StringBuilder(
            "SELECT id::text AS id, payment_id::text AS payment_id, gateway AS gateway, external_event_id AS external_event_id, " +
            "event_type AS event_type, status AS status, error_message AS error_message, attempts AS attempts, " +
            "created_at::text AS created_at, processed_at::text AS processed_at " +
            "FROM payment_webhook_events WHERE payment_id::text = :paymentId"
        );

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("paymentId", paymentId);

        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = :eventType");
            params.put("eventType", eventType.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.put("status", status.toUpperCase().trim());
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            sql.append(" AND created_at >= CAST(:dateFrom AS TIMESTAMPTZ)");
            params.put("dateFrom", dateFrom.trim());
        }
        if (dateTo != null && !dateTo.isBlank()) {
            sql.append(" AND created_at <= CAST(:dateTo AS TIMESTAMPTZ)");
            params.put("dateTo", dateTo.trim());
        }

        sql.append(" ORDER BY created_at DESC");

        NativeQuery<PaymentEventTO> query = databaseQuery.nativeQuery(em, sql.toString(), PaymentEventTO.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    public Optional<PaymentEventDetailTO> findPaymentEventDetail(String eventId) {
        String sql =
            "SELECT id::text AS id, payment_id::text AS payment_id, gateway AS gateway, external_event_id AS external_event_id, " +
            "event_type AS event_type, status AS status, error_message AS error_message, attempts AS attempts, " +
            "created_at::text AS created_at, processed_at::text AS processed_at, payload::text AS payload " +
            "FROM payment_webhook_events WHERE id::text = :eventId";

        return databaseQuery.nativeQuery(em, sql, PaymentEventDetailTO.class)
                .setParameter("eventId", eventId)
                .getOptionalResult();
    }
}
