-- One hash chain per tenant serialised every audited write of an institution behind a single lock (k6 at 150 tps:
-- request threads queued on it while holding pooled connections). Records now spread over independent chains
-- (AuditService.CHAINS); rows written before this migration form chain 0, which simply continues.
alter table audit_log
    add column chain smallint not null default 0;
create index audit_chain on audit_log (tenant_id, chain, id);
