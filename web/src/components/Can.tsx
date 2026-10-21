import type { ReactNode } from 'react';

import { useAuth } from '../lib/auth';

/** Renders children only if the user holds any of the permissions. UI hint only: the API enforces the real rule. */
export function Can({ any, children }: { any: string[]; children: ReactNode }) {
  const { can } = useAuth();
  return can(...any) ? children : null;
}
