package com.magambell.server.payment.app.service;

import static com.magambell.server.payment.domain.enums.PaymentStatus.CANCELLED;
import static com.magambell.server.payment.domain.enums.PaymentStatus.FAILED;
import static com.magambell.server.payment.domain.enums.PaymentStatus.PAID;
import static com.magambell.server.payment.domain.enums.PaymentStatus.READY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.magambell.server.common.exception.InvalidRequestException;
import com.magambell.server.notification.app.port.in.NotificationUseCase;
import com.magambell.server.order.domain.entity.Order;
import com.magambell.server.payment.app.port.in.PaymentCompleteUseCase;
import com.magambell.server.payment.app.port.in.request.PaymentRedirectPaidServiceRequest;
import com.magambell.server.payment.app.port.in.request.PortOneWebhookServiceRequest;
import com.magambell.server.payment.app.port.out.PaymentQueryPort;
import com.magambell.server.payment.app.port.out.PortOnePort;
import com.magambell.server.payment.domain.entity.Payment;
import com.magambell.server.payment.domain.enums.PaymentCompletionType;
import com.magambell.server.payment.domain.enums.PaymentStatus;
import com.magambell.server.payment.infra.PortOnePaymentResponse;
import com.magambell.server.stock.app.port.in.StockUseCase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PortOnePort portOnePort;
    @Mock private PaymentQueryPort paymentQueryPort;
    @Mock private StockUseCase stockUseCase;
    @Mock private NotificationUseCase notificationUseCase;
    @Mock private PaymentCompleteUseCase paymentCompleteUseCase;

    @InjectMocks private PaymentService paymentService;

    // ── redirectPaid ──────────────────────────────────────────────────────

    @Test
    @DisplayName("redirectPaid: WEBHOOK으로 이미 처리된 결제는 조용히 무시한다")
    void redirectPaid_alreadyProcessedViaWebhook_returnsEarly() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(PaymentCompletionType.WEBHOOK);

        paymentService.redirectPaid(new PaymentRedirectPaidServiceRequest("pid-1"));

        verify(notificationUseCase, never()).notifyPaidOrder(any());
    }

    @Test
    @DisplayName("redirectPaid: 이미 결제 완료 상태이면 무시한다")
    void redirectPaid_alreadyPaid_returnsEarly() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(true);

        paymentService.redirectPaid(new PaymentRedirectPaidServiceRequest("pid-1"));

        verify(notificationUseCase, never()).notifyPaidOrder(any());
    }

    @Test
    @DisplayName("redirectPaid: PortOne 상태가 PAID가 아니면 예외를 던진다")
    void redirectPaid_portOneStatusNotPaid_throwsException() {
        PortOnePaymentResponse response = makeResponse(FAILED, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(false);

        assertThatThrownBy(() -> paymentService.redirectPaid(new PaymentRedirectPaidServiceRequest("pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("redirectPaid: 금액이 일치하지 않으면 예외를 던진다")
    void redirectPaid_amountMismatch_throwsException() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(false);
        when(payment.getAmount()).thenReturn(99999);

        assertThatThrownBy(() -> paymentService.redirectPaid(new PaymentRedirectPaidServiceRequest("pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("redirectPaid: 정상 결제 완료 시 paid()를 호출하고 알림을 발송한다")
    void redirectPaid_success_callsPaidAndSendsNotification() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);
        Order order = mock(Order.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(false);
        when(payment.getAmount()).thenReturn(10000);
        when(payment.getOrder()).thenReturn(order);
        when(order.getOrderGoodsList()).thenReturn(List.of());
        when(payment.getOrderStoreOwner()).thenReturn(Set.of());

        paymentService.redirectPaid(new PaymentRedirectPaidServiceRequest("pid-1"));

        verify(payment).paid(eq(response), eq(PaymentCompletionType.REDIRECT));
        verify(notificationUseCase).notifyPaidOrder(any());
    }

    // ── webhook PAID ──────────────────────────────────────────────────────

    @Test
    @DisplayName("webhook PAID: REDIRECT로 이미 처리된 결제는 무시한다")
    void webhookPaid_alreadyProcessedViaRedirect_returnsEarly() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(PaymentCompletionType.REDIRECT);

        paymentService.webhook(webhookRequest(PAID, "pid-1"));

        verify(notificationUseCase, never()).notifyPaidOrder(any());
    }

    @Test
    @DisplayName("webhook PAID: 이미 결제 완료 상태이면 무시한다")
    void webhookPaid_alreadyPaid_returnsEarly() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(true);

        paymentService.webhook(webhookRequest(PAID, "pid-1"));

        verify(notificationUseCase, never()).notifyPaidOrder(any());
    }

    @Test
    @DisplayName("webhook PAID: PortOne 상태가 PAID가 아니면 예외를 던진다")
    void webhookPaid_portOneStatusNotPaid_throwsException() {
        PortOnePaymentResponse response = makeResponse(CANCELLED, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(false);

        assertThatThrownBy(() -> paymentService.webhook(webhookRequest(PAID, "pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("webhook PAID: 금액이 일치하지 않으면 예외를 던진다")
    void webhookPaid_amountMismatch_throwsException() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(false);
        when(payment.getAmount()).thenReturn(5000);

        assertThatThrownBy(() -> paymentService.webhook(webhookRequest(PAID, "pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("webhook PAID: 정상 처리 시 WEBHOOK 타입으로 paid()를 호출하고 알림을 발송한다")
    void webhookPaid_success_callsPaidAndSendsNotification() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);
        Order order = mock(Order.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentCompletionType()).thenReturn(null);
        when(payment.isPaid()).thenReturn(false);
        when(payment.getAmount()).thenReturn(10000);
        when(payment.getOrder()).thenReturn(order);
        when(order.getOrderGoodsList()).thenReturn(List.of());
        when(payment.getOrderStoreOwner()).thenReturn(Set.of());

        paymentService.webhook(webhookRequest(PAID, "pid-1"));

        verify(payment).paid(eq(response), eq(PaymentCompletionType.WEBHOOK));
        verify(notificationUseCase).notifyPaidOrder(any());
    }

    // ── webhook CANCELLED ─────────────────────────────────────────────────

    @Test
    @DisplayName("webhook CANCELLED: 이미 취소된 결제는 무시한다")
    void webhookCancelled_alreadyCancelled_returnsEarly() {
        PortOnePaymentResponse response = makeResponse(CANCELLED, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentStatus()).thenReturn(CANCELLED);

        paymentService.webhook(webhookRequest(CANCELLED, "pid-1"));

        verify(payment, never()).hookCancel();
    }

    @Test
    @DisplayName("webhook CANCELLED: 결제가 완료되지 않은 상태에서 취소 요청 시 예외를 던진다")
    void webhookCancelled_notPaid_throwsException() {
        PortOnePaymentResponse response = makeResponse(CANCELLED, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentStatus()).thenReturn(READY);
        when(payment.isPaid()).thenReturn(false);

        assertThatThrownBy(() -> paymentService.webhook(webhookRequest(CANCELLED, "pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("webhook CANCELLED: PortOne 상태가 CANCELLED가 아니면 예외를 던진다")
    void webhookCancelled_portOneStatusNotCancelled_throwsException() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentStatus()).thenReturn(PAID);
        when(payment.isPaid()).thenReturn(true);

        assertThatThrownBy(() -> paymentService.webhook(webhookRequest(CANCELLED, "pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("webhook CANCELLED: 정상 취소 처리 시 hookCancel()을 호출하고 재고를 복구한다")
    void webhookCancelled_success_callsHookCancelAndRestoresStock() {
        PortOnePaymentResponse response = makeResponse(CANCELLED, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentStatus()).thenReturn(PAID);
        when(payment.isPaid()).thenReturn(true);

        paymentService.webhook(webhookRequest(CANCELLED, "pid-1"));

        verify(payment).hookCancel();
        verify(stockUseCase).restoreStockIfNecessary(payment);
    }

    // ── webhook FAILED ────────────────────────────────────────────────────

    @Test
    @DisplayName("webhook FAILED: 이미 처리된 결제(READY 아님)이면 예외를 던진다")
    void webhookFailed_alreadyProcessed_throwsException() {
        PortOnePaymentResponse response = makeResponse(FAILED, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentStatus()).thenReturn(PAID);

        assertThatThrownBy(() -> paymentService.webhook(webhookRequest(FAILED, "pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("webhook FAILED: PortOne 상태가 FAILED가 아니면 예외를 던진다")
    void webhookFailed_portOneStatusNotFailed_throwsException() {
        PortOnePaymentResponse response = makeResponse(PAID, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentStatus()).thenReturn(READY);

        assertThatThrownBy(() -> paymentService.webhook(webhookRequest(FAILED, "pid-1")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("webhook FAILED: 정상 실패 처리 시 failed()를 호출하고 재고를 복구한다")
    void webhookFailed_success_callsFailedAndRestoresStock() {
        PortOnePaymentResponse response = makeResponse(FAILED, "ORD-001", 10000);
        Payment payment = mock(Payment.class);

        when(portOnePort.getPaymentById("pid-1")).thenReturn(response);
        when(paymentQueryPort.findByMerchantUidWithLockAndRelations("ORD-001")).thenReturn(payment);
        when(payment.getPaymentStatus()).thenReturn(READY);

        paymentService.webhook(webhookRequest(FAILED, "pid-1"));

        verify(payment).failed();
        verify(stockUseCase).restoreStockIfNecessary(payment);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private PortOnePaymentResponse makeResponse(PaymentStatus status, String id, int total) {
        return new PortOnePaymentResponse(
                id,
                "txn-" + id,
                "test-merchant",
                status,
                OffsetDateTime.now(),
                new PortOnePaymentResponse.Method("card", null, null),
                new PortOnePaymentResponse.Amount(total)
        );
    }

    private PortOneWebhookServiceRequest webhookRequest(PaymentStatus status, String paymentId) {
        return new PortOneWebhookServiceRequest(status, null, null, paymentId, null, null);
    }
}
