# ADR-0008: TOTP second factor with step-up for approvals

**Status:** accepted

## Context
A stolen password or a stolen access token was enough to approve a high-value transfer: four-eyes stops one
insider, not an attacker who holds the approver's session.

## Decision
- **TOTP (RFC 6238)**: HMAC-SHA1, 6 digits, 30 s steps, which works with any authenticator app. It is implemented
  on the JDK (`identity/Totp`, about 40 lines) instead of pulling a library onto the login path. The secret is 160
  bits, as RFC 4226 recommends.
- **Login**: users with MFA get `MFA_REQUIRED` after a correct password and resend the form with `otp`. A wrong
  code counts towards the same lockout as a wrong password, so the 10^6 code space cannot be brute-forced.
- **Step-up**: approving a four-eyes transfer needs a *fresh* code in the request (`otp`), on top of the
  `transfers:approve` permission. Approvers without MFA get `MFA_NOT_ENROLLED`. Rejecting does not need a code,
  because it moves no money.
- **Single use**: the last accepted time step is stored per user (`mfa_last_step`). A code is accepted once,
  inside a ±1 step drift window, so a phished or shoulder-surfed code cannot be replayed.
- Step-up failures return **403** `STEP_UP_FAILED`, not 401, so the SPA does not mistake them for an expired
  session and try to refresh.

## Consequences
- A manager who logs in and approves within the same 30 s window waits for the next code. That is the price of
  single-use codes, and banking apps behave the same way.
- The demo seeds `gerente@andina.ec` with a published secret, and the UI shows that user's current code, so
  reviewers can try the flow without a phone. Real users enroll under *Seguridad*.
- Shortcut: the TOTP seed is stored in plain text in the KMS-encrypted database. Before production it should be
  envelope-encrypted with a KMS data key. WebAuthn/passkeys would add phishing resistance and are the next step.
