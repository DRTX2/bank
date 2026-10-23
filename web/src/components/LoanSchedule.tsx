import { useQuery } from '@tanstack/react-query';

import { api } from '../lib/api';
import { formatBusinessDate } from '../lib/format';
import type { InstallmentStatus, LoanDetail } from '../lib/types';
import { ErrorNote, Money, Table } from './ui';

const statusLabel: Record<InstallmentStatus, { text: string; tone: string }> = {
  PAID: { text: 'Pagada', tone: 'text-ok' },
  PENDING: { text: 'Pendiente', tone: 'text-ink-2' },
  OVERDUE: { text: 'Vencida', tone: 'font-medium text-risk' },
};

/** Amortization schedule of one loan, with what was already collected at a day close. */
export function LoanSchedule({ loanId }: { loanId: string }) {
  const detail = useQuery({
    queryKey: ['loan', loanId],
    queryFn: () => api<LoanDetail>(`/api/v1/loans/${loanId}`),
  });
  return (
    <div className="flex flex-col gap-4">
      <ErrorNote error={detail.error} />
      <Table head={['#', 'Vence', 'Capital', 'Interés', 'Cuota', 'Estado']}>
        {detail.data?.schedule.map((i) => (
          <tr key={i.number}>
            <td className="num px-4 py-2.5 text-ink-2">{i.number}</td>
            <td className="whitespace-nowrap px-4 py-2.5">{formatBusinessDate(i.dueDate)}</td>
            <td className="px-4 py-2.5 text-right">
              <Money amount={i.principal} currency="USD" />
            </td>
            <td className="px-4 py-2.5 text-right">
              <Money amount={i.interest} currency="USD" />
            </td>
            <td className="px-4 py-2.5 text-right font-medium">
              <Money amount={i.amount} currency="USD" />
            </td>
            <td className={`px-4 py-2.5 ${statusLabel[i.status].tone}`}>{statusLabel[i.status].text}</td>
          </tr>
        ))}
      </Table>
    </div>
  );
}
