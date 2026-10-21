# ADR-0004: Transactional outbox for domain events

**Status:** accepted

## Context
Publishing to Kafka inside a database transaction risks dual-write inconsistency: the event is sent but the
transaction rolls back, or the transaction commits and the send is lost.

## Decision
Events are inserted into `outbox_events` in the same transaction as the state change. `OutboxRelay` polls with
`FOR UPDATE SKIP LOCKED`, publishes with `acks=all` and an idempotent producer, then marks rows as published.
Several replicas can relay concurrently without double-sending a row.

## Consequences
Delivery is at-least-once, and consumers are idempotent (the risk engine upserts by `transfer_id`). Ordering is
kept per row; the relay stops at the first failure and retries. Latency is about one poll interval (250 ms).
CDC (Debezium) is the upgrade path if throughput outgrows polling.
