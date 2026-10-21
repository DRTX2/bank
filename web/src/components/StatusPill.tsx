import type { TransferStatus } from '../lib/types';

const styles: Record<TransferStatus, { label: string; className: string }> = {
  COMPLETED: { label: 'Completada', className: 'bg-ok-soft text-ok' },
  ACCEPTED: { label: 'En proceso', className: 'bg-surface-2 text-ink-2' },
  PENDING_APPROVAL: { label: 'Espera aprobación', className: 'bg-quinoa-soft text-quinoa' },
  HELD_FOR_REVIEW: { label: 'En revisión de riesgo', className: 'bg-quinoa-soft text-quinoa' },
  FAILED: { label: 'Fallida', className: 'bg-risk-soft text-risk' },
  REJECTED: { label: 'Rechazada', className: 'bg-risk-soft text-risk' },
};

export function StatusPill({ status, reason }: { status: TransferStatus; reason?: string | null }) {
  const s = styles[status];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${s.className}`}
    >
      {s.label}
      {reason && <span className="opacity-75">{reason}</span>}
    </span>
  );
}
