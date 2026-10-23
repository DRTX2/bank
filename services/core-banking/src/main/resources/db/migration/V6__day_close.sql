-- End-of-day close: one day of interest on savings accounts, booked through the ledger once per business date.
alter table tenants
    add column savings_rate_bps int not null default 300 check (savings_rate_bps between 0 and 5000);

create table day_closes (
    id                uuid primary key,
    tenant_id         uuid        not null references tenants (id),
    business_date     date        not null,
    closed_by_email   varchar(254),
    accounts_credited int         not null,
    interest_minor    bigint      not null,
    rate_bps          int         not null,
    journal_id        uuid references journal_entries (id),
    closed_at         timestamptz not null,
    unique (tenant_id, business_date)
);

-- Same tenant boundary as every other tenant table (ADR-0009); DML grants come from V4's default privileges.
alter table day_closes enable row level security;
alter table day_closes force row level security;
create policy tenant_isolation on day_closes
    using (current_setting('app.tenant_id', true) = 'system'
        or tenant_id = nullif(nullif(current_setting('app.tenant_id', true), ''), 'system')::uuid);

-- Existing institutions' system roles get the new permission; new ones get it from SystemRole.
insert into permissions (code, description, scope)
values ('ops:close-day', 'Ejecutar el cierre de día y abonar intereses', 'TENANT')
on conflict (code) do nothing;
insert into role_permissions (role_id, permission_code)
select id, 'ops:close-day' from roles where system and name in ('BRANCH_MANAGER', 'TENANT_ADMIN')
on conflict do nothing;
