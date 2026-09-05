-- Migration: 0055_payment_webhook_events_payment_link.sql
--
-- payment_webhook_events não tinha nenhuma referência ao pagamento a que
-- pertence — não havia como responder "quais eventos aconteceram com este
-- pagamento?", que é a base da área administrativa de Pagamentos.
--
-- payment_id fica NULL quando o evento não corresponde a nenhum pagamento
-- conhecido (ver status = 'IGNORED') — o evento é preservado, não descartado.
-- Linhas antigas (anteriores a esta migration) ficam com payment_id NULL: não
-- há como reconstruir essa associação retroativamente sem reprocessar os
-- payloads originais.
--
-- status substitui o uso isolado do booleano `processed` por um vocabulário
-- compatível com o que WebhookNegocioImpl realmente produz:
--   RECEIVED  — gravado, ainda não processado (nunca deveria sobreviver além
--               da própria transação de processamento)
--   PROCESSED — aplicado com sucesso a um Payment
--   FAILED    — encontrou o Payment mas a aplicação lançou exceção
--   IGNORED   — nenhum Payment correspondente foi encontrado
--
-- error_message e attempts sustentam troubleshooting (seção 23) e o
-- tratamento de reentregas duplicadas pelo gateway (seção 30) sem introduzir
-- retry/reprocessamento manual.
--
-- DOWN:
-- DROP INDEX IF EXISTS idx_payment_webhook_events_payment_id;
-- DROP INDEX IF EXISTS idx_payment_webhook_events_status;
-- DROP INDEX IF EXISTS idx_payments_status;
-- DROP INDEX IF EXISTS idx_payments_gateway;
-- DROP INDEX IF EXISTS idx_payments_created_at;
-- ALTER TABLE payment_webhook_events
--   DROP COLUMN IF EXISTS payment_id,
--   DROP COLUMN IF EXISTS status,
--   DROP COLUMN IF EXISTS error_message,
--   DROP COLUMN IF EXISTS attempts;

ALTER TABLE payment_webhook_events
    ADD COLUMN IF NOT EXISTS payment_id    UUID REFERENCES payments(id),
    ADD COLUMN IF NOT EXISTS status        TEXT NOT NULL DEFAULT 'RECEIVED',
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS attempts      INT  NOT NULL DEFAULT 1;

UPDATE payment_webhook_events
    SET status = CASE WHEN processed THEN 'PROCESSED' ELSE 'RECEIVED' END
    WHERE status = 'RECEIVED';

CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_payment_id ON payment_webhook_events (payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_status     ON payment_webhook_events (status);

CREATE INDEX IF NOT EXISTS idx_payments_status     ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_gateway     ON payments (gateway);
CREATE INDEX IF NOT EXISTS idx_payments_created_at  ON payments (created_at);
