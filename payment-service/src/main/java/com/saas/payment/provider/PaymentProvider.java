package com.saas.payment.provider;

import com.saas.payment.dto.request.CreateCheckoutRequest;
import com.saas.payment.dto.request.CreatePaymentRequest;
import com.saas.payment.dto.request.CreateSubscriptionPaymentRequest;
import com.saas.payment.enums.PaymentGateway;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Abstração central do Payment Service: qualquer gateway suportado
 * (Stripe, Asaas, e futuramente Mercado Pago/Pagar.me) implementa esta
 * interface. A regra de negócio (PaymentNegocio) nunca conhece SDK ou
 * detalhe de um gateway específico — só conversa com PaymentProvider.
 *
 * Adicionar um novo gateway é implementar esta interface e registrar como
 * bean CDI ({@code @ApplicationScoped}) — {@link PaymentProviderResolver}
 * descobre a implementação automaticamente por {@link #getGateway()}, sem
 * exigir alteração em nenhum outro ponto do domínio.
 */
public interface PaymentProvider {

    PaymentGateway getGateway();

    /** Cria uma cobrança avulsa (não recorrente). */
    PaymentProviderResult createPayment(CreatePaymentRequest request);

    /** Cria uma sessão de checkout hospedada pelo gateway; o resultado traz {@code checkoutUrl}. */
    PaymentProviderResult createCheckout(CreateCheckoutRequest request);

    /** Cria uma cobrança recorrente (assinatura no gateway). */
    PaymentProviderResult createSubscription(CreateSubscriptionPaymentRequest request);

    /** Cancela uma cobrança avulsa ainda não capturada/paga, quando o gateway suportar. */
    PaymentProviderResult cancelPayment(String gatewayPaymentId);

    /** Cancela a recorrência no gateway (a assinatura em si, não uma cobrança pontual). */
    PaymentProviderResult cancelSubscription(String gatewaySubscriptionId);

    /**
     * Altera a recorrência (troca de valor/plano) quando o gateway suportar.
     * Implementações que não suportam devem lançar
     * {@link com.saas.payment.exception.PaymentValidationException}.
     */
    PaymentProviderResult changeSubscription(String gatewaySubscriptionId, BigDecimal newAmount, Map<String, Object> metadata);

    PaymentProviderResult refund(String gatewayPaymentId, BigDecimal amount, String reason);

    PaymentProviderResult getPayment(String gatewayPaymentId);

    PaymentProviderResult getSubscription(String gatewaySubscriptionId);

    /** Valida a assinatura/autenticidade do webhook. Deve ser chamado antes de {@link #parseWebhookEvent}. */
    boolean isValidWebhookSignature(String rawPayload, Map<String, String> headers);

    /** Interpreta o evento já validado, convertendo o vocabulário do gateway para o interno. */
    WebhookParseResult parseWebhookEvent(String rawPayload, Map<String, String> headers);
}
