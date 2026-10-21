import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';

import { Can } from '../../components/Can';
import { Button, ErrorNote, Field, Money, PageHeader, Section, Table } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { groupIban } from '../../lib/format';
import type { Account } from '../../lib/types';

export function Customers() {
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/api/v1/accounts') });
  const [filter, setFilter] = useState('');
  const [cash, setCash] = useState<{ account: Account; kind: 'deposits' | 'withdrawals' } | null>(null);
  const [amount, setAmount] = useState('');

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['accounts'] });
  const cashOp = useMutation({
    mutationFn: () =>
      api(`/api/v1/accounts/${cash?.account.id ?? ''}/${cash?.kind ?? 'deposits'}`, {
        method: 'POST',
        body: { amount: Number(amount), reference: 'VENTANILLA' },
      }),
    onSuccess: () => {
      setCash(null);
      setAmount('');
      void refresh();
    },
  });
  const freeze = useMutation({
    mutationFn: (a: Account) =>
      api(`/api/v1/accounts/${a.id}/status`, {
        method: 'PATCH',
        body: { status: a.status === 'FROZEN' ? 'ACTIVE' : 'FROZEN' },
      }),
    onSuccess: () => void refresh(),
  });

  const visible = (accounts.data ?? []).filter(
    (a) =>
      `${a.ownerName ?? ''} ${a.number}`.toLowerCase().includes(filter.toLowerCase().replaceAll(' ', '')) ||
      (a.ownerName ?? '').toLowerCase().includes(filter.toLowerCase()),
  );

  return (
    <>
      <PageHeader
        title="Clientes y cuentas"
        lead="Alta de clientes, operaciones de ventanilla y congelamiento de cuentas."
      />
      <Can any={['customers:onboard']}>
        <Onboarding onDone={() => void refresh()} />
      </Can>

      <Section
        title="Cuentas"
        aside={
          <input
            aria-label="Buscar por nombre o número"
            placeholder="Buscar por nombre o número"
            className="w-64 rounded-md border border-line bg-surface px-3 py-1.5 text-sm"
            value={filter}
            onChange={(e) => {
              setFilter(e.target.value);
            }}
          />
        }
      >
        <ErrorNote error={accounts.error ?? freeze.error} />
        {cash && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              cashOp.mutate();
            }}
            className="mb-4 flex flex-wrap items-end gap-3 rounded-lg border border-lapis bg-surface p-4"
          >
            <p className="w-full text-sm">
              {cash.kind === 'deposits' ? 'Depósito en' : 'Retiro de'}{' '}
              <strong>{cash.account.ownerName}</strong> ({groupIban(cash.account.number)})
            </p>
            <Field
              label="Monto"
              type="number"
              min="0.01"
              step="0.01"
              required
              value={amount}
              onChange={(e) => {
                setAmount(e.target.value);
              }}
            />
            <Button type="submit" disabled={cashOp.isPending}>
              {cash.kind === 'deposits' ? 'Registrar depósito' : 'Registrar retiro'}
            </Button>
            <Button
              type="button"
              variant="quiet"
              onClick={() => {
                setCash(null);
              }}
            >
              Cancelar
            </Button>
            <div className="w-full">
              <ErrorNote error={cashOp.error} />
            </div>
          </form>
        )}
        <Table head={['Titular', 'Número', 'Tipo', 'Saldo', 'Estado', 'Acciones']}>
          {visible.map((a) => (
            <tr key={a.id}>
              <td className="px-4 py-3 font-medium">{a.ownerName}</td>
              <td className="num px-4 py-3 text-xs">{groupIban(a.number)}</td>
              <td className="px-4 py-3 text-ink-2">{a.type === 'SAVINGS' ? 'Ahorros' : 'Corriente'}</td>
              <td className="px-4 py-3 text-right">
                <Money amount={a.balance} currency={a.currency} />
              </td>
              <td className={`px-4 py-3 ${a.status === 'ACTIVE' ? 'text-ok' : 'text-risk'}`}>
                {a.status === 'ACTIVE' ? 'Activa' : a.status === 'FROZEN' ? 'Congelada' : 'Cerrada'}
              </td>
              <td className="px-4 py-3">
                <div className="flex flex-wrap gap-1.5">
                  {can('cash:deposit') && (
                    <Button
                      variant="quiet"
                      className="px-2 py-1 text-xs"
                      onClick={() => {
                        setCash({ account: a, kind: 'deposits' });
                      }}
                    >
                      Depósito
                    </Button>
                  )}
                  {can('cash:withdraw') && (
                    <Button
                      variant="quiet"
                      className="px-2 py-1 text-xs"
                      onClick={() => {
                        setCash({ account: a, kind: 'withdrawals' });
                      }}
                    >
                      Retiro
                    </Button>
                  )}
                  {can('accounts:freeze') && (
                    <Button
                      variant="quiet"
                      className="px-2 py-1 text-xs"
                      onClick={() => {
                        freeze.mutate(a);
                      }}
                    >
                      {a.status === 'FROZEN' ? 'Descongelar' : 'Congelar'}
                    </Button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </Table>
      </Section>
    </>
  );
}

function Onboarding({ onDone }: { onDone: () => void }) {
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    nationalId: '',
    password: '',
    corporate: false,
  });
  const create = useMutation({
    mutationFn: () => api<Account>('/api/v1/customers', { method: 'POST', body: form }),
    onSuccess: () => {
      setForm({ fullName: '', email: '', nationalId: '', password: '', corporate: false });
      onDone();
    },
  });
  const set = (k: 'fullName' | 'email' | 'nationalId' | 'password') => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value });
  };
  function submit(e: SyntheticEvent) {
    e.preventDefault();
    create.mutate();
  }
  return (
    <Section title="Registrar cliente">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Nombre completo" required value={form.fullName} onChange={set('fullName')} />
        <Field
          label="Cédula"
          required
          pattern="\d{10}"
          title="10 dígitos"
          value={form.nationalId}
          onChange={set('nationalId')}
        />
        <Field label="Correo" type="email" required value={form.email} onChange={set('email')} />
        <Field
          label="Contraseña inicial"
          type="password"
          required
          minLength={10}
          value={form.password}
          onChange={set('password')}
          hint="Mínimo 10 caracteres, letras y números"
        />
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.corporate}
            onChange={(e) => {
              setForm({ ...form, corporate: e.target.checked });
            }}
          />
          Cliente empresarial (habilita pagos masivos)
        </label>
        <div className="flex items-end sm:col-span-2 lg:col-span-3 lg:justify-end">
          <Button type="submit" disabled={create.isPending}>
            Registrar y abrir cuenta de ahorros
          </Button>
        </div>
        <div className="sm:col-span-2 lg:col-span-4">
          <ErrorNote error={create.error} />
          {create.data && <p className="text-sm text-ok">Cuenta {groupIban(create.data.number)} abierta.</p>}
        </div>
      </form>
    </Section>
  );
}
