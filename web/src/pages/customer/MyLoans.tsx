import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { Can } from '../../components/Can';
import { LoanSchedule } from '../../components/LoanSchedule';
import { Button, Empty, ErrorNote, Money, PageHeader, Section } from '../../components/ui';
import { api } from '../../lib/api';
import { formatBusinessDate, money, percent } from '../../lib/format';
import type { Loan, Payoff } from '../../lib/types';

export function MyLoans() {
  const loans = useQuery({ queryKey: ['loans'], queryFn: () => api<Loan[]>('/api/v1/loans') });
  const [selected, setSelected] = useState<string | null>(null);
  const current = selected ?? loans.data?.[0]?.id ?? null;

  return (
    <>
      <PageHeader
        title="Mis créditos"
        lead="Cada cuota se debita de tu cuenta en el cierre del día en que vence. Mantén el saldo para no quedar en mora."
      />
      <ErrorNote error={loans.error} />
      {loans.data?.length === 0 && <Empty>No tienes créditos. Puedes simular uno en Simuladores.</Empty>}
      <div className="mb-10 grid gap-4 md:grid-cols-2">
        {loans.data?.map((l) => (
          <button
            key={l.id}
            type="button"
            aria-pressed={current === l.id}
            onClick={() => {
              setSelected(l.id);
            }}
            className={`flex flex-col gap-1 rounded-lg border bg-surface p-5 text-left ${current === l.id ? 'border-lapis' : 'border-line'}`}
          >
            <span className="text-sm text-ink-2">
              Crédito a {l.termMonths} meses, <span className="num">{percent(l.annualRate)}</span> anual
            </span>
            <span className="text-2xl font-semibold">
              <Money amount={l.outstanding} currency="USD" />
            </span>
            <span className="text-sm text-ink-2">
              {l.status === 'PENDING_APPROVAL' ? (
                'En aprobación: el dinero llega a tu cuenta cuando se apruebe'
              ) : l.status === 'REJECTED' ? (
                'No aprobado'
              ) : l.status === 'PAID_OFF' ? (
                'Cancelado'
              ) : (
                <>
                  pendiente de <Money amount={l.principal} currency="USD" />
                </>
              )}
            </span>
            {l.next && (
              <span className="mt-2 text-sm">
                Próxima cuota: <Money amount={l.next.amount} currency="USD" className="font-medium" /> el{' '}
                {formatBusinessDate(l.next.dueDate)}
              </span>
            )}
          </button>
        ))}
      </div>
      {current && loans.data?.find((l) => l.id === current)?.status === 'ACTIVE' && (
        <Can any={['loans:repay:own']}>
          <PayoffPanel loanId={current} />
        </Can>
      )}
      {current && loans.data?.find((l) => l.id === current)?.status !== 'PENDING_APPROVAL' && (
        <Section title="Tabla de pagos">
          <LoanSchedule loanId={current} />
        </Section>
      )}
    </>
  );
}

/** Settle the loan today: outstanding principal plus the interest accrued in the running period, nothing more. */
function PayoffPanel({ loanId }: { loanId: string }) {
  const queryClient = useQueryClient();
  const quote = useQuery({
    queryKey: ['payoff', loanId],
    queryFn: () => api<Payoff>(`/api/v1/loans/${loanId}/payoff`),
  });
  const pay = useMutation({
    mutationFn: () => api<Loan>(`/api/v1/loans/${loanId}/payoff`, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries();
    },
  });
  const q = quote.data;

  return (
    <div className="mb-10">
      <Section title="Pagar por anticipado">
        <div className="flex max-w-md flex-col gap-4">
          <ErrorNote error={quote.error ?? pay.error} />
          {q && (
            <dl className="grid grid-cols-[1fr_auto] gap-y-1 text-sm">
              <dt className="text-ink-2">Capital pendiente</dt>
              <dd className="text-right">
                <Money amount={q.principal} currency="USD" />
              </dd>
              <dt className="text-ink-2">Interés del período hasta hoy</dt>
              <dd className="text-right">
                <Money amount={q.interest} currency="USD" />
              </dd>
              <dt className="font-medium">Total a pagar hoy</dt>
              <dd className="text-right font-medium">
                <Money amount={q.total} currency="USD" />
              </dd>
            </dl>
          )}
          <p className="text-sm text-ink-2">
            Se debita de la cuenta del crédito y cierra todas las cuotas pendientes. No hay penalidad por
            pagar antes.
          </p>
          <Button
            disabled={!q || pay.isPending}
            onClick={() => {
              pay.mutate();
            }}
          >
            {q ? `Pagar ${money(q.total)} ahora` : 'Pagar ahora'}
          </Button>
        </div>
      </Section>
    </div>
  );
}
