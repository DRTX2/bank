import { DECLINE_THRESHOLD, REVIEW_THRESHOLD, RISK_SIGNALS } from '../lib/risk';

const palette = ['bg-lapis', 'bg-quinoa', 'bg-risk', 'bg-ink-2', 'bg-lapis-strong', 'bg-ok'];

/**
 * Explainable risk: the bar is built from each signal's contribution, with the review and decline thresholds
 * marked, so an analyst sees why a payment was stopped, not just that it was.
 */
export function RiskMeter({ score, signals }: { score: number | null; signals: string[] }) {
  if (score === null) return <span className="text-sm text-ink-2">Sin evaluación</span>;
  const parts = signals.map((code, i) => ({
    code,
    label: RISK_SIGNALS[code]?.label ?? code,
    weight: RISK_SIGNALS[code]?.weight ?? 0,
    color: palette[i % palette.length] ?? 'bg-lapis',
  }));
  return (
    <figure className="w-full max-w-md">
      <div className="flex items-baseline justify-between">
        <figcaption className="text-sm text-ink-2">Puntaje de riesgo</figcaption>
        <span className="num text-2xl font-semibold">
          {score}
          <span className="text-sm font-normal text-ink-2">/100</span>
        </span>
      </div>
      <div
        className="relative mt-2 flex h-3 overflow-hidden rounded-sm bg-surface-2"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={score}
        aria-label="Puntaje de riesgo"
      >
        {parts.map((p) => (
          <span key={p.code} className={p.color} style={{ width: `${Math.min(p.weight, 100)}%` }} />
        ))}
        <span className="absolute inset-y-0 w-px bg-ink" style={{ left: `${REVIEW_THRESHOLD}%` }} />
        <span className="absolute inset-y-0 w-px bg-risk" style={{ left: `${DECLINE_THRESHOLD}%` }} />
      </div>
      <div className="num mt-1 flex justify-between text-[11px] text-ink-2">
        <span>0</span>
        <span style={{ marginLeft: `${REVIEW_THRESHOLD - 8}%` }}>revisión {REVIEW_THRESHOLD}</span>
        <span>bloqueo {DECLINE_THRESHOLD}</span>
      </div>
      {parts.length > 0 && (
        <ul className="mt-3 space-y-1.5 text-sm">
          {parts.map((p) => (
            <li key={p.code} className="flex items-center gap-2">
              <span className={`size-2.5 shrink-0 rounded-sm ${p.color}`} aria-hidden />
              <span className="flex-1">{p.label}</span>
              <span className="num text-ink-2">+{p.weight}</span>
            </li>
          ))}
        </ul>
      )}
    </figure>
  );
}
