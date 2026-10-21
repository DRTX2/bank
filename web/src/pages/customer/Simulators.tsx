import { useMutation } from '@tanstack/react-query';
import { useState, type SyntheticEvent, type ReactNode } from 'react';

import { StackedBars } from '../../components/StackedBars';
import { Button, ErrorNote, Field, Money, PageHeader, Select, Table } from '../../components/ui';
import { api } from '../../lib/api';

type Tab = 'loan' | 'deposit' | 'goal' | 'capacity';

const tabs: { id: Tab; label: string; lead: string }[] = [
  {
    id: 'loan',
    label: 'Crédito',
    lead: 'Cuota, intereses y tabla de amortización con sistema francés o alemán.',
  },
  {
    id: 'deposit',
    label: 'Plazo fijo',
    lead: 'Cuánto ganas al vencimiento, después de la retención en la fuente.',
  },
  { id: 'goal', label: 'Meta de ahorro', lead: 'Cuánto ahorrar cada mes para llegar a tu meta a tiempo.' },
  {
    id: 'capacity',
    label: 'Capacidad de endeudamiento',
    lead: 'Cuánto podrías pedir prestado sin ahogar tu presupuesto.',
  },
];

export function Simulators() {
  const [tab, setTab] = useState<Tab>('loan');
  const current = tabs.find((t) => t.id === tab) ?? tabs[0];
  return (
    <>
      <PageHeader
        title="Simuladores"
        lead="Prueba escenarios antes de comprometerte. Nada de esto crea un producto real."
      />
      <div role="tablist" aria-label="Simuladores" className="mb-6 flex flex-wrap gap-1 border-b border-line">
        {tabs.map((t) => (
          <button
            key={t.id}
            role="tab"
            type="button"
            aria-selected={tab === t.id}
            onClick={() => {
              setTab(t.id);
            }}
            className={`-mb-px border-b-2 px-3 py-2 text-sm ${
              tab === t.id
                ? 'border-lapis font-medium text-ink'
                : 'border-transparent text-ink-2 hover:text-ink'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <p className="mb-6 text-ink-2">{current?.lead}</p>
      <div role="tabpanel">
        {tab === 'loan' && <LoanSimulator />}
        {tab === 'deposit' && <DepositSimulator />}
        {tab === 'goal' && <GoalSimulator />}
        {tab === 'capacity' && <CapacitySimulator />}
      </div>
    </>
  );
}

function Layout({ form, result }: { form: ReactNode; result: ReactNode }) {
  return (
    <div className="grid gap-10 lg:grid-cols-[320px_1fr]">
      <div>{form}</div>
      <div className="min-w-0">{result}</div>
    </div>
  );
}

function Figure({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-sm text-ink-2">{label}</dt>
      <dd className="num mt-0.5 text-2xl font-semibold">{children}</dd>
    </div>
  );
}

function useSim<T>(path: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api<T>(`/api/v1/simulations/${path}`, { method: 'POST', body }),
  });
}

interface LoanResult {
  firstInstallment: number;
  lastInstallment: number;
  totalInterest: number;
  totalInsurance: number;
  totalPaid: number;
  effectiveAnnualRatePct: number;
  schedule: {
    period: number;
    installment: number;
    principal: number;
    interest: number;
    insurance: number;
    balance: number;
  }[];
}

function LoanSimulator() {
  const [form, setForm] = useState({
    principal: '15000',
    annualRatePct: '15.5',
    months: '36',
    method: 'FRENCH',
    monthlyInsurancePct: '0.05',
  });
  const sim = useSim<LoanResult>('loan');
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value });
  };
  const submit = (e: SyntheticEvent) => {
    e.preventDefault();
    sim.mutate({
      ...form,
      principal: Number(form.principal),
      annualRatePct: Number(form.annualRatePct),
      months: Number(form.months),
      monthlyInsurancePct: Number(form.monthlyInsurancePct),
    });
  };
  const r = sim.data;
  return (
    <Layout
      form={
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Field
            label="Monto"
            type="number"
            min="100"
            step="100"
            value={form.principal}
            onChange={set('principal')}
          />
          <Field
            label="Tasa nominal anual (%)"
            type="number"
            step="0.01"
            value={form.annualRatePct}
            onChange={set('annualRatePct')}
          />
          <Field
            label="Plazo (meses)"
            type="number"
            min="1"
            max="360"
            value={form.months}
            onChange={set('months')}
          />
          <Select label="Sistema de amortización" value={form.method} onChange={set('method')}>
            <option value="FRENCH">Francés (cuota fija)</option>
            <option value="GERMAN">Alemán (capital fijo, cuota decreciente)</option>
          </Select>
          <Field
            label="Seguro de desgravamen mensual (%)"
            type="number"
            step="0.01"
            value={form.monthlyInsurancePct}
            onChange={set('monthlyInsurancePct')}
          />
          <ErrorNote error={sim.error} />
          <Button type="submit" disabled={sim.isPending}>
            Calcular cuotas
          </Button>
        </form>
      }
      result={
        r && (
          <>
            <dl className="mb-8 grid grid-cols-2 gap-6 sm:grid-cols-4">
              <Figure label={form.method === 'FRENCH' ? 'Cuota mensual' : 'Primera cuota'}>
                <Money amount={r.firstInstallment} currency="USD" />
              </Figure>
              <Figure label="Intereses totales">
                <Money amount={r.totalInterest} currency="USD" />
              </Figure>
              <Figure label="Total a pagar">
                <Money amount={r.totalPaid} currency="USD" />
              </Figure>
              <Figure label="Tasa efectiva anual">{r.effectiveAnnualRatePct.toFixed(2)}%</Figure>
            </dl>
            <StackedBars
              title="Composición de cada cuota"
              names={['Capital', 'Interés']}
              currency="USD"
              data={r.schedule.map((s) => ({ label: String(s.period), a: s.principal, b: s.interest }))}
            />
            <details className="mt-6">
              <summary className="cursor-pointer text-sm font-medium">
                Tabla de amortización ({r.schedule.length} cuotas)
              </summary>
              <div className="mt-3">
                <Table head={['#', 'Cuota', 'Capital', 'Interés', 'Seguro', 'Saldo']}>
                  {r.schedule.map((s) => (
                    <tr key={s.period} className="num">
                      <td className="px-4 py-2">{s.period}</td>
                      <td className="px-4 py-2 text-right">
                        <Money amount={s.installment} currency="USD" />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Money amount={s.principal} currency="USD" />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Money amount={s.interest} currency="USD" />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Money amount={s.insurance} currency="USD" />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Money amount={s.balance} currency="USD" />
                      </td>
                    </tr>
                  ))}
                </Table>
              </div>
            </details>
          </>
        )
      }
    />
  );
}

interface DepositResult {
  grossInterest: number;
  withholdingTax: number;
  netInterest: number;
  maturityAmount: number;
  effectiveAnnualYieldPct: number;
}

function DepositSimulator() {
  const [form, setForm] = useState({
    amount: '10000',
    annualRatePct: '7.5',
    days: '180',
    withholdingTaxPct: '2',
  });
  const sim = useSim<DepositResult>('term-deposit');
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value });
  };
  const r = sim.data;
  return (
    <Layout
      form={
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sim.mutate(Object.fromEntries(Object.entries(form).map(([k, v]) => [k, Number(v)])));
          }}
          className="flex flex-col gap-4"
        >
          <Field
            label="Monto a invertir"
            type="number"
            min="100"
            value={form.amount}
            onChange={set('amount')}
          />
          <Field
            label="Tasa anual (%)"
            type="number"
            step="0.01"
            value={form.annualRatePct}
            onChange={set('annualRatePct')}
          />
          <Field
            label="Plazo (días)"
            type="number"
            min="30"
            max="1825"
            value={form.days}
            onChange={set('days')}
            hint="Año comercial de 360 días"
          />
          <Field
            label="Retención en la fuente (%)"
            type="number"
            step="0.1"
            value={form.withholdingTaxPct}
            onChange={set('withholdingTaxPct')}
          />
          <ErrorNote error={sim.error} />
          <Button type="submit" disabled={sim.isPending}>
            Calcular rendimiento
          </Button>
        </form>
      }
      result={
        r && (
          <dl className="grid grid-cols-2 gap-6">
            <Figure label="Recibes al vencimiento">
              <Money amount={r.maturityAmount} currency="USD" />
            </Figure>
            <Figure label="Interés neto">
              <Money amount={r.netInterest} currency="USD" />
            </Figure>
            <Figure label="Interés bruto">
              <Money amount={r.grossInterest} currency="USD" />
            </Figure>
            <Figure label="Retención">
              <Money amount={r.withholdingTax} currency="USD" />
            </Figure>
            <Figure label="Rendimiento efectivo anual">{r.effectiveAnnualYieldPct.toFixed(2)}%</Figure>
          </dl>
        )
      }
    />
  );
}

interface GoalResult {
  monthlyDeposit: number;
  totalContributed: number;
  totalInterest: number;
  projection: { month: number; contributed: number; interestEarned: number; balance: number }[];
}

function GoalSimulator() {
  const [form, setForm] = useState({ target: '5000', initial: '300', months: '18', annualRatePct: '5' });
  const sim = useSim<GoalResult>('savings-goal');
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value });
  };
  const r = sim.data;
  return (
    <Layout
      form={
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sim.mutate(Object.fromEntries(Object.entries(form).map(([k, v]) => [k, Number(v)])));
          }}
          className="flex flex-col gap-4"
        >
          <Field label="Meta" type="number" min="1" value={form.target} onChange={set('target')} />
          <Field
            label="Ahorro inicial"
            type="number"
            min="0"
            value={form.initial}
            onChange={set('initial')}
          />
          <Field
            label="Plazo (meses)"
            type="number"
            min="1"
            max="600"
            value={form.months}
            onChange={set('months')}
          />
          <Field
            label="Tasa de la cuenta de ahorro (%)"
            type="number"
            step="0.01"
            value={form.annualRatePct}
            onChange={set('annualRatePct')}
          />
          <ErrorNote error={sim.error} />
          <Button type="submit" disabled={sim.isPending}>
            Calcular ahorro mensual
          </Button>
        </form>
      }
      result={
        r && (
          <>
            <dl className="mb-8 grid grid-cols-3 gap-6">
              <Figure label="Ahorra cada mes">
                <Money amount={r.monthlyDeposit} currency="USD" />
              </Figure>
              <Figure label="Aportas en total">
                <Money amount={r.totalContributed} currency="USD" />
              </Figure>
              <Figure label="Intereses ganados">
                <Money amount={r.totalInterest} currency="USD" />
              </Figure>
            </dl>
            <StackedBars
              title="Cómo crece tu ahorro"
              names={['Lo que aportas', 'Intereses']}
              currency="USD"
              data={r.projection.map((p) => ({
                label: String(p.month),
                a: p.contributed,
                b: Math.max(p.interestEarned, 0),
              }))}
            />
          </>
        )
      }
    />
  );
}

interface CapacityResult {
  maxInstallment: number;
  maxLoanAmount: number;
  debtToIncomePct: number;
  rating: 'STRONG' | 'MODERATE' | 'TIGHT' | 'NO_CAPACITY';
}

const ratingCopy: Record<CapacityResult['rating'], string> = {
  STRONG: 'Tus deudas actuales son bajas frente a tus ingresos.',
  MODERATE: 'Tienes margen, pero conviene no estirarlo al máximo.',
  TIGHT: 'Tus deudas ya pesan bastante; un crédito nuevo sería arriesgado.',
  NO_CAPACITY: 'Con estos números no hay espacio para una cuota adicional.',
};

function CapacitySimulator() {
  const [form, setForm] = useState({
    monthlyIncome: '1800',
    monthlyExpenses: '850',
    existingDebtPayments: '150',
    annualRatePct: '16',
    months: '48',
    maxDebtToIncomePct: '35',
  });
  const sim = useSim<CapacityResult>('borrowing-capacity');
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value });
  };
  const r = sim.data;
  return (
    <Layout
      form={
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sim.mutate(Object.fromEntries(Object.entries(form).map(([k, v]) => [k, Number(v)])));
          }}
          className="flex flex-col gap-4"
        >
          <Field
            label="Ingreso mensual"
            type="number"
            min="1"
            value={form.monthlyIncome}
            onChange={set('monthlyIncome')}
          />
          <Field
            label="Gastos mensuales"
            type="number"
            min="0"
            value={form.monthlyExpenses}
            onChange={set('monthlyExpenses')}
          />
          <Field
            label="Cuotas de deudas actuales"
            type="number"
            min="0"
            value={form.existingDebtPayments}
            onChange={set('existingDebtPayments')}
          />
          <Field
            label="Tasa del crédito (%)"
            type="number"
            step="0.01"
            value={form.annualRatePct}
            onChange={set('annualRatePct')}
          />
          <Field
            label="Plazo (meses)"
            type="number"
            min="1"
            max="360"
            value={form.months}
            onChange={set('months')}
          />
          <ErrorNote error={sim.error} />
          <Button type="submit" disabled={sim.isPending}>
            Calcular capacidad
          </Button>
        </form>
      }
      result={
        r && (
          <>
            <dl className="mb-6 grid grid-cols-3 gap-6">
              <Figure label="Podrías pedir hasta">
                <Money amount={r.maxLoanAmount} currency="USD" />
              </Figure>
              <Figure label="Cuota máxima">
                <Money amount={r.maxInstallment} currency="USD" />
              </Figure>
              <Figure label="Endeudamiento actual">{r.debtToIncomePct}%</Figure>
            </dl>
            <p className="max-w-prose">{ratingCopy[r.rating]}</p>
            <p className="mt-2 max-w-prose text-sm text-ink-2">
              Referencial: la aprobación real depende del análisis de crédito de la institución.
            </p>
          </>
        )
      }
    />
  );
}
