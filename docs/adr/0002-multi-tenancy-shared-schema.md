# ADR-0002: Multi-tenancy with a shared schema and fail-closed tenant filtering

**Status:** accepted

## Context
Kuntur hosts many small institutions (credit unions, fintechs). Each must only ever see its own data. Options:
database per tenant, schema per tenant, or a shared schema with a discriminator column.

## Decision
Shared schema. Every business table has `tenant_id`, and entities extend `TenantOwned` (Hibernate `@TenantId`),
so Hibernate stamps inserts and filters every query automatically.

- The tenant comes only from the verified JWT (`tid` claim), bound per request by `TenantContextFilter`.
- **Fail-closed:** with no tenant bound, the resolver returns a sentinel that matches no row. Cross-tenant work
  (outbox relay, provisioning, recovery sweeps) must opt in explicitly through `TenantContext.SYSTEM`.
- Native SQL is reserved for tenant-agnostic tables (outbox) or passes `tenant_id` explicitly (audit).
- Account numbers embed the institution code, and the ledger is per institution: a transfer to another
  institution's account fails with ISO reason `AC01`.

## Consequences
- Cheap to onboard a tenant (one row plus seed data), simple migrations, efficient connection pooling.
- Isolation depends on application code, so it is covered by integration tests (`tenantsAreIsolated`).
- Next hardening step: PostgreSQL Row-Level Security keyed on a `SET LOCAL app.tenant_id`, as defence in depth
  against a future native query that forgets the filter.
