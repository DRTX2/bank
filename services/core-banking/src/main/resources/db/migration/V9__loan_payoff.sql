-- Early payoff: the customer settles the outstanding principal plus the interest accrued so far; the installments
-- that were still open are closed as prepaid (their scheduled interest is never charged).
alter table loan_installments
    add column prepaid boolean not null default false;

insert into permissions (code, description, scope)
values ('loans:repay:own', 'Pagar por anticipado sus créditos', 'TENANT')
on conflict (code) do nothing;
insert into role_permissions (role_id, permission_code)
select id, 'loans:repay:own' from roles where system and name in ('CUSTOMER', 'CORPORATE_CUSTOMER')
on conflict do nothing;
