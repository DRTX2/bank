import { createContext, use } from 'react';

import type { Session } from './types';

export interface AuthState {
  session: Session | null;
  /** Rejects with ApiError MFA_REQUIRED when the user has a second factor and no `otp` was given. */
  login: (tenantCode: string, email: string, password: string, otp?: string) => Promise<void>;
  logout: () => Promise<void>;
  can: (...permissions: string[]) => boolean;
}

export const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const ctx = use(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
