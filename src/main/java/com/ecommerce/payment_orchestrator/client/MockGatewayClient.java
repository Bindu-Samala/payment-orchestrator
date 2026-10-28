package com.ecommerce.payment_orchestrator.client;


import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class MockGatewayClient {

    public record ChargeResult(String gatewayTxnId, String status) {}
    public record GatewayStatus(String gatewayTxnId, String state) {}

    // Simulates an API call to a provider like Razorpay/Stripe
    public ChargeResult initiateCharge(Long amountInCents) {
        // Generates a mock transaction ID (e.g., gtx_102)
        String mockTxnId = "gtx_" + UUID.randomUUID().toString().substring(0, 8);
        return new ChargeResult(mockTxnId, "PENDING");
    }

    // Polling endpoint used by the background reconciler
    public GatewayStatus verifyStatus(String gatewayTxnId) {
        // Deterministic simulation: If the last character is a digit and even, it succeeded at the bank
        char lastChar = gatewayTxnId.charAt(gatewayTxnId.length() - 1);
        if (Character.isDigit(lastChar) && Character.getNumericValue(lastChar) % 2 == 0) {
            return new GatewayStatus(gatewayTxnId, "SUCCESS");
        }
        return new GatewayStatus(gatewayTxnId, "FAILED");
    }
}