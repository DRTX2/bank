const moneyFormatters = new Map<string, Intl.NumberFormat>();

export function money(amount: number, currency = 'USD'): string {
  let f = moneyFormatters.get(currency);
  if (!f) {
    f = new Intl.NumberFormat('es-EC', { style: 'currency', currency, minimumFractionDigits: 2 });
    moneyFormatters.set(currency, f);
  }
  return f.format(amount);
}

const dateTime = new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium' });

export const formatDateTime = (iso: string): string => dateTime.format(new Date(iso));
export const formatDate = (iso: string): string => dateOnly.format(new Date(iso));

/** EC12 ANDN 1234 5678 9012 34: grouped in fours, the way people read account numbers aloud. */
export function groupIban(iban: string): string {
  return iban.replace(/(.{4})/g, '$1 ').trim();
}

export function downloadText(filename: string, content: string, type = 'application/xml'): void {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

const systemRoles: Record<string, string> = {
  CUSTOMER: 'Cliente',
  CORPORATE_CUSTOMER: 'Cliente empresarial',
  TELLER: 'Cajero',
  BRANCH_MANAGER: 'Gerente de agencia',
  COMPLIANCE_OFFICER: 'Oficial de cumplimiento',
  AUDITOR: 'Auditor',
  TENANT_ADMIN: 'Administrador',
  PLATFORM_ADMIN: 'Operador de plataforma',
};

/** System roles get their Spanish name; custom roles (ANALISTA_JUNIOR) read as "analista junior". */
export function roleLabel(code: string): string {
  return systemRoles[code] ?? code.toLowerCase().replaceAll('_', ' ');
}
