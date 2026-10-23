import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { LoanSchedule } from '../../components/LoanSchedule';
import { Empty, ErrorNote, Money, PageHeader, Section } from '../../components/ui';
import { api } from '../../lib/api';
import { formatBusinessDate, percent } from '../../lib/format';
import type { Loan } from '../../lib/types';

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
              {l.status === 'PAID_OFF' ? (
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
      {current && (
        <Section title="Tabla de pagos">
          <LoanSchedule loanId={current} />
        </Section>
      )}
    </>
  );
}
