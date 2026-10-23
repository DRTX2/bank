# Threat model (STRIDE)

Scope: the public web/API surface, service-to-service traffic, data at rest and the delivery pipeline.
Assets, in order of value: customer funds (ledger integrity), credentials and sessions, personal data, and audit
evidence.

| Threat | Example attack | Mitigations | Evidence |
|---|---|---|---|
| **Spoofing** | Stolen password, credential stuffing | BCrypt; per-user lockout after 5 failures; per-IP limiter; WAF rate rule on `/api/v1/auth/*`; constant-time unknown-user path | `AuthIntegrationTest`, `edge` module |
| **Spoofing** | Stolen password or session used to approve a large transfer | TOTP second factor at login; a fresh, single-use code (step-up) for every four-eyes approval; wrong codes count towards lockout | `MfaIntegrationTest`, ADR-0008 |
| **Spoofing** | Stolen refresh token | Opaque, hashed at rest, rotated on every use; reuse revokes the whole family | `refreshTokenRotatesAndReuseRevokesTheWholeSession` |
| **Spoofing** | Bypassing CloudFront/WAF by calling the ALB directly | ALB security group admits only the CloudFront prefix list; listener rule requires a secret origin header | `infra/terraform/modules/edge` |
| **Tampering** | Altering balances or history in the database | Append-only triggers on postings, journal and audit; `CHECK` constraint against overdraft; SHA-256 hash chain on the audit log | `AuditTrailIntegrationTest` |
| **Tampering** | One insider granting a large loan to an accomplice | Loans above the four-eyes threshold wait for a second manager with a fresh MFA code; the granter cannot decide; disbursement only after approval | `aLoanAboveTheThresholdWaitsForASecondManagerWithAFreshCode` |
| **Tampering** | Replaying or duplicating a payment | Idempotency key with payload fingerprint; `MsgId` uniqueness for pain.001 (`DUPL`) | `idempotencyKeyPreventsDoublePayment` |
| **Tampering** | Malicious XML (XXE, billion laughs) | DTDs disabled, no external entities, secure processing, 2 MB and 500-transaction caps | `Pain001ParserTest.blocksXxeAttacks` |
| **Tampering** | Tampered container image in the registry | Keyless cosign signatures, verified before promotion to ECR; immutable ECR tags | `containers.yml`, `deploy.yml` |
| **Repudiation** | "I never approved that transfer" | Every sensitive action is audited with actor, time and notes; four-eyes records reviewer and note; chain verification | `/api/v1/audit/verify` |
| **Information disclosure** | Reading another institution's data | Fail-closed Hibernate tenant filter; tenant only from the signed token; PostgreSQL row-level security underneath, with the app running as a role that owns nothing | `tenantsAreIsolated`, `RowLevelSecurityIntegrationTest`, ADR-0009 |
| **Information disclosure** | Enumerating other customers' accounts | Ownership checks return `404`, not `403`; destination lookup reveals only a masked name | `customersOnlySeeTheirOwnAccounts` |
| **Information disclosure** | Leaking internals in errors or logs | RFC 9457 problems without stack traces; WAF logs redact `Authorization`; no PII in log statements | `GlobalExceptionHandler` |
| **Information disclosure** | Data at rest or in transit | KMS on RDS, logs, secrets, ECR and the site bucket; TLS enforced on RDS (`rds.force_ssl`), MSK (IAM over TLS) and the edge (TLS 1.2+) | `infra/terraform` |
| **Denial of service** | Flooding the API or login | WAF managed rules and rate limiting; autoscaling; bounded inputs (page sizes, XML size, simulator limits) | `edge`, controllers |
| **Denial of service** | A poison Kafka event stalling or silently skipping the risk feature store | Bounded retries, then the event is parked on `<topic>.dlt` with the exception in its headers | `poisonEventIsParkedOnTheDeadLetterTopic` |
| **Denial of service** | Risk engine outage blocking payments | 500 ms deadline; fail-open below 50.00, fail-closed above | ADR-0007, `riskEngineOutageFails…` |
| **Elevation of privilege** | Customer calling staff endpoints | `@PreAuthorize` on every endpoint, enforced by an architecture test | `SecurityArchitectureTest` |
| **Elevation of privilege** | Admin granting themselves more power | No self role changes; platform permissions cannot go into custom roles; four-eyes in the domain model | `AuthorizationIntegrationTest` |
| **Elevation of privilege** | Compromised CI pipeline | Actions pinned by SHA; least-privilege job permissions; OIDC role scoped to repo and environment; prod requires reviewers | `.github/workflows` |

## Known gaps / roadmap
- A dedicated database login for the app in production instead of `SET ROLE` from the owner user (ADR-0009).
- mTLS between services (ECS Service Connect TLS with ACM Private CA).
- WebAuthn/passkeys (phishing-resistant) on top of TOTP, and KMS data keys with rotation for the TOTP seed key (seeds are already AES-GCM sealed).
- BFF with httpOnly cookies instead of tokens in `sessionStorage` for the SPA.
