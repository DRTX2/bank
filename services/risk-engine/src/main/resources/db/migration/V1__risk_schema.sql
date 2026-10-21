-- Feature store of the risk engine. Owned by this service only (database-per-service).

-- Booked transfers, fed asynchronously from Kafka (bank.payments.events.v1, TransferCompleted).
create table transfer_facts (
    transfer_id            uuid primary key,
    tenant_id              uuid        not null,
    source_account_id      uuid        not null,
    destination_account_id uuid        not null,
    amount_minor           bigint      not null,
    occurred_at            timestamptz not null
);
create index facts_source on transfer_facts (source_account_id, occurred_at desc);
create index facts_destination on transfer_facts (destination_account_id, occurred_at desc);

-- Every scoring request: velocity counts attempts, not just successes, and decisions stay explainable.
create table score_log (
    transfer_id       uuid primary key,
    tenant_id         uuid         not null,
    source_account_id uuid         not null,
    destination_account_id uuid    not null,
    amount_minor      bigint       not null,
    score             int          not null,
    decision          varchar(16)  not null,
    signals           varchar(500) not null,
    model_version     varchar(32)  not null,
    scored_at         timestamptz  not null
);
create index score_log_source on score_log (source_account_id, scored_at desc);
create index score_log_destination on score_log (destination_account_id, scored_at desc);
