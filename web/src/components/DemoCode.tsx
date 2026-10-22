import { useEffect, useState } from 'react';

import { DEMO_MFA_SECRETS, totp } from '../lib/totp';

/** For published demo users only: shows what their authenticator app would display right now. */
export function DemoCode({ email }: { email: string }) {
  const secret = DEMO_MFA_SECRETS[email.trim().toLowerCase()];
  const [code, setCode] = useState<string | null>(null);

  useEffect(() => {
    if (!secret) return;
    let alive = true;
    const tick = () => {
      void totp(secret).then((c) => {
        if (alive) setCode(c);
      });
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, [secret]);

  if (!secret || !code) return null;
  return (
    <p className="text-xs text-ink-2">
      Usuario de demostración: tu app de autenticación mostraría{' '}
      <span className="num font-medium text-ink">
        {code.slice(0, 3)} {code.slice(3)}
      </span>
      . Cada código sirve una sola vez.
    </p>
  );
}
