import { money } from './format';
import type { Notification } from './types';

/** Title and detail of a toast, in Spanish, for every notification the server pushes. */
export function toastText(n: Notification): [string, string] {
  if ('transferId' in n) {
    return [
      `${n.direction === 'IN' ? 'Recibiste' : 'Enviaste'} ${money(n.amount, n.currency)}`,
      `${n.direction === 'IN' ? 'Desde' : 'A'} ${n.counterparty} (${n.status.toLowerCase().replaceAll('_', ' ')})`,
    ];
  }
  const amount = money(n.amount, n.currency);
  const cuota = `${String(n.installment)}/${String(n.installments)}`;
  switch (n.type) {
    case 'LoanDisbursed':
      return [
        `Recibiste un crédito de ${amount}`,
        'Ya está en tu cuenta. Revisa la tabla de pagos en Mis créditos.',
      ];
    case 'InstallmentCollected':
      return [`Pagaste la cuota ${cuota} de tu crédito`, `Debitamos ${amount} de tu cuenta.`];
    case 'InstallmentMissed':
      return [`No pudimos cobrar la cuota ${cuota}`, `Deposita ${amount} para ponerte al día.`];
    case 'InterestCredited':
      return [`Ganaste ${amount} de intereses`, 'Abonados en el cierre del día a tu cuenta de ahorro.'];
  }
}
