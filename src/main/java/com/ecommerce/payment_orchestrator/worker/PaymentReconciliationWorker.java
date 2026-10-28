package com.ecommerce.payment_orchestrator.worker;


import com.ecommerce.payment_orchestrator.client.MockGatewayClient;
import com.ecommerce.payment_orchestrator.model.PaymentStatus;
import com.ecommerce.payment_orchestrator.model.PaymentTransaction;
import com.ecommerce.payment_orchestrator.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationWorker {

    private final PaymentRepository paymentRepository;
    private final MockGatewayClient gatewayClient;

    // Runs every 30 seconds
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void reconcileOrphanedPayments() {
        // Find transactions stuck in PAYMENT_PENDING for over 1 minute
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        List<PaymentTransaction> stuckPayments = 
            paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PAYMENT_PENDING, cutoff);

        if (stuckPayments.isEmpty()) {
            return;
        }

        log.info("Found {} unacknowledged pending transactions. Polling gateway...", stuckPayments.size());

        for (PaymentTransaction txn : stuckPayments) {
            try {
                MockGatewayClient.GatewayStatus remote = gatewayClient.verifyStatus(txn.getGatewayTransactionId());

                if ("SUCCESS".equalsIgnoreCase(remote.state())) {
                    txn.transitionTo(PaymentStatus.SETTLED);
                    log.info("[AUTO-RECONCILED] Transaction {} -> SETTLED from gateway verification", txn.getId());
                } else {
                    txn.transitionTo(PaymentStatus.FAILED);
                    txn.setFailureReason("Reconciler confirmed payment failed or timed out at bank");
                    log.info("[AUTO-RECONCILED] Transaction {} -> FAILED from gateway verification", txn.getId());
                }
                paymentRepository.save(txn);
            } catch (Exception e) {
                log.error("Failed to reconcile transaction {}: {}", txn.getId(), e.getMessage());
            }
        }
    }
}