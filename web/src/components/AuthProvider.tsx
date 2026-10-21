import { useEffect, useMemo, useState, type ReactNode } from 'react';

import { api, getSession, onSessionChange, setSession } from '../lib/api';
import { AuthContext, type AuthState } from '../lib/auth';
import type { Session } from '../lib/types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setState] = useState<Session | null>(getSession);

  useEffect(() => onSessionChange(setState), []);

  const value = useMemo<AuthState>(
    () => ({
      session,
      async login(tenantCode, email, password) {
        setSession(
          await api<Session>('/api/v1/auth/login', { method: 'POST', body: { tenantCode, email, password } }),
        );
      },
      async logout() {
        const current = getSession();
        setSession(null);
        if (current) {
          await api('/api/v1/auth/logout', {
            method: 'POST',
            body: { refreshToken: current.refreshToken },
          }).catch(() => undefined);
        }
      },
      can: (...permissions) => permissions.some((p) => session?.user.permissions.includes(p) ?? false),
    }),
    [session],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
