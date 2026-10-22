import { describe, expect, it } from 'vitest';

import { totp } from '../lib/totp';

describe('totp', () => {
  // RFC 6238 appendix B seed "12345678901234567890" (base32), last 6 of the published 8 digits
  const secret = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';

  it('matches the RFC 6238 test vectors, like the server does', async () => {
    expect(await totp(secret, 59_000)).toBe('287082');
    expect(await totp(secret, 1_111_111_109_000)).toBe('081804');
    expect(await totp(secret, 2_000_000_000_000)).toBe('279037');
  });
});
