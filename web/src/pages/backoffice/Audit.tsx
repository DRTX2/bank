import { useMutation, useQuery } from '@tanstack/react-query';
import { ShieldCheck, ShieldX } from 'lucide-react';

import { Button, ErrorNote, PageHeader, Table } from '../../components/ui';
import { api } from '../../lib/api';
import { formatDateTime } from '../../lib/format';

interface AuditRecord {
  id: number;
  actorEmail: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  details: string | null;
  createdAt: string;
  hash: string;
}

interface Verification {
  valid: boolean;
  checkedRecords: number;
  firstBrokenId: number | null;
}

export function Audit() {
  const log = useQuery({ queryKey: ['audit'], queryFn: () => api<AuditRecord[]>('/api/v1/audit?size=200') });
  const verify = useMutation({ mutationFn: () => api<Verification>('/api/v1/audit/verify') });
  const v = verify.data;

  return (
    <>
      <PageHeader
        title="Auditoría"
        lead="Cada registro guarda el hash del anterior. Modificar o borrar uno rompe la cadena desde ese punto, y la verificación lo detecta."
        actions={
          <Button
            onClick={() => {
              verify.mutate();
            }}
            disabled={verify.isPending}
          >
            Verificar integridad
          </Button>
        }
      />
      <ErrorNote error={log.error ?? verify.error} />
      {v && (
        <div
          className={`mb-6 flex items-start gap-3 rounded-lg border p-4 ${v.valid ? 'border-ok/40 bg-ok-soft' : 'border-risk/40 bg-risk-soft'}`}
          role="status"
        >
          {v.valid ? (
            <ShieldCheck className="size-6 text-ok" aria-hidden />
          ) : (
            <ShieldX className="size-6 text-risk" aria-hidden />
          )}
          <div>
            <p className="font-medium">
              {v.valid ? 'Cadena íntegra' : `Cadena alterada en el registro #${v.firstBrokenId ?? '?'}`}
            </p>
            <p className="num text-sm text-ink-2">{v.checkedRecords} registros verificados</p>
          </div>
        </div>
      )}
      <Table head={['#', 'Fecha', 'Actor', 'Acción', 'Recurso', 'Detalle', 'Hash']}>
        {log.data?.map((r) => (
          <tr key={r.id}>
            <td className="num px-4 py-2.5 text-ink-2">{r.id}</td>
            <td className="whitespace-nowrap px-4 py-2.5 text-ink-2">{formatDateTime(r.createdAt)}</td>
            <td className="px-4 py-2.5">{r.actorEmail ?? 'sistema'}</td>
            <td className="px-4 py-2.5 font-medium">{r.action.toLowerCase().replaceAll('_', ' ')}</td>
            <td className="px-4 py-2.5 text-ink-2">{r.resourceType}</td>
            <td className="max-w-xs truncate px-4 py-2.5 text-ink-2" title={r.details ?? ''}>
              {r.details}
            </td>
            <td className="num px-4 py-2.5 text-xs text-ink-2" title={r.hash}>
              {r.hash.slice(0, 10)}
            </td>
          </tr>
        ))}
      </Table>
    </>
  );
}
