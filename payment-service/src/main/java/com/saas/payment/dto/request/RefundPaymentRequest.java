package com.saas.payment.dto.request;

import java.math.BigDecimal;

/**
 * amount nulo solicita reembolso integral; informado, reembolso parcial
 * (quando o gateway suportar).
 */
public record RefundPaymentRequest(
        BigDecimal amount,
        String reason
) {
}
