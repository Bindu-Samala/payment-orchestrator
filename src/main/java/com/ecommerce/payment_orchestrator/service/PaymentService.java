package com.ecommerce.payment_orchestrator.service;


import com.ecommerce.payment_orchestrator.client.MockGatewayClient;
import com.ecommerce.payment_orchestrator.model.PaymentStatus;
import com.ecommerce.payment_orchestrator.model.PaymentTransaction;
import com.ecommerce.payment_orchestrator.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MockGatewayClient gatewayClient;
    private final RedissonClient redissonClient;

    public PaymentTransaction processCheckout(String orderId, Long amountInCents, String idempotencyKey) {
        String lockKey = "lock:idemp:" + idempotencyKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Acquire lock: wait up to 2 seconds, hold for 30 seconds
            boolean acquired = lock.tryLock(2, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("Another payment request is already processing with this key.");
            }

            // 1. Idempotency Check: Return existing record if already processed
            Optional<PaymentTransaction> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay triggered for key: {}", idempotencyKey);
                return existing.get();
            }

            // 2. Create pending transaction
            PaymentTransaction txn = new PaymentTransaction(
                UUID.randomUUID().toString(), orderId, idempotencyKey, amountInCents
            );
            txn.transitionTo(PaymentStatus.PAYMENT_PENDING);

            // 3. Dispatch to Gateway
            MockGatewayClient.ChargeResult chargeResult = gatewayClient.initiateCharge(amountInCents);
            txn.setGatewayTransactionId(chargeResult.gatewayTxnId());

            return paymentRepository.save(txn);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition was interrupted", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    public void handleWebhook(String gatewayTxnId, String status) {
        PaymentTransaction txn = paymentRepository.findByGatewayTransactionId(gatewayTxnId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found for ID: " + gatewayTxnId));

        // State Machine Guard: Terminal states reject incoming callbacks
        if (txn.getStatus() == PaymentStatus.SETTLED || txn.getStatus() == PaymentStatus.FAILED) {
            log.warn("Webhook ignored for already finalized transaction: {}", txn.getId());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            txn.transitionTo(PaymentStatus.SETTLED);
            log.info("Transaction {} settled via webhook.", txn.getId());
        } else {
            txn.transitionTo(PaymentStatus.FAILED);
            txn.setFailureReason("Declined via gateway webhook");
            log.info("Transaction {} marked failed via webhook.", txn.getId());
        }
        paymentRepository.save(txn);
    }
}