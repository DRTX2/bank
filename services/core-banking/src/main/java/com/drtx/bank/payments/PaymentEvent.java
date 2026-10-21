package com.drtx.bank.payments;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event on {@code bank.payments.events.v1}. Additive changes only; breaking changes go to a v2 topic.
 * Consumers: risk-engine (feature store), core-banking realtime gateway (WebSocket fan-out).
 */
public record PaymentEvent(UUID eventId, String type, UUID tenantId, UUID transferId, UUID sourceAccountId,
                           UUID destinationAccountId, UUID sourceOwnerId, UUID destinationOwnerId, String sourceNumber,
                           String destinationNumber, long amountMinor, String currency, String status,
                           Integer riskScore, String riskSignals, String failureCode, Instant occurredAt) {

    static PaymentEvent of(String type, TransferRow row) {
        Transfer t = row.transfer();
        return new PaymentEvent(UUID.randomUUID(), type, t.getTenantId(), t.getId(), t.getSourceAccountId(),
                t.getDestinationAccountId(), row.sourceOwnerId(), row.destinationOwnerId(), row.sourceNumber(),
                row.destinationNumber(), t.getAmountMinor(), t.getCurrency(), t.getStatus().name(), t.getRiskScore(),
                t.getRiskSignals(), t.getFailureCode(), Instant.now());
    }
}
