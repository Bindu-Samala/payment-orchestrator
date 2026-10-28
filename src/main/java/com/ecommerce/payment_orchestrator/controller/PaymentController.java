package com.ecommerce.payment_orchestrator.controller;


import com.ecommerce.payment_orchestrator.model.PaymentTransaction;
import com.ecommerce.payment_orchestrator.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    public record CheckoutRequest(String orderId, Long amountInCents) {}
    public record WebhookRequest(String gatewayTxnId, String status) {}

    @PostMapping("/checkout")
    public ResponseEntity<PaymentTransaction> checkout(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CheckoutRequest request) {

        PaymentTransaction txn = paymentService.processCheckout(
            request.orderId(), request.amountInCents(), idempotencyKey
        );
        return ResponseEntity.ok(txn);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(@RequestBody WebhookRequest request) {
        paymentService.handleWebhook(request.gatewayTxnId(), request.status());
        return ResponseEntity.ok("ACK");
    }
}
