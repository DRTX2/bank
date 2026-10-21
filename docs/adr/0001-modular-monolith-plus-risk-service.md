# ADR-0001: Modular monolith for money movement, separate service for risk scoring

**Status:** accepted

## Context
Booking a transfer touches accounts, ledger postings, the transfer state machine, the audit trail and the outbox.
These must commit or roll back together. Splitting them across services would require sagas and compensations
for what is naturally one ACID transaction, and would make overdraft and double-spend bugs far more likely.

Risk scoring is different: CPU-bound, read-heavy, likely to become an ML model with its own release cadence,
and it owns a different data model (behavioural features, not balances).

## Decision
- `core-banking` is a modular monolith with package-by-feature modules (`identity`, `accounts`, `ledger`,
  `payments`, …) and explicit public types between them.
- `risk-engine` is a separate deployable with its own database, reached synchronously over gRPC and fed
  asynchronously from Kafka.

## Consequences
- One transaction books money. No distributed transaction exists anywhere in the payment path.
- The risk engine can scale, fail and be redeployed independently. Its unavailability is handled by an explicit
  degradation policy ([ADR-0007](0007-risk-engine-degradation-policy.md)).
- More services can be extracted later along the same seams (for example notifications) when a scaling or
  ownership reason appears, not before.
