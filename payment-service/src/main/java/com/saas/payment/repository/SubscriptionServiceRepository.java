package com.saas.payment.repository;

import com.saas.payment.enums.PaymentGateway;
import com.saas.payment.enums.PaymentStatus;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.Instant;
import java.util.UUID;

/**
 * Cliente REST para notificar o subscription-service sobre mudança de status
 * de pagamento (aprovado, recusado, vencido) — o subscription-service é quem
 * decide o que fazer com a assinatura (ex.: PENDING_PAYMENT → ACTIVE/CANCELED),
 * o Payment Service só informa o fato financeiro.
 *
 * Sem messaging (Kafka/RabbitMQ) ativo na plataforma — REST síncrono é o
 * único mecanismo de comunicação entre serviços hoje (ver
 * auth-service.SubscriptionServiceRepository). Diferente daquela chamada,
 * esta não repassa o Authorization do usuário: a notificação nasce de um
 * webhook do gateway, que não carrega nenhum JWT de usuário. Autenticação
 * feita via segredo compartilhado (X-Internal-Token), na mesma linha do
 * padrão já documentado para chamadas server-to-server internas (ver
 * app.internal.service-token).
 */
@RegisterRestClient(configKey = "subscription-service-api")
public interface SubscriptionServiceRepository {

    @POST
    @Path("/api/v1/internal/payments/{subscriptionId}/status")
    void notifyPaymentStatus(@HeaderParam("X-Internal-Token") String internalToken,
                              @PathParam("subscriptionId") UUID subscriptionId,
                              PaymentStatusNotification notification);

    record PaymentStatusNotification(
            UUID paymentId,
            PaymentGateway gateway,
            String gatewayPaymentId,
            PaymentStatus status,
            Instant occurredAt
    ) {
    }
}
