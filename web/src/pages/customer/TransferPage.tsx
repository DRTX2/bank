import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';

import { RiskMeter } from '../../components/RiskMeter';
import { StatusPill } from '../../components/StatusPill';
import { Button, ErrorNote, Field, Money, PageHeader, Select } from '../../components/ui';
import { api, ApiError, newIdempotencyKey } from '../../lib/api';
import { errorText, isoReasons } from '../../lib/errors';
import { groupIban, money } from '../../lib/format';
import type { Account, Transfer } from '../../lib/types';

interface Lookup {
  number: string;
  holder: string;
  currency: string;
  status: string;
}

const outcomeCopy: Partial<Record<Transfer['status'], string>> = {
  COMPLETED: 'El dinero ya está en la cuenta de destino.',
  PENDING_APPROVAL: 'Por su monto, un gerente debe aprobarla. Te avisaremos apenas ocurra.',
  HELD_FOR_REVIEW: 'Nuestro equipo de cumplimiento la está revisando por seguridad.',
  REJECTED: 'La transferencia fue rechazada y no se debitó dinero.',
  FAILED: 'No se pudo completar y no se debitó dinero.',
};

export function TransferPage() {
  const queryClient = useQueryClient();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/api/v1/accounts') });
  const [source, setSource] = useState('');
  const [destination, setDestination] = useState('');
  const [amount, setAmount] = useState('');
  const [concept, setConcept] = useState('');
  const [lookup, setLookup] = useState<Lookup | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  // One key per attempt: double clicks and network retries can never pay twice.
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);

  const sourceAccount = accounts.data?.find((a) => a.id === source) ?? accounts.data?.[0];

  const send = useMutation({
    mutationFn: () =>
      api<Transfer>('/api/v1/transfers', {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: {
          sourceAccountId: sourceAccount?.id,
          destinationAccountNumber: destination.replaceAll(' ', ''),
          amount: Number(amount),
          remittanceInfo: concept || undefined,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries();
    },
  });

  async function checkDestination() {
    setLookup(null);
    setLookupError(null);
    if (!destination.trim()) return;
    try {
      setLookup(
        await api<Lookup>(
          `/api/v1/accounts/lookup?number=${encodeURIComponent(destination.replaceAll(' ', ''))}`,
        ),
      );
    } catch (e) {
      setLookupError(e instanceof ApiError ? errorText(e) : 'No pudimos verificar la cuenta');
    }
  }

  function submit(e: SyntheticEvent) {
    e.preventDefault();
    send.mutate();
  }

  function reset() {
    send.reset();
    setIdempotencyKey(newIdempotencyKey());
    setAmount('');
    setConcept('');
  }

  const result = send.data;

  return (
    <>
      <PageHeader
        title="Transferir"
        lead="A cualquier cuenta de tu institución. Verificamos el número antes de enviar."
      />
      {result ? (
        <div className="max-w-xl rounded-lg border border-line bg-surface p-6">
          <StatusPill status={result.status} reason={result.failureCode} />
          <p className="mt-4 text-3xl font-semibold">
            <Money amount={result.amount} currency={result.currency} />
          </p>
          <p className="mt-1 text-ink-2">a {groupIban(result.destinationAccountNumber)}</p>
          <p className="mt-4">{outcomeCopy[result.status] ?? 'Transferencia registrada.'}</p>
          {result.failureCode && (
            <p className="mt-1 text-sm text-ink-2">
              {isoReasons[result.failureCode] ?? result.failureReason}
            </p>
          )}
          {result.riskScore !== null && result.riskScore > 0 && (
            <div className="mt-6 border-t border-line pt-5">
              <RiskMeter score={result.riskScore} signals={result.riskSignals} />
            </div>
          )}
          <dl className="mt-6 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs text-ink-2">
            <dt>EndToEndId</dt>
            <dd className="num break-all">{result.endToEndId}</dd>
            <dt>UETR</dt>
            <dd className="num break-all">{result.uetr}</dd>
          </dl>
          <div className="mt-6 flex gap-3">
            <Button onClick={reset}>Hacer otra transferencia</Button>
            <Link to="/transferencias">
              <Button variant="quiet">Ver historial</Button>
            </Link>
          </div>
        </div>
      ) : (
        <form onSubmit={submit} className="flex max-w-xl flex-col gap-5">
          <Select
            label="Desde"
            value={sourceAccount?.id ?? ''}
            onChange={(e) => {
              setSource(e.target.value);
            }}
          >
            {accounts.data?.map((a) => (
              <option key={a.id} value={a.id}>
                {a.alias ?? a.type} ({groupIban(a.number)}) — disponible {money(a.balance, a.currency)}
              </option>
            ))}
          </Select>
          <div>
            <Field
              label="Cuenta de destino"
              placeholder="EC12 ANDN 0000 0000 0000 00"
              required
              value={destination}
              onChange={(e) => {
                setDestination(e.target.value);
                setLookup(null);
              }}
              onBlur={() => void checkDestination()}
              error={lookupError ?? undefined}
              hint={lookup ? `Titular: ${lookup.holder}` : 'Formato IBAN con dígitos de control'}
            />
          </div>
          <Field
            label="Monto"
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            required
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value);
            }}
            hint={
              sourceAccount
                ? `Límite diario: ${money(sourceAccount.dailyLimit, sourceAccount.currency)}`
                : undefined
            }
          />
          <Field
            label="Concepto"
            maxLength={140}
            placeholder="Arriendo de octubre"
            value={concept}
            onChange={(e) => {
              setConcept(e.target.value);
            }}
          />
          <ErrorNote error={send.error} />
          <Button type="submit" disabled={send.isPending || !sourceAccount} className="self-start px-6">
            {send.isPending ? 'Enviando…' : 'Enviar transferencia'}
          </Button>
        </form>
      )}
    </>
  );
}
