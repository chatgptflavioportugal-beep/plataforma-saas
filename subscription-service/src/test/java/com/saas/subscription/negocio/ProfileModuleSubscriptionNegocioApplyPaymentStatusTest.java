package com.saas.subscription.negocio;

import com.saas.subscription.dao.ProfileModuleSubscriptionDAO;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes puros (sem @QuarkusTest) da tradução da notificação de pagamento do
 * payment-service em transição de status de profile_module_subscriptions —
 * ver InternalPaymentResource.
 */
class ProfileModuleSubscriptionNegocioApplyPaymentStatusTest {

    private ProfileModuleSubscriptionDAO subscriptionDAO;
    private ProfileModuleSubscriptionNegocioImpl negocio;

    @BeforeEach
    void setUp() {
        subscriptionDAO = mock(ProfileModuleSubscriptionDAO.class);
        negocio = new ProfileModuleSubscriptionNegocioImpl();
        negocio.subscriptionDAO = subscriptionDAO;
    }

    @Test
    void paidMovesFromPendingPaymentToActive() {
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptionDAO.updateStatusIfCurrent(subscriptionId, "PENDING_PAYMENT", "ACTIVE")).thenReturn(1);

        negocio.applyPaymentStatus(subscriptionId, "PAID");

        verify(subscriptionDAO).updateStatusIfCurrent(subscriptionId, "PENDING_PAYMENT", "ACTIVE");
    }

    @Test
    void failedMovesFromPendingPaymentToCanceled() {
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptionDAO.updateStatusIfCurrent(subscriptionId, "PENDING_PAYMENT", "CANCELED")).thenReturn(1);

        negocio.applyPaymentStatus(subscriptionId, "FAILED");

        verify(subscriptionDAO).updateStatusIfCurrent(subscriptionId, "PENDING_PAYMENT", "CANCELED");
    }

    @Test
    void cancelledAndExpiredAlsoMoveToCanceled() {
        UUID subscriptionId = UUID.randomUUID();

        negocio.applyPaymentStatus(subscriptionId, "CANCELLED");
        negocio.applyPaymentStatus(subscriptionId, "EXPIRED");

        verify(subscriptionDAO, org.mockito.Mockito.times(2))
                .updateStatusIfCurrent(eq(subscriptionId), eq("PENDING_PAYMENT"), eq("CANCELED"));
    }

    @Test
    void statusesWithoutAnAutomaticTransitionDoNotTouchTheDatabase() {
        UUID subscriptionId = UUID.randomUUID();

        negocio.applyPaymentStatus(subscriptionId, "OVERDUE");
        negocio.applyPaymentStatus(subscriptionId, "PROCESSING");
        negocio.applyPaymentStatus(subscriptionId, "REFUNDED");

        verify(subscriptionDAO, never()).updateStatusIfCurrent(eq(subscriptionId), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void requiresSubscriptionIdAndStatus() {
        assertThrows(BadRequestException.class, () -> negocio.applyPaymentStatus(null, "PAID"));
        assertThrows(BadRequestException.class, () -> negocio.applyPaymentStatus(UUID.randomUUID(), null));
        assertThrows(BadRequestException.class, () -> negocio.applyPaymentStatus(UUID.randomUUID(), " "));
    }
}
