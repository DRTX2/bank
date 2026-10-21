import { afterEach, describe, expect, it, vi } from 'vitest';

import { api, ApiError, getSession, setSession } from '../lib/api';
import type { Session } from '../lib/types';

const session = (access: string, refresh: string): Session => ({
  accessToken: access,
  refreshToken: refresh,
  expiresIn: 600,
  user: {
    id: 'u',
    email: 'a@b.c',
    fullName: 'A',
    tenantId: 't',
    tenantCode: 'ANDN',
    tenantName: 'Andina',
    roles: [],
    permissions: [],
  },
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

afterEach(() => {
  vi.restoreAllMocks();
  setSession(null);
});

describe('api client', () => {
  it('refreshes once for concurrent 401s and retries with the new token', async () => {
    setSession(session('old', 'r1'));
    let refreshCalls = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      const auth = new Headers(init?.headers).get('Authorization');
      if (url.endsWith('/auth/refresh')) {
        refreshCalls++;
        return Promise.resolve(json(200, session('new', 'r2')));
      }
      return Promise.resolve(
        auth === 'Bearer new' ? json(200, { ok: true }) : json(401, { code: 'UNAUTHORIZED' }),
      );
    });

    const results = await Promise.all([api('/api/v1/a'), api('/api/v1/b'), api('/api/v1/c')]);

    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }]);
    expect(refreshCalls).toBe(1);
    expect(getSession()?.refreshToken).toBe('r2');
  });

  it('surfaces RFC 9457 problem details with the ISO 20022 reason', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      json(422, { code: 'INSUFFICIENT_FUNDS', detail: 'Insufficient funds', isoReason: 'AM04' }),
    );
    const error = await api('/api/v1/transfers', { method: 'POST', body: {} }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 422, code: 'INSUFFICIENT_FUNDS', isoReason: 'AM04' });
  });

  it('drops the session when refresh fails', async () => {
    setSession(session('old', 'stolen'));
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(401, { code: 'INVALID_REFRESH_TOKEN' }));
    await expect(api('/api/v1/accounts')).rejects.toBeInstanceOf(ApiError);
    expect(getSession()).toBeNull();
  });
});
