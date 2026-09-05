package com.saas.payment.provider;

import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.exception.PaymentValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Resolve o {@link PaymentProvider} responsável por um {@link PaymentGateway}.
 * Ponto único de decisão de "qual gateway usar" — o resto da aplicação nunca
 * faz {@code if (gateway == STRIPE) ... else if (gateway == ASAAS) ...}.
 * Adicionar um gateway novo (Mercado Pago, Pagar.me) é só implementar
 * PaymentProvider e registrar como bean CDI; nenhuma mudança aqui.
 */
@ApplicationScoped
public class PaymentProviderResolver {

    @Inject
    Instance<PaymentProvider> providers;

    public PaymentProvider resolve(PaymentGateway gateway) {
        if (gateway == null) {
            throw new PaymentValidationException("gateway é obrigatório");
        }
        return providers.stream()
                .filter(provider -> provider.getGateway() == gateway)
                .findFirst()
                .orElseThrow(() -> new PaymentValidationException("Gateway não suportado: " + gateway));
    }
}
