## What changes

## Why

## Checklist
- [ ] Tests cover the new behaviour (integration test if it touches money, auth or tenancy)
- [ ] New endpoints declare `@PreAuthorize` with a permission from `Permission`
- [ ] Database changes are a new Flyway migration (never edit an applied one)
- [ ] No secrets, PII or tokens in code, logs or fixtures
- [ ] Docs/ADR updated if an architectural decision changed
