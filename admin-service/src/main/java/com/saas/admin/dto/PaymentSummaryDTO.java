package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * Indicadores agregados de pagamentos, respeitando os mesmos filtros da
 * listagem (ver AdminPaymentDAO.fetchPaymentsSummary). Agrupamentos por
 * status (não há um indicador por valor exato do enum PaymentStatus):
 *   paid     = PAID
 *   pending  = PENDING, PROCESSING, AUTHORIZED (aguardando confirmação do gateway)
 *   failed   = FAILED, CANCELLED
 *   overdue  = OVERDUE, EXPIRED
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PaymentSummaryDTO(
        long total,
        long paid,
        long pending,
        long failed,
        long overdue,
        BigDecimal amountReceived,
        BigDecimal amountPending,
        long withWebhookError) {
}
