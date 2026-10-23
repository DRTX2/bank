# ADR-0009: PostgreSQL row-level security as a second tenancy barrier

**Status:** accepted (implements the hardening step named in ADR-0002)

## Context
Tenant isolation lived only in application code: Hibernate `@TenantId` adds `tenant_id = ?` to every query it
generates. A native query, a `JdbcTemplate` call or a reporting script that forgets that predicate would read or
change every institution's rows, and nothing underneath would stop it.

## Decision
- **Policies:** every table with a `tenant_id` column (except `outbox_events`, which the relay reads across tenants by
  design) has `ENABLE` + `FORCE ROW LEVEL SECURITY` and one policy: a row is visible and writable only when its
  `tenant_id` equals the transaction's `app.tenant_id`, or when that setting is `system` (outbox relay, provisioning,
  recovery sweeps, the demo seeder). Unset or empty matches nothing, so the database is fail-closed like the app.
- **Per transaction, not per connection:** `TenantRlsListener` (a Spring `TransactionExecutionListener`) runs
  `set_config('app.tenant_id', ?, true)` right after every transaction begins, on the same connection Hibernate uses.
  The tenant it sends is the one Hibernate resolves at session start, so both layers always agree. The setting is
  transaction-local, so it can never leak to the next borrower of a pooled connection.
- **A role that owns nothing:** the app connects and runs `SET ROLE kuntur_app` (Hikari `connection-init-sql`).
  That role is not the table owner, superuser or `BYPASSRLS`, so it cannot disable policies, triggers or constraints.
  Flyway keeps its own owner connection (`spring.flyway.url/user`).
- **Append-only is also a privilege:** `kuntur_app` has no `UPDATE`/`DELETE`/`TRUNCATE` on `postings`,
  `journal_entries` and `audit_log`; the triggers stay as the second line.

## Consequences
- One extra statement per transaction (`set_config`). Policies add a cheap filter per row, measured under load
  (README, *Performance*).
- Raw SQL outside a transaction sees no tenant rows. That is the point; such code must run inside a transaction or
  `TenantScope`.
- New tenant tables must be added to the policy list in a migration. `RowLevelSecurityIntegrationTest` fails the
  build when a table with `tenant_id` lacks forced policies, and proves the boundary with plain SQL (reads, updates
  and a planted cross-tenant insert).
- `SET ROLE` is a session switch that SQL injection could undo (`RESET ROLE`). The stronger form is a dedicated
  login user for the app in production; the policies and grants stay the same.
