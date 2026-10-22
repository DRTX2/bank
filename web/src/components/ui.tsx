import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react';
import { useId } from 'react';

import { ApiError } from '../lib/api';
import { errorText } from '../lib/errors';
import { money } from '../lib/format';

type Variant = 'primary' | 'quiet' | 'danger';

const variants: Record<Variant, string> = {
  primary: 'bg-lapis text-on-lapis hover:bg-lapis-strong',
  quiet: 'border border-line bg-surface text-ink hover:bg-surface-2',
  danger: 'bg-risk text-white hover:opacity-90',
};

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
      {...props}
    />
  );
}

export function Field({
  label,
  hint,
  error,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={hint || error ? `${id}-hint` : undefined}
        className="rounded-md border border-line bg-surface px-3 py-2 text-[15px] outline-none focus:border-lapis"
        {...props}
      />
      {(hint ?? error) && (
        <p id={`${id}-hint`} className={`text-xs ${error ? 'text-risk' : 'text-ink-2'}`}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

export function Select({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; children: ReactNode }) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <select
        id={id}
        className="rounded-md border border-line bg-surface px-3 py-2 text-[15px] outline-none focus:border-lapis"
        {...props}
      >
        {children}
      </select>
    </div>
  );
}

export function PageHeader({ title, lead, actions }: { title: string; lead?: string; actions?: ReactNode }) {
  return (
    <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div className="max-w-2xl">
        <h1 className="text-[28px] font-semibold leading-tight tracking-tight">{title}</h1>
        {lead && <p className="mt-1.5 text-ink-2">{lead}</p>}
      </div>
      {actions}
    </header>
  );
}

export function Section({
  title,
  children,
  aside,
}: {
  title: string;
  children: ReactNode;
  aside?: ReactNode;
}) {
  return (
    <section className="mb-10">
      <div className="mb-3 flex items-baseline justify-between gap-4 border-b border-line pb-2">
        <h2 className="text-lg font-semibold">{title}</h2>
        {aside}
      </div>
      {children}
    </section>
  );
}

export function Money({
  amount,
  currency,
  signed = false,
  className = '',
}: {
  amount: number;
  currency: string;
  signed?: boolean;
  className?: string;
}) {
  const tone = signed ? (amount < 0 ? 'text-ink' : 'text-ok') : '';
  const text = signed && amount > 0 ? `+${money(amount, currency)}` : money(amount, currency);
  return <span className={`num ${tone} ${className}`}>{text}</span>;
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  const message = errorText(error);
  const iso = error instanceof ApiError && error.isoReason ? ` (ISO ${error.isoReason})` : '';
  return (
    <p role="alert" className="rounded-md border border-risk/40 bg-risk-soft px-3 py-2 text-sm text-risk">
      {message}
      {iso}
    </p>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-md border border-dashed border-line px-4 py-8 text-center text-ink-2">{children}</p>
  );
}

// Money and quantity columns are right-aligned in every table so their headers must follow.
const NUMERIC_HEADS = new Set([
  'Monto',
  'Saldo',
  'Cuota',
  'Capital',
  'Interés',
  'Seguro',
  'Suma de control',
  'Umbral cuatro ojos',
]);

export function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    // relative: keeps absolutely positioned sr-only text inside the scroll box instead of widening the page
    <div className="relative overflow-x-auto rounded-lg border border-line bg-surface">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="border-b border-line bg-surface-2 text-ink-2">
          <tr>
            {head.map((h) => (
              <th
                key={h}
                scope="col"
                className={`px-4 py-2.5 font-medium ${NUMERIC_HEADS.has(h) ? 'text-right' : ''}`}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
    </div>
  );
}
