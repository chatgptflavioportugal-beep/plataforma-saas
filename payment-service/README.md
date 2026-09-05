# payment-service

Camada de abstração financeira entre a plataforma e os gateways de pagamento (Stripe, Asaas — e
futuramente Mercado Pago, Pagar.me etc.). Cria cobranças, checkouts e assinaturas recorrentes,
processa webhooks de forma idempotente e converte o status/vocabulário de cada gateway para um
modelo interno único. **Não decide nenhuma regra de assinatura/plano/módulo** — isso continua
sendo domínio exclusivo do `subscription-service`; este serviço só informa fatos financeiros.

Chamado exclusivamente pelo `subscription-service` (nunca diretamente pelo frontend) em
`/api/v1/payments/**`. Os endpoints `/api/v1/webhooks/**` são chamados diretamente pelos gateways.

## Arquitetura

```
Frontend → subscription-service → payment-service → PaymentProvider
                                                        ├── StripePaymentProvider
                                                        └── AsaasPaymentProvider

Stripe/Asaas → payment-service (/api/v1/webhooks/**) → subscription-service
```

A escolha do gateway é resolvida por `PaymentProviderResolver` a partir do enum `PaymentGateway`
informado em cada requisição — não existe `if/else` por gateway espalhado pela aplicação. Adicionar
um gateway novo é implementar `PaymentProvider` e registrar como bean CDI (`@ApplicationScoped`);
nenhum outro ponto do domínio muda.

### Estrutura de pacotes

```
resource/     PaymentResource (/api/v1/payments/**, @Authenticated) e
              PaymentWebhookResource (/api/v1/webhooks/**, público)
negocio/      PaymentNegocio (cobrança/checkout/assinatura/cancelamento/reembolso),
              WebhookNegocio (validação + idempotência + aplicação do evento),
              SubscriptionNotifier (notifica o subscription-service)
provider/     PaymentProvider (abstração), PaymentProviderResolver,
              stripe/ (StripePaymentProvider, StripeStatusMapper),
              asaas/  (AsaasPaymentProvider, AsaasApiClient, AsaasStatusMapper)
dao/          PaymentDAO, PaymentWebhookEventDAO (SQL nativo, EntityManager)
entity/       Payment, PaymentWebhookEvent
dto/          request/ e response/ — nunca expõem tipo de SDK de gateway
enums/        PaymentGateway, PaymentStatus, PaymentMethod (vocabulário interno)
exception/    PaymentProviderException e especializações + ExceptionMapper
repository/   SubscriptionServiceRepository (RestClient → subscription-service)
```

### Status interno

