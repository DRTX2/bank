import { describe, expect, it } from 'vitest';

import { toastText } from '../lib/notifications';

describe('toastText', () => {
  it('speaks about loans and interest, not transfers', () => {
    const base = { id: 'n1', currency: 'USD', installment: 3, installments: 12 };
    expect(toastText({ ...base, type: 'InstallmentMissed', amount: 106.62 })[0]).toBe(
      'No pudimos cobrar la cuota 3/12',
    );
    expect(toastText({ ...base, type: 'LoanDisbursed', amount: 1200, installment: null })[0]).toMatch(
      /^Recibiste un crédito de \$/,
    );
  });

  it('keeps the transfer wording', () => {
    const [title, detail] = toastText({
      transferId: 't1',
      type: 'TransferCompleted',
      direction: 'IN',
      amount: 10,
      currency: 'USD',
      counterparty: 'EC12',
      status: 'COMPLETED',
      failureCode: null,
    });
    expect(title).toMatch(/^Recibiste /);
    expect(detail).toBe('Desde EC12 (completed)');
  });
});
