import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { DemoCode } from '../../components/DemoCode';
import { RiskMeter } from '../../components/RiskMeter';
import { Button, Empty, ErrorNote, Money, PageHeader } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { formatDateTime, groupIban } from '../../lib/format';
import type { Transfer } from '../../lib/types';

/**
 * Two queues, one screen design: maker-checker approvals (transfers:approve) and risk-engine holds (risk:review).
 * The API enforces four-eyes; the UI only explains it up front.
 */
export function ReviewQueue({ mode }: { mode: 'approval' | 'risk' }) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const status = mode === 'approval' ? 'PENDING_APPROVAL' : 'HELD_FOR_REVIEW';
  const queue = useQuery({
    queryKey: ['transfers', status],
    queryFn: () => api<Transfer[]>(`/api/v1/transfers?status=${status}&size=100`),
    refetchInterval: 5000,
  });
  const [notes, setNotes] = useState<Record<string, string>>({});
  // Step-up: approving moves money, so it needs a fresh second-factor code, not just a valid session.
  const [otp, setOtp] = useState('');

  const decide = useMutation({
    mutationFn: ({ id, approve }: { id: string; approve: boolean }) => {
      const note = notes[id] ?? '';
      if (mode === 'risk') {
        return api<Transfer>(`/api/v1/transfers/${id}/review`, {
          method: 'POST',
          body: { release: approve, note },
        });
      }
      return api<Transfer>(`/api/v1/transfers/${id}/${approve ? 'approve' : 'reject'}`, {
        method: 'POST',
        body: approve ? { note, otp } : { note },
      });
    },
    onSuccess: () => {
      setOtp('');
      void queryClient.invalidateQueries({ queryKey: ['transfers'] });
    },
  });

  const copy =
    mode === 'approval'
      ? {
          title: 'Aprobaciones',
          lead: 'Transferencias que superan el umbral de la institución. Quien la inició nunca puede aprobarla (control de cuatro ojos).',
          empty: 'No hay transferencias esperando aprobación.',
          approve: 'Aprobar y liquidar',
          reject: 'Rechazar',
        }
      : {
          title: 'Revisión de riesgo',
          lead: 'Transferencias detenidas por el motor de riesgo. Cada señal explica qué la hizo sospechosa.',
          empty: 'Sin alertas pendientes. El motor de riesgo no ha detenido ninguna transferencia.',
          approve: 'Liberar',
          reject: 'Bloquear por fraude',
        };

  return (
    <>
      <PageHeader title={copy.title} lead={copy.lead} />
      {mode === 'approval' && (
        <div className="mb-6 flex max-w-md flex-col gap-1.5">
          <label htmlFor="step-up" className="text-sm font-medium">
            Código de verificación para aprobar
          </label>
          <input
            id="step-up"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            className="num rounded-md border border-line bg-surface px-3 py-2 outline-none focus:border-lapis"
            value={otp}
            onChange={(e) => {
              setOtp(e.target.value.replace(/\D/g, ''));
            }}
          />
          <DemoCode email={session?.user.email ?? ''} />
        </div>
      )}
      <ErrorNote error={queue.error ?? decide.error} />
      {queue.data?.length === 0 && <Empty>{copy.empty}</Empty>}
      <ul className="flex flex-col gap-4">
        {queue.data?.map((t) => {
          const own = t.initiatedBy === session?.user.id;
          return (
            <li
              key={t.id}
              className="grid gap-6 rounded-lg border border-line bg-surface p-5 md:grid-cols-[1fr_minmax(0,380px)]"
            >
              <div>
                <p className="text-2xl font-semibold">
                  <Money amount={t.amount} currency={t.currency} />
                </p>
                <p className="num mt-1 text-sm text-ink-2">
                  {groupIban(t.sourceAccountNumber)} → {groupIban(t.destinationAccountNumber)}
                </p>
                <p className="mt-2">{t.remittanceInfo ?? 'Sin concepto'}</p>
                <p className="mt-1 text-sm text-ink-2">Recibida {formatDateTime(t.createdAt)}</p>
                {t.reviewNote && <p className="mt-2 text-sm">Nota de revisión: {t.reviewNote}</p>}
                <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-end">
                  <label className="flex flex-1 flex-col gap-1.5 text-sm font-medium">
                    Nota para auditoría
                    <input
                      className="rounded-md border border-line bg-surface px-3 py-2 font-normal outline-none focus:border-lapis"
                      value={notes[t.id] ?? ''}
                      onChange={(e) => {
                        setNotes({ ...notes, [t.id]: e.target.value });
                      }}
                      placeholder={
                        mode === 'risk'
                          ? 'Cliente confirmó por teléfono'
                          : 'Soporte de la operación verificado'
                      }
                    />
                  </label>
                  <div className="flex gap-2">
                    <Button
                      disabled={
                        own ||
                        decide.isPending ||
                        (mode === 'risk' && !notes[t.id]) ||
                        (mode === 'approval' && otp.length !== 6)
                      }
                      onClick={() => {
                        decide.mutate({ id: t.id, approve: true });
                      }}
                    >
                      {copy.approve}
                    </Button>
                    <Button
                      variant="danger"
                      disabled={own || decide.isPending || (mode === 'risk' && !notes[t.id])}
                      onClick={() => {
                        decide.mutate({ id: t.id, approve: false });
                      }}
                    >
                      {copy.reject}
                    </Button>
                  </div>
                </div>
                {own && (
                  <p className="mt-2 text-sm text-quinoa">La iniciaste tú: otra persona debe decidir.</p>
                )}
              </div>
              <RiskMeter score={t.riskScore} signals={t.riskSignals} />
            </li>
          );
        })}
      </ul>
    </>
  );
}
