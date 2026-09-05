-- Migration: 0054_payment_service_tables.sql
-- Tabelas do novo Payment Service: camada de abstração financeira sobre os
-- gateways de pagamento (Stripe, Asaas, futuros). O Payment Service é dono
-- exclusivamente da parte financeira (cobrança, status de pagamento,
-- identificadores externos do gateway); regras de assinatura/plano/módulo
-- continuam em profile_module_subscriptions (subscription-service).

-- DOWN:
-- DROP TABLE IF EXISTS payment_webhook_events;
-- DROP TABLE IF EXISTS payments;

CREATE TABLE IF NOT EXISTS payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id         UUID,
    customer_id             UUID NOT NULL,
    gateway                 TEXT NOT NULL,
    gateway_payment_id      TEXT,
    gateway_customer_id     TEXT,
    gateway_subscription_id TEXT,
    payment_method          TEXT,
    amount                  NUMERIC(12,2) NOT NULL,
    currency                TEXT NOT NULL DEFAULT 'BRL',
    status                  TEXT NOT NULL,
    fee_amount              NUMERIC(12,2),
    net_amount              NUMERIC(12,2),
    idempotency_key         TEXT,
    checkout_url            TEXT,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Uma cobrança pertence a um único gateway; o id externo é único por gateway
-- (nunca colidem entre Stripe e Asaas, mas o índice é composto por clareza e
-- porque protege contra reprocessamento indevido do mesmo gateway_payment_id).
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_gateway_payment_id
    ON payments (gateway, gateway_payment_id)
    WHERE gateway_payment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_idempotency_key
    ON payments (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_subscription_id ON payments (subscription_id);
CREATE INDEX IF NOT EXISTS idx_payments_customer_id ON payments (customer_id);
CREATE INDEX IF NOT EXISTS idx_payments_gateway_subscription_id ON payments (gateway, gateway_subscription_id);

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway            TEXT NOT NULL,
    external_event_id  TEXT NOT NULL,
    event_type         TEXT NOT NULL,
    payload            JSONB NOT NULL,
    processed          BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Garante idempotência: o mesmo evento do mesmo gateway nunca é processado
-- duas vezes (INSERT ... ON CONFLICT DO NOTHING antes de processar).
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_webhook_events_gateway_event
    ON payment_webhook_events (gateway, external_event_id);
