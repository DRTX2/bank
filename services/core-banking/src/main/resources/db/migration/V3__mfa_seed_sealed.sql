-- TOTP seeds are now AES-GCM sealed ("v1:" + base64 of iv and ciphertext), which no longer fits in 64 characters.
-- Rows written before this migration stay readable: the converter treats values without the prefix as plain text.
alter table users
    alter column mfa_secret type varchar(255);
