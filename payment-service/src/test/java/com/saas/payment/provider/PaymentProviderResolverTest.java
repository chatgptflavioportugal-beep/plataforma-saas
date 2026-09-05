package com.saas.payment.provider;

import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.exception.PaymentValidationException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes puros (sem @QuarkusTest — não precisam de container CDI nem de
 * banco) do ponto único de decisão "qual gateway usar".
 */
class PaymentProviderResolverTest {

    @SuppressWarnings("unchecked")
    private PaymentProviderResolver resolverWith(PaymentProvider... providers) {
        Instance<PaymentProvider> instance = mock(Instance.class);
        when(instance.stream()).thenAnswer(invocation -> Stream.of(providers));
        PaymentProviderResolver resolver = new PaymentProviderResolver();
        resolver.providers = instance;
        return resolver;
    }

    private PaymentProvider providerFor(PaymentGateway gateway) {
        PaymentProvider provider = mock(PaymentProvider.class);
        when(provider.getGateway()).thenReturn(gateway);
        return provider;
    }

    @Test
    void resolvesTheProviderMatchingTheGateway() {
        PaymentProvider stripe = providerFor(PaymentGateway.STRIPE);
        PaymentProvider asaas = providerFor(PaymentGateway.ASAAS);
        var resolver = resolverWith(stripe, asaas);

        assertEquals(stripe, resolver.resolve(PaymentGateway.STRIPE));
        assertEquals(asaas, resolver.resolve(PaymentGateway.ASAAS));
    }

    @Test
    void throwsValidationExceptionWhenGatewayIsNull() {
        var resolver = resolverWith();
        assertThrows(PaymentValidationException.class, () -> resolver.resolve(null));
    }

    @Test
    void throwsValidationExceptionWhenNoProviderMatches() {
        var resolver = resolverWith(providerFor(PaymentGateway.STRIPE));
        assertThrows(PaymentValidationException.class, () -> resolver.resolve(PaymentGateway.ASAAS));
    }

    @Test
    void addingANewGatewayNeverRequiresChangingTheResolver() {
        // Documenta a garantia central do desenho: qualquer PaymentProvider novo
        // (Mercado Pago, Pagar.me, ...) só precisa existir como bean — nenhum
        // if/else é necessário aqui.
        List<PaymentGateway> allGateways = List.of(PaymentGateway.values());
        PaymentProvider[] providers = allGateways.stream().map(this::providerFor).toArray(PaymentProvider[]::new);
        var resolver = resolverWith(providers);

        for (PaymentGateway gateway : allGateways) {
            assertEquals(gateway, resolver.resolve(gateway).getGateway());
        }
    }
}
