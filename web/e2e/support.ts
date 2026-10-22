import { expect, type APIRequestContext, type Page } from '@playwright/test';

import { totp } from '../src/lib/totp';

export const PASSWORD = 'Kuntur2026!';
export const GERENTE_SECRET = 'KUNTURDEMOGERENTEMFAANDINAKUNTUR';

export interface Customer {
  email: string;
  accountId: string;
  number: string;
}

export async function apiToken(
  request: APIRequestContext,
  email: string,
  tenantCode = 'ANDN',
): Promise<string> {
  const res = await request.post('/api/v1/auth/login', { data: { tenantCode, email, password: PASSWORD } });
  expect(res.ok(), `login ${email}: ${res.status()} ${await res.text()}`).toBeTruthy();
  return ((await res.json()) as { accessToken: string }).accessToken;
}

/** Onboards a fresh customer through the teller API: no transfer history, so the risk engine scores predictably. */
export async function newCustomer(request: APIRequestContext, deposit: number): Promise<Customer> {
  const teller = await apiToken(request, 'cajero@andina.ec');
  const email = `e2e-${crypto.randomUUID().slice(0, 8)}@andina.ec`;
  const res = await request.post('/api/v1/customers', {
    headers: { Authorization: `Bearer ${teller}` },
    data: { email, fullName: 'Cliente E2E', nationalId: '1700000000', password: PASSWORD, corporate: false },
  });
  expect(res.status()).toBe(201);
  const account = (await res.json()) as { id: string; number: string };
  if (deposit > 0) {
    const dep = await request.post(`/api/v1/accounts/${account.id}/deposits`, {
      headers: { Authorization: `Bearer ${teller}` },
      data: { amount: deposit },
    });
    expect(dep.ok(), `deposit: ${dep.status()} ${await dep.text()}`).toBeTruthy();
  }
  return { email, accountId: account.id, number: account.number };
}

export async function apiTransfer(
  request: APIRequestContext,
  from: Customer,
  to: Customer,
  amount: number,
): Promise<{ id: string; status: string }> {
  const token = await apiToken(request, from.email);
  const res = await request.post('/api/v1/transfers', {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': crypto.randomUUID() },
    data: {
      sourceAccountId: from.accountId,
      destinationAccountNumber: to.number,
      amount,
      remittanceInfo: 'E2E',
    },
  });
  expect(res.status()).toBe(201);
  return (await res.json()) as { id: string; status: string };
}

/** Signs in and waits until the app has the session, unless the user has MFA (then the code step follows). */
export async function uiLogin(
  page: Page,
  email: string,
  tenant = 'Cooperativa Andina',
  mfa = false,
): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Institución').selectOption({ label: tenant });
  await page.getByLabel('Correo').fill(email);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  if (!mfa) await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

/**
 * Login with the second factor. Codes are single-use, and a previous run may have spent this window and the next
 * (login + step-up), so on "already used" it waits for the following window, up to three times.
 */
export async function mfaLogin(page: Page, email: string, secret: string): Promise<void> {
  await uiLogin(page, email, 'Cooperativa Andina', true);
  const field = page.getByLabel('Código de verificación');
  await expect(field).toBeVisible();
  for (let attempt = 0; attempt < 3; attempt++) {
    await field.fill(await totp(secret));
    await page.getByRole('button', { name: 'Verificar e ingresar' }).click();
    const signedIn = page.waitForURL((url) => !url.pathname.startsWith('/login')).then(() => true);
    const spent = page
      .getByRole('alert')
      .waitFor()
      .then(() => false);
    if (await Promise.race([signedIn, spent])) return;
    await page.waitForTimeout(30_000 - (Date.now() % 30_000) + 500);
  }
  throw new Error(`MFA login failed for ${email}`);
}

/** Same format the UI uses, so assertions match what a person reads. */
export function usd(amount: number): string {
  return new Intl.NumberFormat('es-EC', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
  }).format(amount);
}

/** A non-round amount in [min + 0.01, min + 99.99]: unique per run, never trips the ROUND_AMOUNT rule. */
export function oddAmount(min: number): number {
  return Math.round(min * 100 + Math.floor(Math.random() * 9_999) + 1) / 100;
}
