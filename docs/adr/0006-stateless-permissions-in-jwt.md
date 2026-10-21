# ADR-0006: Granular permissions carried in short-lived JWTs

**Status:** accepted

## Decision
- Authorization is permission-based (`transfers:approve`), never role-based in code. Roles are just bundles of
  permissions, editable per institution. The `Permission` enum is the single source of truth, synced to the
  database at startup and guarded by an architecture test.
- Access tokens (RS256, 10 min) carry the effective permissions, so each request is authorized without a
  database hit. The JWKS is published at `/.well-known/jwks.json` for other services or a gateway.
- Refresh tokens are opaque, hashed at rest, rotated on every use, with family revocation on reuse.
- Privilege rules: nobody edits their own roles, custom roles can never contain platform-scope permissions, and
  four-eyes is enforced in the `Transfer` aggregate itself.

## Consequences
Revoking a permission takes effect within 10 minutes (the next refresh re-reads roles). For immediate revocation
of a compromised account, disabling the user also kills its refresh-token family. The trade-off is documented
rather than hidden.
