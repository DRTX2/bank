import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { QrCode } from '../components/QrCode';
import { Button, ErrorNote, Field, PageHeader, Section } from '../components/ui';
import { api } from '../lib/api';
import type { Profile } from '../lib/types';

interface Enrollment {
  secret: string;
  otpauthUri: string;
}

/** Self-service second factor. Approvers need it: approving a transfer asks for a fresh code. */
export function Security() {
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ['me'], queryFn: () => api<Profile>('/api/v1/me') });
  const [enrollment, setEnrollment] = useState<Enrollment | null>(null);
  const [code, setCode] = useState('');

  const done = () => {
    setEnrollment(null);
    setCode('');
    void queryClient.invalidateQueries({ queryKey: ['me'] });
  };
  const start = useMutation({
    mutationFn: () => api<Enrollment>('/api/v1/me/mfa/enrollment', { method: 'POST' }),
    onSuccess: setEnrollment,
  });
  const activate = useMutation({
    mutationFn: () => api('/api/v1/me/mfa/activation', { method: 'POST', body: { code } }),
    onSuccess: done,
  });
  const disable = useMutation({
    mutationFn: () => api('/api/v1/me/mfa', { method: 'DELETE', body: { code } }),
    onSuccess: done,
  });

  const codeField = (
    <Field
      label="Código de verificación"
      hint="Las 6 cifras que muestra tu app ahora mismo."
      inputMode="numeric"
      autoComplete="one-time-code"
      maxLength={6}
      value={code}
      onChange={(e) => {
        setCode(e.target.value.replace(/\D/g, ''));
      }}
    />
  );

  return (
    <>
      <PageHeader
        title="Seguridad"
        lead="La verificación en dos pasos pide un código de tu teléfono además de la contraseña. Quien aprueba transferencias la necesita."
      />
      <ErrorNote error={me.error ?? start.error ?? activate.error ?? disable.error} />
      {me.data && (
        <Section title="Verificación en dos pasos">
          {me.data.mfaEnabled ? (
            <form
              className="flex max-w-md flex-col gap-4"
              onSubmit={(e) => {
                e.preventDefault();
                disable.mutate();
              }}
            >
              <p className="font-medium text-ok">Activa. Cada ingreso y cada aprobación piden un código.</p>
              {codeField}
              <Button type="submit" variant="quiet" disabled={code.length !== 6 || disable.isPending}>
                Desactivar verificación en dos pasos
              </Button>
            </form>
          ) : enrollment ? (
            <form
              className="flex max-w-md flex-col gap-4"
              onSubmit={(e) => {
                e.preventDefault();
                activate.mutate();
              }}
            >
              <p>
                Escanea el código QR con Google Authenticator, Microsoft Authenticator u otra app compatible.
                En el teléfono puedes{' '}
                <a className="text-lapis underline" href={enrollment.otpauthUri}>
                  abrirla directamente en la app
                </a>
                .
              </p>
              <QrCode
                value={enrollment.otpauthUri}
                label="Código QR para registrar la verificación en dos pasos"
              />
              <p className="text-sm text-ink-2">¿No puedes escanear? Ingresa esta clave a mano:</p>
              <p className="num rounded-md border border-line bg-surface px-3 py-2 text-lg tracking-wide break-all">
                {enrollment.secret.replace(/(.{4})/g, '$1 ').trim()}
              </p>
              {codeField}
              <Button type="submit" disabled={code.length !== 6 || activate.isPending}>
                Confirmar y activar
              </Button>
            </form>
          ) : (
            <div className="flex max-w-md flex-col items-start gap-4">
              <p className="text-ink-2">Todavía no está activa en tu usuario.</p>
              <Button
                onClick={() => {
                  start.mutate();
                }}
                disabled={start.isPending}
              >
                Activar verificación en dos pasos
              </Button>
            </div>
          )}
        </Section>
      )}
    </>
  );
}
