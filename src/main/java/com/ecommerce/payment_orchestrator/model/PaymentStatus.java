package com.ecommerce.payment_orchestrator.model;

public enum PaymentStatus {
    CREATED,
    PAYMENT_PENDING,
    SETTLED,
    FAILED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case CREATED -> next == PAYMENT_PENDING || next == FAILED;
            case PAYMENT_PENDING -> next == SETTLED || next == FAILED || next == REFUNDED;
            case SETTLED, FAILED, REFUNDED -> false; // Terminal states: cannot transition
        };
    }
}