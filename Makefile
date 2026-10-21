.DEFAULT_GOAL := help
TF := docker run --rm -u $$(id -u):$$(id -g) -e HOME=/tmp -v $(CURDIR)/infra/terraform:/w -w /w hashicorp/terraform:1.16.5

help: ## List targets
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  %-14s %s\n", $$1, $$2}'

up: ## Start the full stack with demo data (http://localhost:8088)
	docker compose up --build -d

observability: ## Stack plus Prometheus (:9091) and Grafana (:3000)
	docker compose --profile observability up --build -d

down: ## Stop the stack (keeps data)
	docker compose down

test: ## Backend + frontend tests
	./mvnw -B verify
	cd web && npm ci && npm run lint && npm run typecheck && npm test

web-dev: ## Front end with hot reload (expects backend on :8080)
	cd web && npm run dev

tf-validate: ## terraform fmt + validate for every root module
	$(TF) fmt -check -recursive
	for d in envs/dev envs/prod bootstrap; do $(TF) -chdir=$$d init -backend=false -input=false >/dev/null && $(TF) -chdir=$$d validate; done

scan: ## Local security scans (Trivy IaC + filesystem, gitleaks)
	docker run --rm -v $(CURDIR):/w aquasec/trivy:latest fs --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --skip-dirs /w/web/node_modules /w
	docker run --rm -v $(CURDIR):/repo zricethezav/gitleaks:v8.30.1 git /repo

.PHONY: help up observability down test web-dev tf-validate scan
