import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { Can } from '../../components/Can';
import { DemoCode } from '../../components/DemoCode';
import { LoanSchedule } from '../../components/LoanSchedule';
import {
  Button,
  Empty,
  ErrorNote,
  Field,
  Money,
  PageHeader,
  Section,
  Select,
  Table,
} from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { formatBusinessDate, groupIban, money, percent } from '../../lib/format';
import type { Account, Loan } from '../../lib/types';

/** French system: fixed installment. Display only; the server computes the real schedule to the cent. */
function installment(principal: number, annualRatePct: number, months: number): number {
  const r = annualRatePct / 1200;
  return r === 0 ? principal / months : (principal * r) / (1 - (1 + r) ** -months);
}

export function Loans() {
  const loans = useQuery({ queryKey: ['loans'], queryFn: () => api<Loan[]>('/api/v1/loans') });
  const [selected, setSelected] = useState<string | null>(null);
  const current = loans.data?.find((l) => l.id === selected);

  return (
    <>
      <PageHeader
        title="Créditos"
        lead="Sistema francés, cuota fija. El desembolso y cada cuota pasan por el libro mayor; las cuotas se cobran en el cierre de día."
      />
      <div className="grid gap-10 lg:grid-cols-[1fr_340px]">
        {/* min-w-0: lets the table scroll inside its box instead of pushing the page sideways */}
        <div className="min-w-0">
          <Section title="Cartera">
            <ErrorNote error={loans.error} />
            {loans.data?.length === 0 ? (
              <Empty>Todavía no hay créditos.</Empty>
            ) : (
              <Table head={['Cliente', 'Monto', 'Saldo', 'Tasa', 'Próxima cuota']}>
                {loans.data?.map((l) => (
                  <tr key={l.id} className={selected === l.id ? 'bg-surface-2' : ''}>
                    <td className="px-4 py-2.5">
                      <button
                        type="button"
                        className="whitespace-nowrap text-left text-lapis underline"
                        onClick={() => {
                          setSelected(l.id);
                        }}
                      >
                        {l.customerName}
                      </button>
                      <span className="num block whitespace-nowrap text-xs text-ink-2">
                        {groupIban(l.accountNumber)}
                      </span>
                    </td>
                    <td className="px-4 py-2.5 text-right">
                      <Money amount={l.principal} currency="USD" />
                    </td>
                    <td className="px-4 py-2.5 text-right">
                      <Money amount={l.outstanding} currency="USD" />
                    </td>
                    <td className="num whitespace-nowrap px-4 py-2.5 text-right">{percent(l.annualRate)}</td>
                    <td className="whitespace-nowrap px-4 py-2.5">
                      {l.status === 'PENDING_APPROVAL' ? (
                        <span className="font-medium text-quinoa">Por aprobar</span>
                      ) : l.status === 'REJECTED' ? (
                        'Rechazado'
                      ) : l.status === 'PAID_OFF' ? (
                        'Cancelado'
                      ) : l.next ? (
                        formatBusinessDate(l.next.dueDate)
                      ) : (
                        '—'
                      )}
                    </td>
                  </tr>
                ))}
              </Table>
            )}
          </Section>
        </div>
        <Can any={['loans:originate']}>
          <OriginateForm onGranted={setSelected} />
        </Can>
      </div>
      {current?.status === 'PENDING_APPROVAL' ? (
        <div className="mt-10">
          <Decision loan={current} />
        </div>
      ) : (
        selected && (
          <div className="mt-10">
            <Section title="Tabla de pagos">
              <LoanSchedule loanId={selected} />
            </Section>
          </div>
        )
      )}
    </>
  );
}

