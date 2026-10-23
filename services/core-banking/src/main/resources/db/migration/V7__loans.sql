-- Loans: French amortization, disbursed and repaid through the ledger; installments are collected at the day close.
create table loans (
    id                uuid primary key,
    tenant_id         uuid         not null references tenants (id),
    customer_id       uuid         not null references users (id),
    account_id        uuid         not null references accounts (id),
    principal_minor   bigint       not null check (principal_minor > 0),
    annual_rate_bps   int          not null check (annual_rate_bps between 0 and 6000),
    term_months       int          not null check (term_months between 1 and 360),
    outstanding_minor bigint       not null check (outstanding_minor >= 0),
    status            varchar(16)  not null,
    created_by_email  varchar(254) not null,
    created_at        timestamptz  not null,
    version           bigint       not null default 0
);
create index loans_customer on loans (tenant_id, customer_id);

create table loan_installments (
    id              uuid primary key,
    tenant_id       uuid        not null references tenants (id),
    loan_id         uuid        not null references loans (id),
    number          int         not null,
    due_date        date        not null,
    principal_minor bigint      not null check (principal_minor >= 0),
    interest_minor  bigint      not null check (interest_minor >= 0),
    paid_at         timestamptz,
    journal_id      uuid references journal_entries (id),
    version         bigint      not null default 0,
    unique (loan_id, number)
);
create index loan_installments_due on loan_installments (tenant_id, due_date) where paid_at is null;

-- Same tenant boundary as every tenant table (ADR-0009).
do
$$
    declare
        t text;
    begin
        foreach t in array array ['loans', 'loan_installments']
            loop
                execute format('alter table %I enable row level security', t);
                execute format('alter table %I force row level security', t);
                execute format($p$
                    create policy tenant_isolation on %I
                        using (current_setting('app.tenant_id', true) = 'system'
                            or tenant_id = nullif(nullif(current_setting('app.tenant_id', true), ''), 'system')::uuid)$p$,
                               t);
            end loop;
    end
$$;

-- Existing institutions' system roles get the new permissions; new ones get them from SystemRole.
insert into permissions (code, description, scope)
values ('loans:originate', 'Otorgar créditos y desembolsarlos', 'TENANT'),
       ('loans:read:own', 'Ver sus créditos y su tabla de pagos', 'TENANT'),
       ('loans:read:any', 'Ver los créditos de la institución', 'TENANT')
on conflict (code) do nothing;
insert into role_permissions (role_id, permission_code)
select r.id, g.code
from roles r
         join (values ('BRANCH_MANAGER', 'loans:originate'),
                      ('BRANCH_MANAGER', 'loans:read:any'),
                      ('AUDITOR', 'loans:read:any'),
                      ('CUSTOMER', 'loans:read:own'),
                      ('CORPORATE_CUSTOMER', 'loans:read:own')) as g(role, code) on g.role = r.name
where r.system
on conflict do nothing;
