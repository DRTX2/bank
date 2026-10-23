-- Second tenancy barrier (ADR-0009). Hibernate @TenantId already filters every query; these policies make PostgreSQL
-- enforce the same boundary, so a native query that forgets "where tenant_id = ?" still sees nothing.
--
-- The application connects as a login user and immediately runs SET ROLE kuntur_app (hikari connection-init-sql).
-- kuntur_app owns nothing, so it cannot disable policies, triggers or constraints; Flyway keeps the owner connection.
do
$$
    begin
        if not exists (select from pg_roles where rolname = 'kuntur_app') then
            create role kuntur_app nologin;
        end if;
    end
$$;
grant kuntur_app to current_user;

grant usage on schema public to kuntur_app;
grant select, insert, update, delete on all tables in schema public to kuntur_app;
grant usage, select on all sequences in schema public to kuntur_app;
alter default privileges in schema public grant select, insert, update, delete on tables to kuntur_app;
alter default privileges in schema public grant usage, select on sequences to kuntur_app;
revoke all on flyway_schema_history from kuntur_app;
-- Append-only is also a privilege, not only a trigger: the app role cannot even attempt to rewrite history.
revoke update, delete, truncate on postings, journal_entries, audit_log from kuntur_app;

-- app.tenant_id is set per transaction by the application: a tenant uuid, 'system' for platform jobs
-- (outbox relay, provisioning, recovery sweeps) or empty/unset, which matches no row (fail-closed).
do
$$
    declare
        t text;
    begin
        foreach t in array array ['roles', 'users', 'refresh_tokens', 'accounts', 'journal_entries', 'postings',
            'payment_batches', 'transfers', 'audit_log']
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
