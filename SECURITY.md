# Security policy

## Reporting a vulnerability
Please report vulnerabilities privately through
[GitHub Security Advisories](../../security/advisories/new) rather than in public issues.
You will get an acknowledgement within 72 hours and a remediation plan within 14 days for confirmed issues.

## Scope
In scope: this repository's services, web app, infrastructure code and CI/CD workflows.
The demo credentials published in the README are intentional and only exist when `DEMO_SEED=true`.

## Practices
- Threat model: [docs/threat-model.md](docs/threat-model.md)
- Automated on every change: CodeQL, gitleaks, Trivy (dependencies, secrets, IaC, images), dependency review,
  ZAP baseline (weekly), SBOM generation, signed images.
- Secrets never live in the repository: AWS Secrets Manager in the cloud, environment variables locally.
