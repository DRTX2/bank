# Kuntur core banking — notes for Claude

Multi-tenant core banking showcase (portfolio flagship). Read `README.md` for the product and architecture,
`docs/adr/` for decisions, `docs/threat-model.md` for security gaps already accepted.

## Layout
- `pom.xml` — Maven monorepo (Spring Boot 4.1.1, Java 21): `proto/`, `services/core-banking/`, `services/risk-engine/`.
- `services/core-banking/src/main/java/com/drtx/bank/` — modular monolith: `platform`, `identity`, `accounts`,
  `ledger`, `payments` (+ `iso20022`, `risk`), `simulations`, `realtime`, `ops`, `demo`, `shared/{security,tenancy,audit,outbox,error,web}`.
- `services/risk-engine/` — gRPC server (:9090) + Kafka consumer + rule engine (`RiskRules`) + feature store.
- `web/` — React 19 + Vite + TS + Tailwind 4 + TanStack Query + STOMP. UI copy is **Spanish**.
- `infra/terraform/` — AWS modules + `envs/{dev,prod}` + `bootstrap`. Validated only, **never applied**.
- `.github/workflows/` — ci, security, containers, deploy, infra, dast. Actions pinned by SHA.

## Commands
| Task | Command |
|---|---|
| All backend tests (needs Docker for Testcontainers) | `./mvnw -B -ntp verify` |
| One test class | `./mvnw -B -ntp -pl services/core-banking -am verify -Dtest=TransferIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Frontend checks | `cd web && npm run lint && npm run typecheck && npm test && npm run build` |
| Full stack (demo data, simulator) | `make up` → http://localhost:8088 · `make down` |
| Stack + Prometheus/Grafana | `make observability` (Prometheus :9091, Grafana :3000) |
| E2E (Playwright, needs `make up`) | `make e2e` (one spec: `cd web && npx playwright test e2e/payments.spec.ts`) |
| Load test (k6 in Docker, needs `make up`) | `make load` (`PEAK_RPS=150` default; 150/s → p95 ≈ 80 ms on the dev laptop) |
| README screenshots | `cd web && npm run screenshots` → `docs/screenshots/` |
| Terraform fmt/validate (via Docker) | `make tf-validate` |
| Security scans | `make scan` |

Ports: Postgres **5433** (5432 is taken by another local project), Kafka external **9094**, core-banking 8080,
web 8088, Swagger at `/swagger-ui.html`. Demo password for every user: `Kuntur2026!`
(e.g. `admin@andina.ec`, `ana@andina.ec`, `root@kuntur.dev`; full list in `demo/DemoDataSeeder`).
`gerente@andina.ec` has MFA with the published demo secret `KUNTURDEMOGERENTEMFAANDINAKUNTUR` (`web/src/lib/totp.ts`
computes its code; the login and approvals screens show it for that demo user).

## Rules that keep the codebase consistent
- Every controller endpoint has `@PreAuthorize` with a code from `shared/security/Permission`.
  `SecurityArchitectureTest` fails the build otherwise. New permission → add to `Permission` and to roles in `SystemRole`.
- Money is `long` minor units end to end (`bigint` in SQL). Never `double`/`BigDecimal` in the domain.
- Ledger postings are append-only (DB triggers). Book money only through `LedgerService.post`.
- Tenant-owned entities implement `TenantOwned` (`@TenantId`). Cross-tenant work goes through `TenantScope.asSystem`.
  Tenancy is fail-closed: no tenant bound → queries match nothing.
- Resources a caller may not see return **404**, not 403 (no ID enumeration).
- Errors: throw `DomainException(ErrorCode.X)`; responses are RFC 9457 problem details. ISO 20022 reason codes live on `ErrorCode`.
- Domain events go through `OutboxWriter` in the same transaction, never straight to Kafka.
- Schema changes: add a new Flyway migration (next is `V3__...`). Never edit an applied one.
- Docs and code comments in English; UI strings in Spanish. Backend error messages stay English; the UI translates by
  error code / ISO reason in `web/src/lib/errors.ts` — add new codes there.
- Approving four-eyes transfers is a step-up: `MfaService.requireStepUp` before `TransferService.approve`.
- Money moves only through `LedgerService.post`, which locks accounts by `refresh(PESSIMISTIC_WRITE)` in uuid
  order. Do not lock accounts with a query after reading them in the same transaction (stale-version 409s).

## Gotchas
- protobuf must stay 4.x: the micrometer OTLP exporter breaks on 3.x (`RuntimeVersion$RuntimeDomain`).
- `pom.xml` has security overrides (tomcat, jackson, lz4). Remove them once Spring Boot ships the fixes.
- The user's shell is zsh: no word splitting of unquoted variables. Wrap docker one-liners in `bash -c` when that matters.
- Risk engine cold start: the gRPC channel connects eagerly (`RiskChannelConfig`) so the first transfer does not blow the 500 ms deadline.
- Trivy in CI pre-populates `~/.m2` to avoid Maven Central HTTP 429.
- Virtual threads are **off** on purpose (Java 21 pinning: p95 4.8 s vs 27 ms at 140 req/s). Re-enable only on Java 24+
  and re-run `make load`.
- TOTP codes are single-use per user: tests use `otp(secret, +1)` for the next code after login/activation; E2E
  `mfaLogin` waits for the next 30 s window if a previous run spent the current one.
- The API answers before `DemoDataSeeder` finishes: wait for `cajero@pacifico.ec` to log in, not just `/tenants`.
- nginx upstream is named `core-banking` (the name becomes the Host header; Tomcat rejects `_`). Never add
  `add_header` inside a `location` (it drops the server-level security headers).
- `gitleaks` allowlist in `.gitleaks.toml` covers only the demo TOTP secret and the RFC 6238 test seed.
- Login rate limit is per IP via `X-Forwarded-For` (`server.forward-headers-strategy: native`); compose raises it
  to 300/min (`LOGIN_RATE_LIMIT`) because all local clients share the Docker gateway IP.

## Hard limits
- **Do not deploy to AWS** (`terraform apply`, `aws ...`, the `deploy.yml`/`infra.yml` workflows) without the user's explicit approval. It costs money; ARNs, certificates and the domain in tfvars are placeholders.
- Pushing, opening PRs and force-pushing need confirmation.
