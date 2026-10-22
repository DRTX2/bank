-- TOTP second factor. mfa_last_step makes every code single-use (RFC 6238 section 5.2).
alter table users
    add column mfa_secret     varchar(64),
    add column mfa_enabled_at timestamptz,
    add column mfa_last_step  bigint not null default 0;
