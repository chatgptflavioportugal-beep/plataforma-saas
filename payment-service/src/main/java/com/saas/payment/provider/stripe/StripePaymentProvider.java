package com.saas.payment.provider.stripe;

import com.saas.payment.dto.request.CreateCheckoutRequest;
import com.saas.payment.dto.request.CreatePaymentRequest;
import com.saas.payment.dto.request.CreateSubscriptionPaymentRequest;
import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.exception.PaymentProviderException;
import com.saas.payment.exception.PaymentUnavailableException;
import com.saas.payment.exception.PaymentValidationException;
import com.saas.payment.provider.PaymentProvider;
import com.saas.payment.provider.PaymentProviderResult;
import com.saas.payment.provider.WebhookParseResult;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Product;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação Stripe do {@link PaymentProvider}. Nenhum tipo do SDK do
 * Stripe (PaymentIntent, Subscription, Event, ...) escapa desta classe — o
 * restante da aplicação só enxerga {@link PaymentProviderResult}/{@link WebhookParseResult}.
 */
@ApplicationScoped
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger LOG = Logger.getLogger(StripePaymentProvider.class);

    @ConfigProperty(name = "payment.stripe.secret-key")
    String secretKey;

    @ConfigProperty(name = "payment.stripe.webhook-secret")
    String webhookSecret;

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.STRIPE;
    }

    @Override
    public PaymentProviderResult createPayment(CreatePaymentRequest request) {
        try {
            String customerId = resolveOrCreateCustomer(request.customerId().toString(), request.gatewayCustomerId());

            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(toCents(request.amount()))
                    .setCurrency(currencyOrDefault(request.currency()))
                    .setCustomer(customerId)
                    .setDescription(request.description())
                    .putMetadata("customerId", request.customerId().toString());
            if (request.subscriptionId() != null) {
                builder.putMetadata("subscriptionId", request.subscriptionId().toString());
            }

            if (request.paymentMethodId() != null && !request.paymentMethodId().isBlank()) {
                builder.setPaymentMethod(request.paymentMethodId())
                        .setConfirm(true)
                        .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build());
            } else {
                builder.setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build());
            }

            PaymentIntent intent = PaymentIntent.create(builder.build(), requestOptions(request.idempotencyKey()));
            return toResult(intent);
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult createCheckout(CreateCheckoutRequest request) {
        try {
            String customerId = resolveOrCreateCustomer(request.customerId().toString(), request.gatewayCustomerId());

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomer(customerId)
                    .setSuccessUrl(request.successUrl())
                    .setCancelUrl(request.cancelUrl())
                    .putMetadata("customerId", request.customerId().toString())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currencyOrDefault(request.currency()))
                                    .setUnitAmount(toCents(request.amount()))
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(Optional.ofNullable(request.description()).orElse("Cobrança"))
                                            .build())
                                    .build())
                            .build());
            if (request.subscriptionId() != null) {
                paramsBuilder.putMetadata("subscriptionId", request.subscriptionId().toString());
            }

            Session session = Session.create(paramsBuilder.build(), requestOptions(request.idempotencyKey()));
            return new PaymentProviderResult(
                    session.getPaymentIntent(),
                    session.getCustomer(),
                    null,
                    com.saas.payment.enums.PaymentStatus.PENDING,
                    com.saas.payment.enums.PaymentMethod.UNKNOWN,
                    request.amount(),
                    currencyOrDefault(request.currency()),
                    null,
                    null,
                    session.getUrl()
            );
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult createSubscription(CreateSubscriptionPaymentRequest request) {
        try {
            String customerId = resolveOrCreateCustomer(request.customerId().toString(), request.gatewayCustomerId());
            // Diferente do Checkout Session, o item de uma Subscription não aceita
            // product_data inline — só a referência a um Product já existente. Como o
            // catálogo de planos vive no subscription-service (não no Stripe), criamos
            // um Product dedicado a cada assinatura para descrever a cobrança.
            String productId = Product.create(
                    ProductCreateParams.builder()
                            .setName(Optional.ofNullable(request.description()).orElse("Assinatura"))
                            .build(),
                    requestOptions(null)
            ).getId();

            SubscriptionCreateParams.Item.PriceData.Recurring.Interval interval = "ANNUAL".equalsIgnoreCase(request.billingCycle())
                    ? SubscriptionCreateParams.Item.PriceData.Recurring.Interval.YEAR
                    : SubscriptionCreateParams.Item.PriceData.Recurring.Interval.MONTH;

            SubscriptionCreateParams.Builder builder = SubscriptionCreateParams.builder()
                    .setCustomer(customerId)
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPriceData(SubscriptionCreateParams.Item.PriceData.builder()
                                    .setCurrency(currencyOrDefault(request.currency()))
                                    .setUnitAmount(toCents(request.amount()))
                                    .setRecurring(SubscriptionCreateParams.Item.PriceData.Recurring.builder()
                                            .setInterval(interval)
                                            .build())
                                    .setProduct(productId)
                                    .build())
                            .build())
                    .putMetadata("customerId", request.customerId().toString());
            if (request.subscriptionId() != null) {
                builder.putMetadata("subscriptionId", request.subscriptionId().toString());
            }
            if (request.paymentMethodId() != null && !request.paymentMethodId().isBlank()) {
                builder.setDefaultPaymentMethod(request.paymentMethodId());
            }

            Subscription subscription = Subscription.create(builder.build(), requestOptions(request.idempotencyKey()));
            return new PaymentProviderResult(
                    null,
                    customerId,
                    subscription.getId(),
                    StripeStatusMapper.fromSubscriptionStatus(subscription.getStatus()),
                    com.saas.payment.enums.PaymentMethod.UNKNOWN,
                    request.amount(),
                    currencyOrDefault(request.currency()),
                    null,
                    null,
                    null
            );
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult cancelPayment(String gatewayPaymentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(gatewayPaymentId, requestOptions(null));
            PaymentIntent canceled = intent.cancel(requestOptions(null));
            return toResult(canceled);
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult cancelSubscription(String gatewaySubscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(gatewaySubscriptionId, requestOptions(null));
            Subscription canceled = subscription.cancel(SubscriptionCancelParams.builder().build(), requestOptions(null));
            return new PaymentProviderResult(
                    null, canceled.getCustomer(), canceled.getId(),
                    StripeStatusMapper.fromSubscriptionStatus(canceled.getStatus()),
                    com.saas.payment.enums.PaymentMethod.UNKNOWN, null, canceled.getCurrency(), null, null, null
            );
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult changeSubscription(String gatewaySubscriptionId, BigDecimal newAmount, Map<String, Object> metadata) {
        try {
            Subscription subscription = Subscription.retrieve(gatewaySubscriptionId, requestOptions(null));
            if (subscription.getItems().getData().isEmpty()) {
                throw new PaymentValidationException("Assinatura Stripe sem itens: " + gatewaySubscriptionId);
            }
            var currentItem = subscription.getItems().getData().get(0);
            var currentPrice = currentItem.getPrice();

            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(currentItem.getId())
                            .setPriceData(SubscriptionUpdateParams.Item.PriceData.builder()
                                    .setCurrency(currentPrice.getCurrency())
                                    .setProduct(currentPrice.getProduct())
                                    .setUnitAmount(toCents(newAmount))
                                    .setRecurring(SubscriptionUpdateParams.Item.PriceData.Recurring.builder()
                                            .setInterval(SubscriptionUpdateParams.Item.PriceData.Recurring.Interval.valueOf(
                                                    currentPrice.getRecurring().getInterval().toUpperCase()))
                                            .build())
                                    .build())
                            .build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .build();

            Subscription updated = subscription.update(params, requestOptions(null));
            return new PaymentProviderResult(
                    null, updated.getCustomer(), updated.getId(),
                    StripeStatusMapper.fromSubscriptionStatus(updated.getStatus()),
                    com.saas.payment.enums.PaymentMethod.UNKNOWN, newAmount, currentPrice.getCurrency(), null, null, null
            );
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult refund(String gatewayPaymentId, BigDecimal amount, String reason) {
        try {
            RefundCreateParams.Builder builder = RefundCreateParams.builder().setPaymentIntent(gatewayPaymentId);
            if (amount != null) {
                builder.setAmount(toCents(amount));
            }
            Refund refund = Refund.create(builder.build(), requestOptions(null));
            com.saas.payment.enums.PaymentStatus status = switch (refund.getStatus()) {
                case "succeeded" -> com.saas.payment.enums.PaymentStatus.REFUNDED;
                case "pending" -> com.saas.payment.enums.PaymentStatus.PROCESSING;
                case "canceled" -> com.saas.payment.enums.PaymentStatus.CANCELLED;
                default -> com.saas.payment.enums.PaymentStatus.FAILED;
            };
            return new PaymentProviderResult(
                    gatewayPaymentId, null, null, status, com.saas.payment.enums.PaymentMethod.UNKNOWN,
                    amount != null ? amount : fromCents(refund.getAmount()), refund.getCurrency(), null, null, null
            );
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult getPayment(String gatewayPaymentId) {
        try {
            PaymentIntentRetrieveParams params = PaymentIntentRetrieveParams.builder()
                    .addExpand("latest_charge.balance_transaction")
                    .build();
            PaymentIntent intent = PaymentIntent.retrieve(gatewayPaymentId, params, requestOptions(null));
            return toResult(intent);
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public PaymentProviderResult getSubscription(String gatewaySubscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(gatewaySubscriptionId, requestOptions(null));
            return new PaymentProviderResult(
                    null, subscription.getCustomer(), subscription.getId(),
                    StripeStatusMapper.fromSubscriptionStatus(subscription.getStatus()),
                    com.saas.payment.enums.PaymentMethod.UNKNOWN, null, subscription.getCurrency(), null, null, null
            );
        } catch (StripeException e) {
            throw translate(e);
        }
    }

    @Override
    public boolean isValidWebhookSignature(String rawPayload, Map<String, String> headers) {
        String signature = headers.get("Stripe-Signature");
        if (signature == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        try {
            Webhook.constructEvent(rawPayload, signature, webhookSecret);
            return true;
        } catch (SignatureVerificationException e) {
            LOG.warn("Assinatura de webhook Stripe inválida: " + e.getMessage());
            return false;
        }
    }

    @Override
    public WebhookParseResult parseWebhookEvent(String rawPayload, Map<String, String> headers) {
        try {
            Event event = Webhook.constructEvent(rawPayload, headers.get("Stripe-Signature"), webhookSecret);
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = deserializer.getObject().orElse(null);

            String gatewayPaymentId = null;
            String gatewaySubscriptionId = null;
            com.saas.payment.enums.PaymentStatus status;

            if (stripeObject instanceof PaymentIntent intent) {
                gatewayPaymentId = intent.getId();
                status = StripeStatusMapper.fromEventType(event.getType(), StripeStatusMapper.fromPaymentIntentStatus(intent.getStatus()));
            } else if (stripeObject instanceof Subscription subscription) {
                gatewaySubscriptionId = subscription.getId();
                status = StripeStatusMapper.fromEventType(event.getType(), StripeStatusMapper.fromSubscriptionStatus(subscription.getStatus()));
            } else if (stripeObject instanceof Session session) {
                gatewayPaymentId = session.getPaymentIntent();
                status = StripeStatusMapper.fromEventType(event.getType(), com.saas.payment.enums.PaymentStatus.PAID);
            } else if (stripeObject instanceof Charge charge) {
                gatewayPaymentId = charge.getPaymentIntent();
                status = StripeStatusMapper.fromEventType(event.getType(), com.saas.payment.enums.PaymentStatus.PAID);
            } else {
                status = StripeStatusMapper.fromEventType(event.getType(), com.saas.payment.enums.PaymentStatus.PENDING);
            }

            return new WebhookParseResult(event.getId(), event.getType(), gatewayPaymentId, gatewaySubscriptionId, status);
        } catch (SignatureVerificationException e) {
            throw new PaymentValidationException("Assinatura de webhook Stripe inválida", e);
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private String resolveOrCreateCustomer(String customerId, String gatewayCustomerId) throws StripeException {
        if (gatewayCustomerId != null && !gatewayCustomerId.isBlank()) {
            return gatewayCustomerId;
        }
        Customer customer = Customer.create(
                CustomerCreateParams.builder().putMetadata("customerId", customerId).build(),
                requestOptions(null));
        return customer.getId();
    }

    private PaymentProviderResult toResult(PaymentIntent intent) {
        BigDecimal feeAmount = null;
        BigDecimal netAmount = null;
        try {
            Charge charge = intent.getLatestChargeObject();
            if (charge != null && charge.getBalanceTransactionObject() != null) {
                BalanceTransaction tx = charge.getBalanceTransactionObject();
                feeAmount = fromCents(tx.getFee());
                netAmount = fromCents(tx.getNet());
            }
        } catch (RuntimeException ignored) {
            // balance_transaction não expandido — segue sem taxa/valor líquido.
        }

        String methodType = intent.getPaymentMethodTypes() != null && !intent.getPaymentMethodTypes().isEmpty()
                ? intent.getPaymentMethodTypes().get(0)
                : null;

        return new PaymentProviderResult(
                intent.getId(),
                intent.getCustomer(),
                null,
                StripeStatusMapper.fromPaymentIntentStatus(intent.getStatus()),
                StripeStatusMapper.fromPaymentMethodType(methodType),
                fromCents(intent.getAmount()),
                intent.getCurrency(),
                feeAmount,
                netAmount,
                null
        );
    }

    private RequestOptions requestOptions(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder().setApiKey(secretKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        return builder.build();
    }

    private static long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private static BigDecimal fromCents(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }

    private static String currencyOrDefault(String currency) {
        return (currency == null || currency.isBlank()) ? "brl" : currency.toLowerCase();
    }

    private PaymentProviderException translate(StripeException e) {
        if (e instanceof CardException || e instanceof InvalidRequestException) {
            return new PaymentValidationException("Stripe rejeitou a requisição: " + e.getMessage(), e);
        }
        if (e instanceof ApiConnectionException || e instanceof RateLimitException || e instanceof ApiException) {
            return new PaymentUnavailableException("Stripe indisponível: " + e.getMessage(), e);
        }
        return new PaymentProviderException("Erro Stripe: " + e.getMessage(), e);
    }
}
