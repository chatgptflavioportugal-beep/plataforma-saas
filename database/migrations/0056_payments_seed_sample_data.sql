-- Migration: 0056_payments_seed_sample_data.sql
--
-- DADOS DE EXEMPLO (DEV/TESTE) — NÃO É SCHEMA.
--
-- payments/payment_webhook_events estavam vazias em desenvolvimento (nenhum
-- fluxo real ainda cria Payment — ver comentário em PaymentResource/
-- SubscriptionServiceRepository), o que impedia testar visualmente a tela
-- Admin > Pagamentos. Este script insere 6 pagamentos fictícios cobrindo os
-- principais cenários (pago avulso, pago recorrente, pendente recorrente —
-- espelhando a assinatura PENDING_PAYMENT real "Whatsapp/Contabilidade",
-- falho com erro de webhook, reembolsado após pago, e vencido) e o histórico
-- de webhook de cada um.
--
-- Todos os IDs usam o prefixo fixo aXXXXXXX.../eXXXXXXX... para serem fáceis
-- de identificar e remover. Dois pagamentos (ids ...0002 e ...0005) são
-- ligados a uma assinatura e tenant reais já existentes no banco
-- (152a05ec-6f2c-436e-978d-10e3ba5a41fc); o pagamento ...0003 é ligado à
-- assinatura real 848ced97-... que já está PENDING_PAYMENT, para refletir o
-- mesmo estado dos dois lados. O pagamento ...0004 usa um customer_id que
-- não existe em `tenants`, de propósito, para validar que a tela trata bem
-- um cliente não resolvido (mostra "—" em vez de quebrar).
--
-- SEGURO POR PADRÃO: só insere se a tabela payments ainda não tiver nenhuma
-- dessas linhas (ON CONFLICT DO NOTHING) — rodar de novo não duplica nada.
-- NÃO aplique este script em produção.
--
-- DOWN:
-- DELETE FROM payment_webhook_events WHERE payment_id IN (
--   'a0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002',
--   'a0000000-0000-4000-8000-000000000003','a0000000-0000-4000-8000-000000000004',
--   'a0000000-0000-4000-8000-000000000005','a0000000-0000-4000-8000-000000000006');
-- DELETE FROM payments WHERE id IN (
--   'a0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002',
--   'a0000000-0000-4000-8000-000000000003','a0000000-0000-4000-8000-000000000004',
--   'a0000000-0000-4000-8000-000000000005','a0000000-0000-4000-8000-000000000006');

