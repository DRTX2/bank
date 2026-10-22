import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { Can } from '../../components/Can';
import { StatusPill } from '../../components/StatusPill';
import { Button, Empty, ErrorNote, Field, Money, PageHeader, Section } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { groupIban } from '../../lib/format';
import { useStomp } from '../../lib/realtime';
import type { PaymentEvent, TransferStatus } from '../../lib/types';

interface Dashboard {
  transfers: Record<TransferStatus, { count: number; amount: number }>;
  customerAccounts: number;
  customerDeposits: number;
}

interface Run {
  id: string;
  requested: number;
  processed: number;
  completed: number;
  held: number;
  rejected: number;
  failed: number;
  finished: boolean;
}

const eventLabel: Record<string, string> = {
  TransferCompleted: 'Liquidada',
  TransferFailed: 'Fallida',
  TransferRejected: 'Rechazada',
  TransferAwaitingAction: 'Detenida',
};

export function Operations() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const dashboard = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api<Dashboard>('/api/v1/ops/dashboard'),
    refetchInterval: 5000,
  });
  const [feed, setFeed] = useState<PaymentEvent[]>([]);
  const [runId, setRunId] = useState<string | null>(null);
  const [count, setCount] = useState('120');
  const [fraud, setFraud] = useState('0.15');

  useStomp<PaymentEvent>(session ? `/topic/tenant.${session.user.tenantId}.ops` : null, (e) => {
    setFeed((f) => [e, ...f].slice(0, 40));
  });

  const run = useQuery({
    queryKey: ['sim-run', runId],
    queryFn: () => api<Run>(`/api/v1/ops/simulator/runs/${runId ?? ''}`),
    enabled: runId !== null,
    refetchInterval: (q) => (q.state.data?.finished ? false : 1000),
  });

  const start = useMutation({
    mutationFn: () =>
      api<Run>('/api/v1/ops/simulator/runs', {
        method: 'POST',
        body: { transfers: Number(count), fraudRatio: Number(fraud), delayMs: 120 },
      }),
    onSuccess: (r) => {
      setRunId(r.id);
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const t = dashboard.data?.transfers;
  const stopped = (t?.HELD_FOR_REVIEW.count ?? 0) + (t?.PENDING_APPROVAL.count ?? 0);

  return (
    <>
      <PageHeader
        title="Operaciones en vivo"
        lead="Últimas 24 horas. El flujo de pagos llega por WebSocket desde Kafka, sin recargar."
      />

      <ErrorNote error={dashboard.error} />
      {t && dashboard.data && (
        <dl className="mb-10 grid grid-cols-2 gap-x-8 gap-y-6 border-y border-line py-6 md:grid-cols-4">
          <div>
            <dt className="text-sm text-ink-2">Volumen liquidado</dt>
            <dd className="mt-1 text-2xl font-semibold sm:text-3xl">
              <Money amount={t.COMPLETED.amount} currency="USD" />
            </dd>
            <dd className="num text-sm text-ink-2">{t.COMPLETED.count} transferencias</dd>
          </div>
          <div>
            <dt className="text-sm text-ink-2">Detenidas por control</dt>
            <dd className={`num mt-1 text-3xl font-semibold ${stopped > 0 ? 'text-quinoa' : ''}`}>
              {stopped}
            </dd>
            <dd className="num text-sm text-ink-2">
              {t.HELD_FOR_REVIEW.count} riesgo, {t.PENDING_APPROVAL.count} aprobación
            </dd>
          </div>
          <div>
            <dt className="text-sm text-ink-2">Rechazadas o fallidas</dt>
            <dd className="num mt-1 text-3xl font-semibold">{t.REJECTED.count + t.FAILED.count}</dd>
            <dd className="num text-sm text-ink-2">{t.REJECTED.count} por fraude o decisión</dd>
          </div>
          <div>
            <dt className="text-sm text-ink-2">Depósitos de clientes</dt>
            <dd className="mt-1 text-2xl font-semibold sm:text-3xl">
              <Money amount={dashboard.data.customerDeposits} currency="USD" />
            </dd>
            <dd className="num text-sm text-ink-2">{dashboard.data.customerAccounts} cuentas</dd>
          </div>
        </dl>
      )}

      <div className="grid gap-10 lg:grid-cols-[1fr_320px]">
        <Section
          title="Flujo de pagos"
          aside={<span className="text-sm text-ink-2">{feed.length} eventos</span>}
        >
          {feed.length === 0 && (
            <Empty>Esperando pagos. Lanza una simulación o haz una transferencia para verlos aquí.</Empty>
          )}
          <ol className="divide-y divide-line">
            {feed.map((e) => (
              <li
                key={e.eventId}
                className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 py-3 sm:grid-cols-[150px_1fr_auto_auto]"
              >
                <span className="text-sm font-medium">{eventLabel[e.type] ?? e.type}</span>
                <span className="num hidden text-xs text-ink-2 sm:block">
                  {groupIban(e.sourceNumber)} → {groupIban(e.destinationNumber)}
                </span>
                <span className="text-right">
                  <Money amount={e.amountMinor / 100} currency={e.currency} />
                </span>
                <span className="col-span-2 sm:col-span-1">
                  <StatusPill
                    status={e.status}
                    reason={e.failureCode ?? (e.riskScore ? `riesgo ${e.riskScore}` : null)}
                  />
                </span>
              </li>
            ))}
          </ol>
        </Section>

        <Can any={['ops:simulate']}>
          <Section title="Simulador de tráfico">
            <p className="mb-4 text-sm text-ink-2">
              Genera pagos sintéticos entre clientes, con una parte de patrones de fraude (ráfagas, montos
              atípicos, cuentas mula) para ver al motor de riesgo actuar.
            </p>
            <div className="flex flex-col gap-4">
              <Field
                label="Transferencias"
                type="number"
                min="1"
                max="1000"
                value={count}
                onChange={(e) => {
                  setCount(e.target.value);
                }}
              />
              <Field
                label="Proporción de fraude"
                type="number"
                min="0"
                max="0.5"
                step="0.05"
                value={fraud}
                onChange={(e) => {
                  setFraud(e.target.value);
                }}
              />
              <ErrorNote error={start.error} />
              <Button
                onClick={() => {
                  start.mutate();
                }}
                disabled={start.isPending || (run.data !== undefined && !run.data.finished)}
              >
                Iniciar simulación
              </Button>
              {run.data && (
                <dl className="num grid grid-cols-2 gap-2 rounded-md border border-line bg-surface p-3 text-sm">
                  <dt className="text-ink-2">Progreso</dt>
                  <dd>
                    {run.data.processed}/{run.data.requested}
                  </dd>
                  <dt className="text-ink-2">Liquidadas</dt>
                  <dd>{run.data.completed}</dd>
                  <dt className="text-ink-2">Detenidas</dt>
                  <dd>{run.data.held}</dd>
                  <dt className="text-ink-2">Rechazadas</dt>
                  <dd>{run.data.rejected}</dd>
                  <dt className="text-ink-2">Fallidas</dt>
                  <dd>{run.data.failed}</dd>
                </dl>
              )}
            </div>
          </Section>
        </Can>
      </div>
    </>
  );
}
