package com.saas.admin.negocio.impl;

import com.saas.admin.dto.PaymentDetailDTO;
import com.saas.admin.dto.PaymentEventDetailDTO;
import com.saas.admin.dto.PaymentEventListItemDTO;
import com.saas.admin.dto.PaymentPageDTO;
import com.saas.admin.dto.PaymentSummaryDTO;
import com.saas.admin.dto.PaymentTimelineEventDTO;

import java.util.List;
import java.util.Optional;

/**
 * Consultas administrativas de pagamentos (listagem, indicadores, detalhe,
 * histórico de webhooks e timeline). 100% leitura — não há nenhuma ação de
 * escrita nesta área (ver AdminPaymentDAO). Persistência isolada em
 * AdminPaymentDAO.
 */
public interface AdminPaymentNegocio {

    PaymentSummaryDTO getSummary(
            String id, String externalId, String subscriptionId, String customerId, String search,
            String moduleId, String gateway, String paymentMethod, String type, String billingCycle,
            String status, String amountMin, String amountMax, String dateFrom, String dateTo,
            Boolean hasWebhookError, String lastWebhookStatus, String lastWebhookDateFrom, String lastWebhookDateTo);

    PaymentPageDTO listPayments(
            String id, String externalId, String subscriptionId, String customerId, String search,
            String moduleId, String gateway, String paymentMethod, String type, String billingCycle,
            String status, String amountMin, String amountMax, String dateFrom, String dateTo,
            Boolean hasWebhookError, String lastWebhookStatus, String lastWebhookDateFrom, String lastWebhookDateTo,
            int page, int size);

    Optional<PaymentDetailDTO> getDetail(String id);

    List<PaymentEventListItemDTO> listEvents(String paymentId, String eventType, String status, String dateFrom, String dateTo);

    Optional<PaymentEventDetailDTO> getEventDetail(String eventId);

    List<PaymentTimelineEventDTO> getTimeline(String paymentId);
}
