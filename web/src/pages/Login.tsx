import { useQuery } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';
import { Navigate } from 'react-router';

import { Button, ErrorNote, Field, Select } from '../components/ui';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';

const DEMO_PASSWORD = 'Kuntur2026!';

const demoUsers = [
  {
    tenant: 'ANDN',
    email: 'ana@andina.ec',
    role: 'Clienta empresarial',
    note: 'Transferencias y pagos ISO 20022',
  },
  { tenant: 'ANDN', email: 'luis@andina.ec', role: 'Cliente', note: 'Banca personal y simuladores' },
  { tenant: 'ANDN', email: 'cajero@andina.ec', role: 'Cajero', note: 'Depósitos, retiros, alta de clientes' },
  { tenant: 'ANDN', email: 'gerente@andina.ec', role: 'Gerente', note: 'Aprueba transferencias grandes' },
  {
    tenant: 'ANDN',
    email: 'cumplimiento@andina.ec',
    role: 'Oficial de cumplimiento',
    note: 'Revisa alertas de fraude',
  },
  {
    tenant: 'ANDN',
    email: 'admin@andina.ec',
    role: 'Administradora',
    note: 'Usuarios, roles, simulador de tráfico',
  },
  {
    tenant: 'ANDN',
    email: 'auditor@andina.ec',
    role: 'Auditora',
    note: 'Solo lectura y verificación de auditoría',
  },
  { tenant: 'KNTR', email: 'root@kuntur.dev', role: 'Operador de plataforma', note: 'Alta de instituciones' },
];

export function Login() {
  const { session, login } = useAuth();
  const tenants = useQuery({
    queryKey: ['tenants-public'],
    queryFn: () => api<{ code: string; name: string }[]>('/api/v1/auth/tenants'),
  });
  const [tenant, setTenant] = useState('ANDN');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (session) return <Navigate to="/" replace />;

  async function submit(e: SyntheticEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(tenant, email, password);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <div className="relative flex flex-col bg-lapis text-on-lapis">
        <div className="textile h-4" aria-hidden />
        <div className="flex flex-1 flex-col justify-between gap-10 px-8 py-10 sm:px-14 lg:py-16">
          <p className="text-2xl font-semibold tracking-tight">Kuntur</p>
          <div className="max-w-md">
            <h1 className="text-4xl font-semibold leading-[1.1] tracking-tight sm:text-5xl">
              Banca central para cooperativas que vuelan alto.
            </h1>
            <p className="mt-5 text-lg text-on-lapis/80">
              Libro mayor de partida doble, pagos ISO 20022 y un motor de riesgo que explica cada decisión.
              Cada institución, aislada en su propio espacio.
            </p>
          </div>
          <dl className="grid max-w-md grid-cols-3 gap-6 text-sm">
            <div>
              <dt className="text-on-lapis/70">Liquidación</dt>
              <dd className="mt-1 font-medium">En línea, 24/7</dd>
            </div>
            <div>
              <dt className="text-on-lapis/70">Mensajería</dt>
              <dd className="mt-1 font-medium">ISO 20022</dd>
            </div>
            <div>
              <dt className="text-on-lapis/70">Control</dt>
              <dd className="mt-1 font-medium">Cuatro ojos</dd>
            </div>
          </dl>
        </div>
      </div>

      <div className="flex items-center justify-center px-6 py-12 sm:px-12">
        <div className="w-full max-w-md">
          <h2 className="text-2xl font-semibold tracking-tight">Ingresa a tu institución</h2>
          <form onSubmit={(e) => void submit(e)} className="mt-6 flex flex-col gap-4">
            <Select
              label="Institución"
              value={tenant}
              onChange={(e) => {
                setTenant(e.target.value);
              }}
            >
              {(tenants.data ?? [{ code: 'ANDN', name: 'Cooperativa Andina' }]).map((t) => (
                <option key={t.code} value={t.code}>
                  {t.name}
                </option>
              ))}
            </Select>
            <Field
              label="Correo"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
              }}
            />
            <Field
              label="Contraseña"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
              }}
            />
            <ErrorNote error={error} />
            <Button type="submit" disabled={busy} className="mt-2 py-2.5">
              {busy ? 'Ingresando…' : 'Ingresar'}
            </Button>
          </form>

          <div className="mt-10">
            <h3 className="font-medium">Explora con un rol de demostración</h3>
            <p className="mt-1 text-sm text-ink-2">
              Cada perfil ve un producto distinto según sus permisos. Contraseña:{' '}
              <code className="num">{DEMO_PASSWORD}</code>
            </p>
            <ul className="mt-3 divide-y divide-line rounded-lg border border-line bg-surface">
              {demoUsers.map((u) => (
                <li key={u.email}>
                  <button
                    type="button"
                    className="flex w-full items-baseline justify-between gap-3 px-4 py-2.5 text-left hover:bg-surface-2"
                    onClick={() => {
                      setTenant(u.tenant);
                      setEmail(u.email);
                      setPassword(DEMO_PASSWORD);
                    }}
                  >
                    <span>
                      <span className="block text-sm font-medium">{u.role}</span>
                      <span className="block text-xs text-ink-2">{u.note}</span>
                    </span>
                    <span className="shrink-0 text-xs text-ink-2">{u.email}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
