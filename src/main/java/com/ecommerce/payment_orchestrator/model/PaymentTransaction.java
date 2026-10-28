package com.ecommerce.payment_orchestrator.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_idemp_key", columnList = "idempotencyKey", unique = true),
    @Index(name = "idx_status_created", columnList = "status, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    private String id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(unique = true)
    private String gatewayTransactionId;

    @Column(nullable = false)
    private Long amountInCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public PaymentTransaction(String id, String orderId, String idempotencyKey, Long amountInCents) {
        this.id = id;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.amountInCents = amountInCents;
        this.status = PaymentStatus.CREATED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void transitionTo(PaymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Illegal state transition from %s to %s", this.status, newStatus)
            );
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
}