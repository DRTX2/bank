# ADR-0003: Double-entry ledger with integer minor units

**Status:** accepted

## Decision
- Every movement is a journal entry whose postings sum to zero. A customer transfer debits one account and credits
  another. A cash deposit credits the customer and debits the institution's internal vault account, which may run
  negative because it mirrors assets.
- Amounts are signed `bigint` minor units (cents). No floating point anywhere. `BigDecimal` exists only at the API
  edge and in simulators.
- `accounts.balance_minor` is a cached projection updated under `SELECT … FOR UPDATE`, with rows locked in
  primary-key order. A `CHECK (allow_negative OR balance_minor >= 0)` constraint backs up the domain rule.
- `postings`, `journal_entries` and `audit_log` are append-only through a trigger. Corrections are compensating
  entries, never edits.

## Consequences
History is immutable and reconstructable (`balance_after_minor` on each posting makes statements cheap). Hot
accounts serialise on their row lock. That is the correct behaviour for a single-currency retail ledger at
cooperative scale, and the documented path to scale further is sharding by account.
