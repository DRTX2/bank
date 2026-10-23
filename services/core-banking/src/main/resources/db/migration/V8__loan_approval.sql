-- Loans above the institution's four-eyes threshold wait for a second manager, who approves with a fresh MFA code.
-- The schedule and the disbursement happen at approval.
alter table loans
    add column decided_by_email varchar(254),
    add column decided_at       timestamptz,
    add column decision_note    varchar(255);
