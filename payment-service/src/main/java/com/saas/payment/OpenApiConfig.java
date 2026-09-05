package com.saas.payment;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Metadados gerais expostos em /q/openapi e /q/swagger-ui.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SaaS Platform — Payment Service",
        version = "1.0.0",
        description = "Camada de abstração financeira sobre os gateways de pagamento (Stripe, Asaas e futuros). " +
            "Cria cobranças, checkouts e assinaturas recorrentes, processa webhooks de forma idempotente e " +
            "notifica o subscription-service sobre mudanças de status. Não decide nenhuma regra de assinatura/plano " +
            "— isso continua sendo domínio exclusivo do subscription-service. As rotas /api/v1/payments/** exigem " +
            "JWT do Supabase (chamadas do subscription-service); /api/v1/webhooks/** são públicas, validadas por " +
            "assinatura/token próprio de cada gateway."
    )
)
public class OpenApiConfig {
}
