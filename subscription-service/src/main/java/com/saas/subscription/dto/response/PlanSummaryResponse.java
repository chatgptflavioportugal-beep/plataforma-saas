package com.saas.subscription.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Serializado em snake_case pela property-naming-strategy global (Jackson) —
 * mesmo contrato já usado publicamente por /api/v1/public/plans.
 * modulesJson mantém-se String (JSON já serializado pelo Postgres via
 * json_agg(...)::text) para não alterar o contrato consumido pelo frontend,
 * que hoje faz o parse desse campo no cliente.
 */
public record PlanSummaryResponse(
    UUID id,
    String name,
    String code,
    String description,
    BigDecimal priceMonthly,
    BigDecimal priceAnnual,
    BigDecimal discountAnnualPercent,
    int maxUsers,
    int maxAiRequestsMonth,
    int sortOrder,
    int version,
    String billingType,
    boolean isMostPopular,
    String planType,
    BigDecimal totalMonthlyPrice,
    BigDecimal totalAnnualMonthlyPrice,
    BigDecimal totalAnnualPrice,
    int moduleCount,
    String modulesJson
) {}
