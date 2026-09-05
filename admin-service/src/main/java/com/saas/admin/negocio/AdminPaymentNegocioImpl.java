package com.saas.admin.negocio;

import com.saas.admin.dao.AdminPaymentDAO;
import com.saas.admin.dto.PaymentDetailDTO;
import com.saas.admin.dto.PaymentEventDetailDTO;
import com.saas.admin.dto.PaymentEventListItemDTO;
import com.saas.admin.dto.PaymentListItemDTO;
import com.saas.admin.dto.PaymentPageDTO;
import com.saas.admin.dto.PaymentSubscriptionDTO;
import com.saas.admin.dto.PaymentSummaryDTO;
import com.saas.admin.dto.PaymentTimelineEventDTO;
import com.saas.admin.negocio.impl.AdminPaymentNegocio;
import com.saas.admin.to.PaymentDetailTO;
import com.saas.admin.to.PaymentEventDetailTO;
import com.saas.admin.to.PaymentEventTO;
import com.saas.admin.to.PaymentListItemTO;
import com.saas.admin.to.PaymentsSummaryTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AdminPaymentNegocioImpl implements AdminPaymentNegocio {

    @Inject
    AdminPaymentDAO dao;

    private AdminPaymentDAO.SearchFilters buildFilters(
            String id, String externalId, String subscriptionId, String customerId, String search,
            String moduleId, String gateway, String paymentMethod, String type, String billingCycle,
            String status, String amountMin, String amountMax, String dateFrom, String dateTo,
            Boolean hasWebhookError, String lastWebhookStatus, String lastWebhookDateFrom, String lastWebhookDateTo,
            int size, int offset) {
        return new AdminPaymentDAO.SearchFilters(
            id, externalId, subscriptionId, customerId, search, moduleId, gateway, paymentMethod, type, billingCycle,
            status, amountMin, amountMax, dateFrom, dateTo, hasWebhookError, lastWebhookStatus, lastWebhookDateFrom, lastWebhookDateTo,
            size, offset);
    }

    @Override
    public PaymentSummaryDTO getSummary(
            String id, String externalId, String subscriptionId, String customerId, String search,
            String moduleId, String gateway, String paymentMethod, String type, String billingCycle,
            String status, String amountMin, String amountMax, String dateFrom, String dateTo,
            Boolean hasWebhookError, String lastWebhookStatus, String lastWebhookDateFrom, String lastWebhookDateTo) {

        AdminPaymentDAO.SearchFilters filters = buildFilters(
            id, externalId, subscriptionId, customerId, search, moduleId, gateway, paymentMethod, type, billingCycle,
            status, amountMin, amountMax, dateFrom, dateTo, hasWebhookError, lastWebhookStatus, lastWebhookDateFrom, lastWebhookDateTo,
            0, 0);

        PaymentsSummaryTO row = dao.fetchPaymentsSummary(filters);
        return new PaymentSummaryDTO(
            row.totalCount(), row.paidCount(), row.pendingCount(), row.failedCount(), row.overdueCount(),
            row.amountReceived(), row.amountPending(), row.withWebhookErrorCount());
    }

    @Override
    public PaymentPageDTO listPayments(
            String id, String externalId, String subscriptionId, String customerId, String search,
            String moduleId, String gateway, String paymentMethod, String type, String billingCycle,
            String status, String amountMin, String amountMax, String dateFrom, String dateTo,
            Boolean hasWebhookError, String lastWebhookStatus, String lastWebhookDateFrom, String lastWebhookDateTo,
            int page, int size) {

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;

        AdminPaymentDAO.SearchFilters filters = buildFilters(
            id, externalId, subscriptionId, customerId, search, moduleId, gateway, paymentMethod, type, billingCycle,
            status, amountMin, amountMax, dateFrom, dateTo, hasWebhookError, lastWebhookStatus, lastWebhookDateFrom, lastWebhookDateTo,
            safeSize, safeOffset);

        AdminPaymentDAO.PageResult result = dao.findPayments(filters);
        List<PaymentListItemDTO> items = result.items().stream().map(this::toListItemDTO).toList();
        return new PaymentPageDTO(items, result.total(), page, safeSize);
    }

    private PaymentListItemDTO toListItemDTO(PaymentListItemTO row) {
        return new PaymentListItemDTO(
            row.id(), row.externalId(), row.subscriptionId(), row.customerId(), row.customerName(), row.customerEmail(),
            row.moduleId(), row.moduleName(), row.type(), row.billingCycle(), row.gateway(), row.paymentMethod(),
            row.amount(), row.currency(), row.status(), row.createdAt(),
            row.lastEventType(), row.lastEventStatus(), row.lastEventAt(), Boolean.TRUE.equals(row.hasWebhookError()));
    }

    @Override
    public Optional<PaymentDetailDTO> getDetail(String id) {
        return dao.findPaymentDetail(id).map(this::toDetailDTO);
    }

    private PaymentDetailDTO toDetailDTO(PaymentDetailTO row) {
        PaymentSubscriptionDTO subscription = row.subscriptionId() == null ? null : new PaymentSubscriptionDTO(
            row.subscriptionId(), row.subscriptionModuleId(), row.subscriptionModuleName(),
            row.subscriptionPlanId(), row.subscriptionPlanName(), row.subscriptionBillingCycle(),
            row.subscriptionStatus(), row.subscriptionStartedAt(), row.subscriptionExpiresAt());

        return new PaymentDetailDTO(
            row.id(), row.externalId(), row.customerId(), row.customerName(), row.customerEmail(), row.type(),
            row.amount(), row.currency(), row.feeAmount(), row.netAmount(), row.status(), row.gateway(), row.paymentMethod(),
            row.gatewayCustomerId(), row.gatewayPaymentId(), row.gatewaySubscriptionId(), row.metadata(), row.checkoutUrl(),
            row.createdAt(), row.updatedAt(), subscription);
    }

    @Override
    public List<PaymentEventListItemDTO> listEvents(String paymentId, String eventType, String status, String dateFrom, String dateTo) {
        return dao.findPaymentEvents(paymentId, eventType, status, dateFrom, dateTo).stream()
            .map(this::toEventListItemDTO)
            .toList();
    }

    private PaymentEventListItemDTO toEventListItemDTO(PaymentEventTO row) {
        return new PaymentEventListItemDTO(
            row.id(), row.gateway(), row.externalEventId(), row.eventType(), row.status(),
            row.errorMessage(), row.attempts(), row.createdAt(), row.processedAt());
    }

    @Override
    public Optional<PaymentEventDetailDTO> getEventDetail(String eventId) {
        return dao.findPaymentEventDetail(eventId).map(row -> new PaymentEventDetailDTO(
            row.id(), row.paymentId(), row.gateway(), row.externalEventId(), row.eventType(), row.status(),
            row.errorMessage(), row.attempts(), row.createdAt(), row.processedAt(), row.payload()));
    }

    @Override
    public List<PaymentTimelineEventDTO> getTimeline(String paymentId) {
        Optional<PaymentDetailTO> detail = dao.findPaymentDetail(paymentId);
        if (detail.isEmpty()) {
            return List.of();
        }

        List<PaymentTimelineEventDTO> timeline = new ArrayList<>();
        // "Pagamento criado" não carrega um status próprio: só temos o status
        // ATUAL do Payment (payments não guarda histórico de status), e
        // atribuí-lo à criação seria inventar um fato que o banco não registrou.
        timeline.add(new PaymentTimelineEventDTO("PAYMENT_CREATED", null, null, detail.get().createdAt(), null));

        dao.findPaymentEvents(paymentId, null, null, null, null).stream()
            .sorted((a, b) -> a.createdAt().compareTo(b.createdAt()))
            .forEach(event -> timeline.add(new PaymentTimelineEventDTO(
                "WEBHOOK_EVENT", event.eventType(), event.status(), event.createdAt(), event.processedAt())));

        return timeline;
    }
}