`PaymentStatus` (`PENDING`, `PROCESSING`, `AUTHORIZED`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED`,
`EXPIRED`, `OVERDUE`) é independente de gateway. Cada `PaymentProvider` converte o vocabulário do
gateway para este enum (`StripeStatusMapper`/`AsaasStatusMapper`) — nenhuma outra classe do serviço
compara strings de status de gateway diretamente.

## Endpoints

Todas as rotas abaixo (exceto webhooks) exigem `Authorization: Bearer <JWT Supabase>` — o mesmo
token que o subscription-service recebeu do frontend, repassado adiante (mesmo padrão de
propagação usado entre os demais microsserviços da plataforma). `tenantId`/`subscriptionId`/
`customerId` chegam explícitos no corpo: este serviço não resolve membership de tenant.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/payments` | Cria cobrança avulsa (idempotente por `idempotencyKey`) |
| POST | `/api/v1/payments/checkout` | Cria sessão de checkout hospedada pelo gateway (`checkout_url`) |
| POST | `/api/v1/payments/subscriptions` | Cria cobrança recorrente (assinatura no gateway) |
| GET | `/api/v1/payments/{id}` | Consulta um pagamento |
| POST | `/api/v1/payments/{id}/cancel` | Cancela cobrança avulsa não capturada |
| POST | `/api/v1/payments/{id}/cancel-subscription` | Cancela a recorrência no gateway |
| POST | `/api/v1/payments/{id}/refund` | Solicita reembolso (total ou parcial) |
| POST | `/api/v1/webhooks/stripe` | Recebe eventos do Stripe (público, validado por assinatura) |
| POST | `/api/v1/webhooks/asaas` | Recebe eventos do Asaas (público, validado por token) |

Documentação interativa completa em `/q/swagger-ui` (dev).

## Fluxo de pagamento

1. Usuário contrata um módulo → `subscription-service` inicia o processo de assinatura.
2. `subscription-service` chama `POST /api/v1/payments` (ou `/checkout`, ou `/subscriptions`),
   repassando o gateway escolhido (`STRIPE`/`ASAAS`) e os dados financeiros.
3. `payment-service` resolve o `PaymentProvider` correspondente e delega a operação.
4. O resultado (id externo, status, taxa, valor líquido) é persistido em `payments` e, se
   `subscriptionId` foi informado, o `subscription-service` é notificado via
   `POST /api/v1/internal/payments/{subscriptionId}/status` (autenticado por segredo
   compartilhado `X-Internal-Token`, já que não há JWT de usuário nesse ponto — a mudança pode
   nascer de um webhook do gateway).

## Fluxo de webhook

1. Stripe/Asaas chamam `/api/v1/webhooks/{stripe|asaas}`.
2. O `PaymentProvider` correspondente valida a autenticidade (`Stripe-Signature` via SDK; token
   compartilhado no header `asaas-access-token` para o Asaas — o Asaas não assina o payload).
3. O evento é interpretado (`WebhookParseResult`) e uma tentativa de inserção idempotente é feita
   em `payment_webhook_events` (`INSERT ... ON CONFLICT (gateway, external_event_id) DO NOTHING`).
   Se já existia, o evento é ignorado — **nunca reprocessado**.
4. O pagamento correspondente (localizado por `gateway_payment_id` ou `gateway_subscription_id`) é
   atualizado e o `subscription-service` é notificado, se aplicável.

## Variáveis de ambiente

```bash
# Banco (mesmo Supabase compartilhado pelos demais microsserviços)
QUARKUS_DATASOURCE_JDBC_URL=
QUARKUS_DATASOURCE_USERNAME=
QUARKUS_DATASOURCE_PASSWORD=

# JWT Supabase — valida as chamadas do subscription-service
SUPABASE_JWT_JWKS_URL=
SUPABASE_JWT_ISSUER=

# Notificação ao subscription-service
SUBSCRIPTION_SERVICE_URL=http://localhost:8085
INTERNAL_SERVICE_TOKEN=          # mesmo valor configurado no subscription-service

# Stripe (Dashboard → Developers → API keys / Webhooks)
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Asaas (Configurações → Integrações → API)
ASAAS_API_KEY=...
ASAAS_WEBHOOK_SECRET=...         # token definido por você no cadastro do webhook no painel Asaas
ASAAS_API_URL=https://sandbox.asaas.com/api/v3
```

Nunca commitar nenhum destes valores — ver `.env.example` na raiz do repositório.

## Exemplo de uso

```bash
curl -X POST http://localhost:8088/api/v1/payments \
  -H "Authorization: Bearer $SUPABASE_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionId": "b7e5...",
    "customerId": "a1c2...",
    "gateway": "STRIPE",
    "amount": 99.90,
    "currency": "BRL",
    "description": "Assinatura módulo PDF — mensal",
    "idempotencyKey": "sub-b7e5-2026-09"
  }'
```

## Como executar localmente

Via `docker-compose` (padrão do projeto):

```bash
docker compose up payment-service
```

Diretamente (dev mode, hot-reload) — requer instalar `platform-database-quarkus` no repositório
Maven local primeiro:

```bash
cd libs/platform-database-quarkus && mvn install -N -DskipTests && cd ../../payment-service
mvn quarkus:dev
```

## Como executar os testes

Testes unitários (não usam `@QuarkusTest` nem banco — mocks/stubs para os Providers e DAOs, como
pedido: nenhum teste de negócio depende da API real do Stripe/Asaas):

```bash
cd payment-service
mvn test
```

Cobrem: `PaymentProviderResolver` (seleção de gateway, incluindo "gateway inexistente"),
`StripeStatusMapper`/`AsaasStatusMapper` (conversão de status), `PaymentNegocioImpl` (criação,
idempotência, reembolso) e `WebhookNegocioImpl` (assinatura inválida, evento duplicado, evento sem
pagamento correspondente, aplicação normal do evento).

## Limitações conhecidas desta primeira versão

- **Asaas exige `cpfCnpj` do cliente em produção** — não há esse dado disponível em nenhum DTO
  hoje (não é domínio do payment-service). `AsaasPaymentProvider.resolveOrCreateCustomer` envia
  `null`; para produção, o subscription-service precisa repassar o CPF/CNPJ (ex.: via
  `metadata`) antes de habilitar o Asaas fora do sandbox.
- **Dunning/cobrança recorrente vencida**: `ProfileModuleSubscriptionNegocio.applyPaymentStatus`
  (subscription-service) só automatiza `PENDING_PAYMENT → ACTIVE` (pagamento aprovado) e
  `PENDING_PAYMENT → CANCELED` (falha/cancelamento/expiração antes da primeira cobrança).
  `OVERDUE`/`FAILED` numa assinatura já `ACTIVE` (renovação recorrente que falhou) não dispara
  nenhuma transição automática ainda — fica para quando houver uma política de dunning definida.
- **Troca de valor de assinatura Stripe** (`changeSubscription`) assume que a assinatura tem
  exatamente um item de cobrança — cenários com múltiplos itens por assinatura não são cobertos.
