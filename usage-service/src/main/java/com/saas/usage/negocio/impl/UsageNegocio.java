package com.saas.usage.negocio.impl;

import com.saas.usage.dto.UsageIncrementResponse;
import com.saas.usage.dto.UsageSummaryItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras de negócio de controle de uso de módulos: incremento/verificação de quota,
 * consulta de consumo e auditoria de uso.
 */
public interface UsageNegocio {

    /**
     * Verifica a quota (claim {@code limits} do ModuleAccessToken) e, se ainda houver espaço,
     * incrementa o contador do dia. Quando o limite não está presente no token, o consumo é
     * ilimitado (nenhuma quota configurada para esse plano/módulo).
     */
    UsageIncrementResponse increment(UUID tenantId, UUID userId, String moduleSlug,
                                      String metricCode, long amount, Long limit);

    List<UsageSummaryItem> summary(UUID tenantId, String moduleSlug, String metricCode,
                                    LocalDate from, LocalDate to, int page, int pageSize);

    long countSummary(UUID tenantId, String moduleSlug, String metricCode, LocalDate from, LocalDate to);

    void audit(UUID tenantId, UUID userId, String moduleSlug, String metricCode,
               String action, Map<String, Object> metadata);
}
