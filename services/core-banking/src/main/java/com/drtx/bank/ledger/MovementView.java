package com.drtx.bank.ledger;

import java.time.Instant;
import java.util.UUID;

public record MovementView(Long postingId, UUID journalId, JournalEntry.Type type, String reference,
                           String description, long amountMinor, long balanceAfterMinor, Instant bookedAt) {
}
