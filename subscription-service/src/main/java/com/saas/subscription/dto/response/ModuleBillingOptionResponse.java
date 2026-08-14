package com.saas.subscription.dto.response;

import java.util.UUID;

/**
 * Serializado em snake_case (property-naming-strategy global) — mesmo contrato
 * já usado por /api/v1/public/modules/billing-options. servicesJson/
 * availablePlansJson mantêm-se String (JSON já serializado pelo Postgres) para
 * não alterar o contrato consumido pelo frontend.
 */
public record ModuleBillingOptionResponse(
    UUID moduleId,
    String moduleName,
    String moduleSlug,
    String moduleDescription,
    String iconPath,
    String servicesJson,
    String availablePlansJson
) {}
