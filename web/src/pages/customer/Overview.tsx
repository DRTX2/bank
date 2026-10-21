import { useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { Button, Empty, ErrorNote, Money, PageHeader, Section, Table } from '../../components/ui';
import { api, apiText } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { downloadText, formatDateTime, groupIban } from '../../lib/format';
import type { Account, Movement } from '../../lib/types';

const movementLabel: Record<Movement['type'], string> = {
  TRANSFER: 'Transferencia',
  CASH_DEPOSIT: 'Depósito',
  CASH_WITHDRAWAL: 'Retiro',
};

export function Overview() {
  const { session } = useAuth();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/api/v1/accounts') });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = accounts.data?.find((a) => a.id === selectedId) ?? accounts.data?.[0];
  const movements = useQuery({
    queryKey: ['movements', selected?.id],
    queryFn: () => api<Movement[]>(`/api/v1/accounts/${selected?.id ?? ''}/movements`),
    enabled: Boolean(selected),
  });
  const [statementError, setStatementError] = useState<unknown>(null);

  const total = accounts.data?.reduce((sum, a) => sum + a.balance, 0) ?? 0;
  const firstName = session?.user.fullName.split(' ')[0] ?? '';

  async function downloadStatement(account: Account) {
    setStatementError(null);
    try {
      const xml = await apiText(`/api/v1/accounts/${account.id}/statements/camt053`);
      downloadText(`estado-${account.number}.xml`, xml);
    } catch (e) {
      setStatementError(e);
    }
  }

  return (
    <>
      <PageHeader
        title={`Hola, ${firstName}`}
        lead="Tus saldos se actualizan en el momento en que se registra cada movimiento."
        actions={
          <Link to="/banca/transferir">
            <Button>Nueva transferencia</Button>
          </Link>
        }
      />

      <section aria-label="Saldo total" className="mb-10">
        <p className="text-ink-2">Saldo disponible en todas tus cuentas</p>
        <p className="num mt-1 text-5xl font-semibold tracking-tight sm:text-6xl">
          <Money amount={total} currency={selected?.currency ?? 'USD'} />
        </p>
      </section>

      <Section title="Cuentas">
        <ErrorNote error={accounts.error} />
        {accounts.data?.length === 0 && (
          <Empty>Aún no tienes cuentas. Acércate a una agencia para abrir la primera.</Empty>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          {accounts.data?.map((a) => {
            const active = a.id === selected?.id;
            return (
              <button
                type="button"
                key={a.id}
                aria-pressed={active}
                onClick={() => {
                  setSelectedId(a.id);
                }}
                className={`rounded-lg border p-5 text-left transition-colors ${
                  active
                    ? 'border-lapis bg-surface ring-1 ring-lapis'
                    : 'border-line bg-surface hover:border-ink-2'
                }`}
              >
                <span className="flex items-baseline justify-between gap-2">
                  <span className="font-medium">
                    {a.alias ?? (a.type === 'SAVINGS' ? 'Ahorros' : 'Corriente')}
                  </span>
                  {a.status !== 'ACTIVE' && (
                    <span className="text-xs text-risk">
                      {a.status === 'FROZEN' ? 'Congelada' : 'Cerrada'}
                    </span>
                  )}
                </span>
                <span className="num mt-1 block text-sm text-ink-2">{groupIban(a.number)}</span>
                <Money
                  amount={a.balance}
                  currency={a.currency}
                  className="mt-4 block text-2xl font-semibold"
                />
              </button>
            );
          })}
        </div>
      </Section>

      {selected && (
        <Section
          title={`Movimientos de ${selected.alias ?? 'la cuenta'}`}
          aside={
            <Button variant="quiet" onClick={() => void downloadStatement(selected)}>
              <Download className="size-4" aria-hidden /> Estado de cuenta (camt.053)
            </Button>
          }
        >
          <ErrorNote error={statementError ?? movements.error} />
          {movements.data?.length === 0 && <Empty>Sin movimientos en los últimos 30 días.</Empty>}
          {movements.data && movements.data.length > 0 && (
            <Table head={['Fecha', 'Concepto', 'Tipo', 'Monto', 'Saldo']}>
              {movements.data.map((m) => (
                <tr key={m.id}>
                  <td className="whitespace-nowrap px-4 py-3 text-ink-2">{formatDateTime(m.bookedAt)}</td>
                  <td className="px-4 py-3">{m.description ?? '—'}</td>
                  <td className="px-4 py-3 text-ink-2">{movementLabel[m.type]}</td>
                  <td className="px-4 py-3 text-right">
                    <Money amount={m.amount} currency={selected.currency} signed />
                  </td>
                  <td className="px-4 py-3 text-right text-ink-2">
                    <Money amount={m.balanceAfter} currency={selected.currency} />
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Section>
      )}
    </>
  );
}
