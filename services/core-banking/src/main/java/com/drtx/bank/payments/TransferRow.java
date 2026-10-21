package com.drtx.bank.payments;

import java.util.UUID;

/** Transfer joined with both account numbers and owners (one query, no N+1). */
public record TransferRow(Transfer transfer, String sourceNumber, String destinationNumber, UUID sourceOwnerId,
                          UUID destinationOwnerId) {
}
