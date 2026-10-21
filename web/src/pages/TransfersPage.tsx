import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { StatusPill } from '../components/StatusPill';
import { Button, Empty, ErrorNote, Money, PageHeader, Select, Table } from '../components/ui';
import { api, apiText } from '../lib/api';
import { downloadText, formatDateTime, groupIban } from '../lib/format';
import type { Transfer, TransferStatus } from '../lib/types';

const filters: { value: '' | TransferStatus; label: string }[] = [
  { value: '', label: 'Todas' },
  { value: 'COMPLETED', label: 'Completadas' },
  { value: 'PENDING_APPROVAL', label: 'Esperando aprobación' },
  { value: 'HELD_FOR_REVIEW', label: 'En revisión de riesgo' },
  { value: 'REJECTED', label: 'Rechazadas' },
  { value: 'FAILED', label: 'Fallidas' },
];

export function TransfersPage() {
  const [status, setStatus] = useState<'' | TransferStatus>('');
  const transfers = useQuery({
    queryKey: ['transfers', status],
    queryFn: () => api<Transfer[]>(`/api/v1/transfers?size=100${status ? `&status=${status}` : ''}`),
  });
  const [error, setError] = useState<unknown>(null);

  async function exportPacs008(t: Transfer) {
    try {
      downloadText(`pacs008-${t.endToEndId}.xml`, await apiText(`/api/v1/transfers/${t.id}/pacs008`));
    } catch (e) {
      setError(e);
    }
  }

  return (
    <>
      <PageHeader
        title="Transferencias"
        lead="Cada transferencia lleva identificadores ISO 20022 (EndToEndId y UETR) para rastrearla de punta a punta."
        actions={
          <div className="w-56">
            <Select
              label="Estado"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as '' | TransferStatus);
              }}
            >
              {filters.map((f) => (
                <option key={f.value} value={f.value}>
                  {f.label}
                </option>
              ))}
            </Select>
          </div>
        }
      />
      <ErrorNote error={transfers.error ?? error} />
      {transfers.data?.length === 0 && <Empty>No hay transferencias con este estado.</Empty>}
      {transfers.data && transfers.data.length > 0 && (
        <Table head={['Fecha', 'Origen', 'Destino', 'Concepto', 'Monto', 'Estado', '']}>
          {transfers.data.map((t) => (
            <tr key={t.id}>
              <td className="whitespace-nowrap px-4 py-3 text-ink-2">{formatDateTime(t.createdAt)}</td>
              <td className="num whitespace-nowrap px-4 py-3 text-xs">{groupIban(t.sourceAccountNumber)}</td>
              <td className="num whitespace-nowrap px-4 py-3 text-xs">
                {groupIban(t.destinationAccountNumber)}
              </td>
              <td className="px-4 py-3">{t.remittanceInfo ?? '—'}</td>
              <td className="px-4 py-3 text-right">
                <Money amount={t.amount} currency={t.currency} />
              </td>
              <td className="px-4 py-3">
                <StatusPill status={t.status} reason={t.failureCode} />
              </td>
              <td className="px-4 py-3">
                <Button variant="quiet" className="px-2 py-1 text-xs" onClick={() => void exportPacs008(t)}>
                  pacs.008
                </Button>
              </td>
            </tr>
          ))}
        </Table>
      )}
    </>
  );
}
