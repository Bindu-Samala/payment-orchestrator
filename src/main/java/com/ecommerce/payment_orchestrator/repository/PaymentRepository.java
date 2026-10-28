package com.ecommerce.payment_orchestrator.repository;



import com.ecommerce.payment_orchestrator.model.PaymentStatus;
import com.ecommerce.payment_orchestrator.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentTransaction, String> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentTransaction> findByGatewayTransactionId(String gatewayTransactionId);
    
    // Finds transactions stuck in pending state before the given timestamp
    List<PaymentTransaction> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime threshold);
}
