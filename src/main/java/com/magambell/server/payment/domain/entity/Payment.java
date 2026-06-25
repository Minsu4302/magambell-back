package com.magambell.server.payment.domain.entity;

import static com.magambell.server.payment.app.service.PaymentService.MERCHANT_UID_PREFIX;

import com.magambell.server.common.BaseTimeEntity;
import com.magambell.server.order.domain.entity.Order;
import com.magambell.server.payment.app.port.in.dto.CreatePaymentDTO;
import com.magambell.server.payment.domain.enums.PaymentCompletionType;
import com.magambell.server.payment.domain.enums.PaymentStatus;
import com.magambell.server.payment.infra.PortOnePaymentResponse;
import com.magambell.server.user.domain.entity.User;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Payment extends BaseTimeEntity {

    @Id
    @Tsid
    @Column(name = "payment_id")
    private Long id;

    private String transactionId;
    private String merchantUid;
    private String payType;
    private String easyPayProvider;
    private String cardName;
    private Integer amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    private PaymentCompletionType paymentCompletionType;
    private LocalDateTime paidAt;
    private String failReason;
    private String cancelReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Builder(access = AccessLevel.PRIVATE)
    private Payment(final String merchantUid, final String payType,
                    final String easyPayProvider, final String cardName,
                    final Integer amount, final PaymentStatus paymentStatus,
                    final String failReason,
                    final String cancelReason) {
        this.merchantUid = merchantUid;
        this.payType = payType;
        this.easyPayProvider = easyPayProvider;
        this.cardName = cardName;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
        this.failReason = failReason;
        this.cancelReason = cancelReason;
    }

    public static Payment create(final CreatePaymentDTO dto) {
        Payment payment = Payment.builder()
                .merchantUid(MERCHANT_UID_PREFIX + dto.order().getId().toString())
                .amount(dto.amount())
                .paymentStatus(dto.paymentStatus())
                .build();

        payment.addOrder(dto.order());

        return payment;
    }

    public void addOrder(final Order order) {
        this.order = order;
        order.addPayment(this);
    }

    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }

    public void paid(final PortOnePaymentResponse response, final PaymentCompletionType paymentCompletionType) {
        this.paymentStatus = PaymentStatus.PAID;
        this.paymentCompletionType = paymentCompletionType;
        this.transactionId = response.transactionId();
        this.paidAt = response.paidAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        this.payType = response.method().type();
        this.easyPayProvider = response.method().provider();
        this.order.paid();
    }

    public void cancel(final PaymentCompletionType paymentCompletionType) {
        this.paymentCompletionType = paymentCompletionType;
        this.paymentStatus = PaymentStatus.CANCELLED;
    }

    public void hookCancel() {
        cancel(PaymentCompletionType.WEBHOOK);
        this.order.cancelled(PaymentCompletionType.WEBHOOK);
    }

    public void failed() {
        this.paymentStatus = PaymentStatus.FAILED;
        this.order.failed();
    }

    /**
     * 테스트용 결제의 transactionId 설정
     * @param testTransactionId 테스트용 transactionId (예: "test_ORDER_xxx")
     */
    public void setTestTransactionId(final String testTransactionId) {
        this.transactionId = testTransactionId;
    }

    public Set<User> getOrderStoreOwner() {
        return this.order.getOrderStoreOwner();
    }
}
