-- Kuntur core-banking schema.
-- Multi-tenancy: shared schema, every business table carries tenant_id (enforced by Hibernate @TenantId).
-- Money: signed bigint minor units (cents). Never floating point.

create table tenants (
    id                       uuid primary key,
    code                     varchar(8)   not null unique,
    name                     varchar(120) not null,
    country                  varchar(2)      not null,
    currency                 varchar(3)      not null,
    bic                      varchar(11)  not null,
    status                   varchar(16)  not null,
    approval_threshold_minor bigint       not null check (approval_threshold_minor > 0),
    created_at               timestamptz  not null
);

create table permissions (
    code        varchar(64)  primary key,
    description varchar(255) not null,
    scope       varchar(16)  not null
);

create table roles (
    id          uuid primary key,
    tenant_id   uuid         not null references tenants (id),
    name        varchar(64)  not null,
    description varchar(255),
    system      boolean      not null default false,
    created_at  timestamptz  not null,
    unique (tenant_id, name)
);

create table role_permissions (
    role_id         uuid        not null references roles (id) on delete cascade,
    permission_code varchar(64) not null references permissions (code),
    primary key (role_id, permission_code)
);

create table users (
    id            uuid primary key,
    tenant_id     uuid         not null references tenants (id),
    email         varchar(254) not null,
    full_name     varchar(120) not null,
    national_id   varchar(20),
    password_hash varchar(100) not null,
    status        varchar(16)  not null,
    failed_logins int          not null default 0,
    locked_until  timestamptz,
    created_at    timestamptz  not null,
    version       bigint       not null default 0,
    unique (tenant_id, email)
);

create table user_roles (
    user_id uuid not null references users (id) on delete cascade,
    role_id uuid not null references roles (id) on delete cascade,
    primary key (user_id, role_id)
);

create table refresh_tokens (
    id         uuid primary key,
    tenant_id  uuid        not null references tenants (id),
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash varchar(64)    not null unique,
    family_id  uuid        not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null
);
create index refresh_tokens_family on refresh_tokens (family_id);

create table accounts (
    id                uuid primary key,
    tenant_id         uuid        not null references tenants (id),
    number            varchar(34) not null unique,
    owner_id          uuid references users (id),
    type              varchar(16) not null,
    currency          varchar(3)     not null,
    balance_minor     bigint      not null default 0,
    status            varchar(16) not null,
    allow_negative    boolean     not null default false,
    daily_limit_minor bigint      not null,
    alias             varchar(60),
    created_at        timestamptz not null,
    version           bigint      not null default 0,
    -- last line of defence against overdraft bugs: the database refuses it
    constraint balance_non_negative check (allow_negative or balance_minor >= 0)
);
create index accounts_owner on accounts (tenant_id, owner_id);

create table journal_entries (
    id          uuid primary key,
    tenant_id   uuid        not null references tenants (id),
    type        varchar(24) not null,
    reference   varchar(64) not null,
    description varchar(140),
    created_at  timestamptz not null
);

create table postings (
    id                  bigserial primary key,
    tenant_id           uuid        not null references tenants (id),
    journal_id          uuid        not null references journal_entries (id),
    account_id          uuid        not null references accounts (id),
    amount_minor        bigint      not null check (amount_minor <> 0),
    balance_after_minor bigint      not null,
    created_at          timestamptz not null
);
create index postings_account_time on postings (account_id, created_at);
create index postings_journal on postings (journal_id);

create table payment_batches (
    id                uuid primary key,
    tenant_id         uuid        not null references tenants (id),
    message_id        varchar(35) not null,
    created_by        uuid        not null,
    number_of_txs     int         not null,
    control_sum_minor bigint      not null,
    status            varchar(16) not null,
    created_at        timestamptz not null,
    unique (tenant_id, message_id)
);

create table transfers (
    id                     uuid primary key,
    tenant_id              uuid        not null references tenants (id),
    idempotency_key        varchar(80) not null,
    request_hash           varchar(64)    not null,
    initiated_by           uuid        not null,
    source_account_id      uuid        not null references accounts (id),
    destination_account_id uuid        not null references accounts (id),
    amount_minor           bigint      not null check (amount_minor > 0),
    currency               varchar(3)     not null,
    status                 varchar(24) not null,
    end_to_end_id          varchar(35) not null,
    uetr                   uuid        not null unique,
    remittance_info        varchar(140),
    purpose_code           varchar(4),
    risk_score             int,
    risk_signals           varchar(500),
    journal_id             uuid references journal_entries (id),
    batch_id               uuid references payment_batches (id),
    reviewed_by            uuid,
    review_note            varchar(255),
    failure_code           varchar(8),
    failure_reason         varchar(255),
    created_at             timestamptz not null,
    updated_at             timestamptz not null,
    version                bigint      not null default 0,
    unique (tenant_id, initiated_by, idempotency_key),
    check (source_account_id <> destination_account_id)
);
create index transfers_status on transfers (tenant_id, status, created_at desc);
create index transfers_source on transfers (source_account_id, created_at desc);
create index transfers_destination on transfers (destination_account_id, created_at desc);

create table audit_log (
    id            bigserial primary key,
    tenant_id     uuid         not null,
    actor_id      uuid,
    actor_email   varchar(254),
    action        varchar(64)  not null,
    resource_type varchar(32),
    resource_id   varchar(64),
    details       varchar(1000),
    created_at    timestamptz  not null,
    prev_hash     varchar(64)     not null,
    hash          varchar(64)     not null
);
create index audit_tenant on audit_log (tenant_id, id);

create table outbox_events (
    id             uuid primary key,
    tenant_id      uuid         not null,
    aggregate_type varchar(32)  not null,
    aggregate_id   varchar(64)  not null,
    event_type     varchar(64)  not null,
    topic          varchar(128) not null,
    payload        text         not null,
    created_at     timestamptz  not null,
    published_at   timestamptz,
    attempts       int          not null default 0
);
create index outbox_pending on outbox_events (created_at) where published_at is null;

-- Ledger postings and the audit trail are append-only, enforced by the database itself.
create function forbid_mutation() returns trigger
    language plpgsql as
$$
begin
    raise exception '% is append-only', tg_table_name using errcode = 'P0001';
end
$$;

create trigger postings_append_only before update or delete on postings
    for each row execute function forbid_mutation();
create trigger journal_append_only before update or delete on journal_entries
    for each row execute function forbid_mutation();
create trigger audit_append_only before update or delete on audit_log
    for each row execute function forbid_mutation();