function OriginateForm({ onGranted }: { onGranted: (id: string) => void }) {
  const queryClient = useQueryClient();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/api/v1/accounts') });
  const [accountId, setAccountId] = useState('');
  const [principal, setPrincipal] = useState('5000');
  const [rate, setRate] = useState('15.5');
  const [months, setMonths] = useState('24');
  const grant = useMutation({
    mutationFn: () =>
      api<Loan>('/api/v1/loans', {
        method: 'POST',
        body: { accountId, principal: Number(principal), annualRate: Number(rate), months: Number(months) },
      }),
    onSuccess: (loan) => {
      void queryClient.invalidateQueries({ queryKey: ['loans'] });
      void queryClient.invalidateQueries({ queryKey: ['accounts'] });
      onGranted(loan.id);
    },
  });
  const active = accounts.data?.filter((a) => a.status === 'ACTIVE' && a.ownerName) ?? [];
  const preview = installment(Number(principal), Number(rate), Number(months));

  return (
    <Section title="Otorgar crédito">
      <form
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          grant.mutate();
        }}
      >
        <Select
          label="Cuenta del cliente"
          value={accountId}
          onChange={(e) => {
            setAccountId(e.target.value);
          }}
          required
        >
          <option value="">Elige una cuenta</option>
          {active.map((a) => (
            <option key={a.id} value={a.id}>
              {a.ownerName} · {groupIban(a.number)}
            </option>
          ))}
        </Select>
        <Field
          label="Monto"
          type="number"
          min="100"
          max="1000000"
          step="0.01"
          value={principal}
          onChange={(e) => {
            setPrincipal(e.target.value);
          }}
        />
        <Field
          label="Tasa anual (%)"
          type="number"
          min="0"
          max="60"
          step="0.01"
          value={rate}
          onChange={(e) => {
            setRate(e.target.value);
          }}
        />
        <Field
          label="Plazo (meses)"
          type="number"
          min="1"
          max="360"
          value={months}
          onChange={(e) => {
            setMonths(e.target.value);
          }}
          hint={
            Number.isFinite(preview) && preview > 0 ? `Cuota mensual aprox. ${money(preview)}` : undefined
          }
        />
        <ErrorNote error={grant.error} />
        <Button type="submit" disabled={!accountId || grant.isPending}>
          Otorgar y desembolsar
        </Button>
      </form>
    </Section>
  );
}

/** Four-eyes: someone other than the granter approves, with a fresh code; rejecting moves no money. */
function Decision({ loan }: { loan: Loan }) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const decide = useMutation({
    mutationFn: (decision: 'approve' | 'reject') =>
      api<Loan>(`/api/v1/loans/${loan.id}/${decision}`, {
        method: 'POST',
        body: { note: 'Comité de crédito', otp: decision === 'approve' ? code : undefined },
      }),
    onSuccess: () => {
      setCode('');
      void queryClient.invalidateQueries({ queryKey: ['loans'] });
    },
  });
  const own = session?.user.email.toLowerCase() === loan.grantedBy.toLowerCase();

  return (
    <Section title="Aprobación del crédito">
      <div className="flex max-w-md flex-col gap-4">
        <p>
          <Money amount={loan.principal} currency="USD" className="font-medium" /> para {loan.customerName}, a{' '}
          {loan.termMonths} meses al <span className="num">{percent(loan.annualRate)}</span>. Supera el umbral
          de cuatro ojos de la institución: otra persona lo aprueba con un código de verificación, y recién
          entonces se desembolsa.
        </p>
        <p className="text-sm text-ink-2">Otorgado por {loan.grantedBy}.</p>
        {own ? (
          <p className="font-medium text-quinoa">Otorgaste este crédito: otra persona debe aprobarlo.</p>
        ) : (
          <Can any={['loans:originate']}>
            <Field
              label="Código de verificación"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => {
                setCode(e.target.value.replace(/\D/g, ''));
              }}
            />
            {session && <DemoCode email={session.user.email} />}
            <ErrorNote error={decide.error} />
            <div className="flex flex-wrap gap-3">
              <Button
                disabled={code.length !== 6 || decide.isPending}
                onClick={() => {
                  decide.mutate('approve');
                }}
              >
                Aprobar y desembolsar
              </Button>
              <Button
                variant="quiet"
                disabled={decide.isPending}
                onClick={() => {
                  decide.mutate('reject');
                }}
              >
                Rechazar
              </Button>
            </div>
          </Can>
        )}
      </div>
    </Section>
  );
}