INSERT INTO payments (
    id, subscription_id, customer_id, gateway, gateway_payment_id, gateway_customer_id, gateway_subscription_id,
    payment_method, amount, currency, status, fee_amount, net_amount, created_at, updated_at
) VALUES
    -- 1. Pago, avulso (PIX/Asaas), cliente real "Madelene"
    ('a0000000-0000-4000-8000-000000000001', NULL, '73047557-a324-46d3-b4e8-2f6b7bdb34be',
     'ASAAS', 'pay_seed_0001', 'cus_seed_0001', NULL,
     'PIX', 49.90, 'BRL', 'PAID', 1.50, 48.40, now() - interval '20 days', now() - interval '20 days' + interval '5 minutes'),

    -- 2. Pago, recorrente mensal (cartão/Stripe), ligado à assinatura real do Imobiliário (Flávio)
    ('a0000000-0000-4000-8000-000000000002', '152a05ec-6f2c-436e-978d-10e3ba5a41fc', 'c1238e40-0aeb-42b4-84ef-f7975b66cd1f',
     'STRIPE', 'pi_seed_0002', 'cus_seed_0002', 'sub_seed_0002',
     'CREDIT_CARD', 89.90, 'BRL', 'PAID', 3.20, 86.70, now() - interval '45 days', now() - interval '45 days' + interval '10 minutes'),

    -- 3. Pendente, recorrente (boleto/Asaas), ligado à assinatura real PENDING_PAYMENT do Whatsapp (Contabilidade)
    ('a0000000-0000-4000-8000-000000000003', '848ced97-1374-4e68-95f9-62ae35268ebc', 'a1a6081a-8c3c-48c8-afb3-561b18caf952',
     'ASAAS', 'pay_seed_0003', 'cus_seed_0003', 'sub_seed_0003',
     'BOLETO', 129.90, 'BRL', 'PENDING', NULL, NULL, now() - interval '2 days', now() - interval '2 days'),

    -- 4. Falhou, avulso (cartão/Stripe), customer_id proposital sem tenant correspondente
    ('a0000000-0000-4000-8000-000000000004', NULL, '99999999-9999-4999-8999-999999999999',
     'STRIPE', 'pi_seed_0004', 'cus_seed_0004', NULL,
     'CREDIT_CARD', 199.00, 'BRL', 'FAILED', NULL, NULL, now() - interval '5 days', now() - interval '5 days' + interval '3 minutes'),

    -- 5. Reembolsado após pago, mesma assinatura recorrente do pagamento 2 (mês diferente)
    ('a0000000-0000-4000-8000-000000000005', '152a05ec-6f2c-436e-978d-10e3ba5a41fc', 'c1238e40-0aeb-42b4-84ef-f7975b66cd1f',
     'STRIPE', 'pi_seed_0005', 'cus_seed_0002', 'sub_seed_0002',
     'CREDIT_CARD', 89.90, 'BRL', 'REFUNDED', 3.20, 86.70, now() - interval '15 days', now() - interval '14 days'),

    -- 6. Vencido, avulso (boleto/Asaas), segunda compra da "Madelene"
    ('a0000000-0000-4000-8000-000000000006', NULL, '73047557-a324-46d3-b4e8-2f6b7bdb34be',
     'ASAAS', 'pay_seed_0006', 'cus_seed_0001', NULL,
     'BOLETO', 59.90, 'BRL', 'OVERDUE', NULL, NULL, now() - interval '10 days', now() - interval '3 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO payment_webhook_events (
    id, payment_id, gateway, external_event_id, event_type, payload, processed, status, error_message, attempts, created_at, processed_at
) VALUES
    -- histórico do pagamento 1 (pago avulso)
    ('e0000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000001', 'ASAAS', 'evt_seed_0001_1', 'PAYMENT_CREATED',
     '{"id":"evt_seed_0001_1","event":"PAYMENT_CREATED","payment":{"id":"pay_seed_0001"}}', true, 'PROCESSED', NULL, 1,
     now() - interval '20 days', now() - interval '20 days'),
    ('e0000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000001', 'ASAAS', 'evt_seed_0001_2', 'PAYMENT_CONFIRMED',
     '{"id":"evt_seed_0001_2","event":"PAYMENT_CONFIRMED","payment":{"id":"pay_seed_0001","status":"RECEIVED"}}', true, 'PROCESSED', NULL, 1,
     now() - interval '20 days' + interval '5 minutes', now() - interval '20 days' + interval '5 minutes'),

    -- histórico do pagamento 2 (pago recorrente)
    ('e0000000-0000-4000-8000-000000000021', 'a0000000-0000-4000-8000-000000000002', 'STRIPE', 'evt_seed_0002_1', 'payment_intent.created',
     '{"id":"evt_seed_0002_1","type":"payment_intent.created","data":{"object":{"id":"pi_seed_0002"}}}', true, 'PROCESSED', NULL, 1,
     now() - interval '45 days', now() - interval '45 days'),
    ('e0000000-0000-4000-8000-000000000022', 'a0000000-0000-4000-8000-000000000002', 'STRIPE', 'evt_seed_0002_2', 'payment_intent.succeeded',
     '{"id":"evt_seed_0002_2","type":"payment_intent.succeeded","data":{"object":{"id":"pi_seed_0002","amount":8990}}}', true, 'PROCESSED', NULL, 1,
     now() - interval '45 days' + interval '2 minutes', now() - interval '45 days' + interval '2 minutes'),
    ('e0000000-0000-4000-8000-000000000023', 'a0000000-0000-4000-8000-000000000002', 'STRIPE', 'evt_seed_0002_3', 'invoice.paid',
     '{"id":"evt_seed_0002_3","type":"invoice.paid","data":{"object":{"subscription":"sub_seed_0002"}}}', true, 'PROCESSED', NULL, 1,
     now() - interval '45 days' + interval '10 minutes', now() - interval '45 days' + interval '10 minutes'),

    -- histórico do pagamento 3 (pendente recorrente — ainda sem confirmação)
    ('e0000000-0000-4000-8000-000000000031', 'a0000000-0000-4000-8000-000000000003', 'ASAAS', 'evt_seed_0003_1', 'PAYMENT_CREATED',
     '{"id":"evt_seed_0003_1","event":"PAYMENT_CREATED","payment":{"id":"pay_seed_0003"}}', true, 'PROCESSED', NULL, 1,
     now() - interval '2 days', now() - interval '2 days'),

    -- histórico do pagamento 4 (falhou — o único com erro de webhook)
    ('e0000000-0000-4000-8000-000000000041', 'a0000000-0000-4000-8000-000000000004', 'STRIPE', 'evt_seed_0004_1', 'payment_intent.created',
     '{"id":"evt_seed_0004_1","type":"payment_intent.created","data":{"object":{"id":"pi_seed_0004"}}}', true, 'PROCESSED', NULL, 1,
     now() - interval '5 days', now() - interval '5 days'),
    ('e0000000-0000-4000-8000-000000000042', 'a0000000-0000-4000-8000-000000000004', 'STRIPE', 'evt_seed_0004_2', 'payment_intent.payment_failed',
     '{"id":"evt_seed_0004_2","type":"payment_intent.payment_failed","data":{"object":{"id":"pi_seed_0004","last_payment_error":{"message":"Cartao recusado pela operadora (dado de teste)"}}}}',
     false, 'FAILED', 'Cartao recusado pela operadora (dado de teste)', 1,
     now() - interval '5 days' + interval '3 minutes', now() - interval '5 days' + interval '3 minutes'),

    -- histórico do pagamento 5 (pago e depois reembolsado — histórico anterior preservado)
    ('e0000000-0000-4000-8000-000000000051', 'a0000000-0000-4000-8000-000000000005', 'STRIPE', 'evt_seed_0005_1', 'payment_intent.succeeded',
     '{"id":"evt_seed_0005_1","type":"payment_intent.succeeded","data":{"object":{"id":"pi_seed_0005","amount":8990}}}', true, 'PROCESSED', NULL, 1,
     now() - interval '15 days', now() - interval '15 days'),
    ('e0000000-0000-4000-8000-000000000052', 'a0000000-0000-4000-8000-000000000005', 'STRIPE', 'evt_seed_0005_2', 'charge.refunded',
     '{"id":"evt_seed_0005_2","type":"charge.refunded","data":{"object":{"id":"pi_seed_0005","amount_refunded":8990}}}', true, 'PROCESSED', NULL, 1,
     now() - interval '14 days', now() - interval '14 days'),

    -- histórico do pagamento 6 (venceu sem pagamento)
    ('e0000000-0000-4000-8000-000000000061', 'a0000000-0000-4000-8000-000000000006', 'ASAAS', 'evt_seed_0006_1', 'PAYMENT_CREATED',
     '{"id":"evt_seed_0006_1","event":"PAYMENT_CREATED","payment":{"id":"pay_seed_0006"}}', true, 'PROCESSED', NULL, 1,
     now() - interval '10 days', now() - interval '10 days'),
    ('e0000000-0000-4000-8000-000000000062', 'a0000000-0000-4000-8000-000000000006', 'ASAAS', 'evt_seed_0006_2', 'PAYMENT_OVERDUE',
     '{"id":"evt_seed_0006_2","event":"PAYMENT_OVERDUE","payment":{"id":"pay_seed_0006"}}', true, 'PROCESSED', NULL, 1,
     now() - interval '3 days', now() - interval '3 days')
ON CONFLICT (gateway, external_event_id) DO NOTHING;
