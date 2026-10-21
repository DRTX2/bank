import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileUp } from 'lucide-react';
import { useState, type ChangeEvent } from 'react';

import { Button, Empty, ErrorNote, Money, PageHeader, Section, Table } from '../../components/ui';
import { api } from '../../lib/api';
import { downloadText, formatDateTime } from '../../lib/format';
import type { Account } from '../../lib/types';

interface TxReport {
  endToEndId: string;
  status: 'ACSC' | 'ACCP' | 'PDNG' | 'RJCT';
  reasonCode: string | null;
  info: string | null;
  amount: number;
  currency: string;
}

interface StatusReport {
  originalMessageId: string;
  originalCount: number;
  originalSum: number;
  groupStatus: string;
  instructions: { paymentInfoId: string | null; transactions: TxReport[] }[];
}

interface Batch {
  id: string;
  messageId: string;
  numberOfTxs: number;
  controlSum: number;
  status: string;
  createdAt: string;
}

const txStatus: Record<TxReport['status'], string> = {
  ACSC: 'Liquidada',
  ACCP: 'Aceptada',
  PDNG: 'Pendiente',
  RJCT: 'Rechazada',
};

const groupStatus: Record<string, string> = {
  ACSC: 'Todas liquidadas',
  ACCP: 'Aceptadas, algunas pendientes',
  PART: 'Aceptación parcial',
  RJCT: 'Todo el archivo fue rechazado',
};

function samplePain001(debtor: Account, creditors: string[]): string {
  const msgId = `KUNTUR-${new Date().toISOString().slice(0, 19).replace(/\D/g, '')}`;
  const txs = creditors
    .map(
      (iban, i) => `      <CdtTrfTxInf>
        <PmtId><EndToEndId>${msgId}-${i + 1}</EndToEndId></PmtId>
        <Amt><InstdAmt Ccy="${debtor.currency}">${(25 + i * 10).toFixed(2)}</InstdAmt></Amt>
        <Cdtr><Nm>Proveedor ${i + 1}</Nm></Cdtr>
        <CdtrAcct><Id><IBAN>${iban}</IBAN></Id></CdtrAcct>
        <RmtInf><Ustrd>Factura 00${i + 1}</Ustrd></RmtInf>
      </CdtTrfTxInf>`,
    )
    .join('\n');
  const sum = creditors.reduce((s, _, i) => s + 25 + i * 10, 0).toFixed(2);
  return `<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
  <CstmrCdtTrfInitn>
    <GrpHdr>
      <MsgId>${msgId}</MsgId>
      <CreDtTm>${new Date().toISOString().slice(0, 19)}</CreDtTm>
      <NbOfTxs>${creditors.length}</NbOfTxs>
      <CtrlSum>${sum}</CtrlSum>
      <InitgPty><Nm>Mi empresa</Nm></InitgPty>
    </GrpHdr>
    <PmtInf>
      <PmtInfId>PMT-1</PmtInfId>
      <PmtMtd>TRF</PmtMtd>
      <Dbtr><Nm>Mi empresa</Nm></Dbtr>
      <DbtrAcct><Id><IBAN>${debtor.number}</IBAN></Id></DbtrAcct>
${txs}
    </PmtInf>
  </CstmrCdtTrfInitn>
</Document>
`;
}

export function BulkPayments() {
  const queryClient = useQueryClient();
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/api/v1/accounts') });
  const batches = useQuery({
    queryKey: ['batches'],
    queryFn: () => api<Batch[]>('/api/v1/payments/batches'),
  });
  const [fileName, setFileName] = useState<string | null>(null);

  const upload = useMutation({
    mutationFn: (xml: string) =>
      api<StatusReport>('/api/v1/payments/pain001', {
        method: 'POST',
        rawBody: xml,
        headers: { 'Content-Type': 'application/xml' },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries();
    },
  });

  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    upload.mutate(await file.text());
    e.target.value = '';
  }

  function downloadSample() {
    const own = accounts.data ?? [];
    const debtor = own[0];
    if (!debtor) return;
    const creditors = own.slice(1).map((a) => a.number);
    downloadText(
      'pain001-ejemplo.xml',
      samplePain001(debtor, creditors.length > 0 ? creditors : [debtor.number]),
    );
  }

  const report = upload.data;

  return (
    <>
      <PageHeader
        title="Pagos masivos"
        lead="Sube un archivo pain.001 (ISO 20022) con la nómina o los pagos a proveedores. Respondemos con un pain.002 que detalla el estado de cada pago."
      />
      <div className="mb-10 flex flex-wrap items-center gap-3">
        <label className="inline-flex cursor-pointer items-center gap-2 rounded-md bg-lapis px-4 py-2.5 text-sm font-medium text-on-lapis hover:bg-lapis-strong focus-within:outline-2 focus-within:outline-lapis">
          <FileUp className="size-4" aria-hidden />
          {upload.isPending ? 'Procesando…' : 'Subir pain.001'}
          <input
            type="file"
            accept=".xml,application/xml,text/xml"
            className="sr-only"
            onChange={(e) => void onFile(e)}
          />
        </label>
        <Button variant="quiet" onClick={downloadSample} disabled={!accounts.data?.length}>
          Descargar archivo de ejemplo
        </Button>
        {fileName && <span className="text-sm text-ink-2">{fileName}</span>}
      </div>
      <ErrorNote error={upload.error} />

      {report && (
        <Section
          title={`Resultado de ${report.originalMessageId}`}
          aside={<span className="text-sm">{groupStatus[report.groupStatus] ?? report.groupStatus}</span>}
        >
          <Table head={['EndToEndId', 'Monto', 'Estado', 'Motivo']}>
            {report.instructions.flatMap((i) =>
              i.transactions.map((t) => (
                <tr key={t.endToEndId}>
                  <td className="num px-4 py-3 text-xs">{t.endToEndId}</td>
                  <td className="px-4 py-3 text-right">
                    <Money amount={t.amount} currency={t.currency} />
                  </td>
                  <td
                    className={`px-4 py-3 ${t.status === 'RJCT' ? 'text-risk' : t.status === 'ACSC' ? 'text-ok' : 'text-quinoa'}`}
                  >
                    {txStatus[t.status]}
                  </td>
                  <td className="px-4 py-3 text-ink-2">
                    {t.reasonCode ? `${t.reasonCode}: ${t.info ?? ''}` : '—'}
                  </td>
                </tr>
              )),
            )}
          </Table>
        </Section>
      )}

      <Section title="Archivos recibidos">
        {batches.data?.length === 0 && <Empty>Todavía no has subido archivos.</Empty>}
        {batches.data && batches.data.length > 0 && (
          <Table head={['MsgId', 'Recibido', 'Pagos', 'Suma de control', 'Estado']}>
            {batches.data.map((b) => (
              <tr key={b.id}>
                <td className="num px-4 py-3 text-xs">{b.messageId}</td>
                <td className="px-4 py-3 text-ink-2">{formatDateTime(b.createdAt)}</td>
                <td className="num px-4 py-3">{b.numberOfTxs}</td>
                <td className="px-4 py-3 text-right">
                  <Money amount={b.controlSum} currency="USD" />
                </td>
                <td className="px-4 py-3">{groupStatus[b.status] ?? b.status}</td>
              </tr>
            ))}
          </Table>
        )}
      </Section>
    </>
  );
}
