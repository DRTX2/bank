import { expect, test } from '@playwright/test';

import { totp } from '../src/lib/totp';
import {
  apiToken,
  apiTransfer,
  GERENTE_SECRET,
  mfaLogin,
  newCustomer,
  oddAmount,
  uiLogin,
  usd,
} from './support';

test('a customer sends a transfer and the money lands', async ({ page, request }) => {
  const sender = await newCustomer(request, 500);
  const receiver = await newCustomer(request, 0);
  const amount = oddAmount(10).toFixed(2);

  await uiLogin(page, sender.email);
  await page.getByRole('link', { name: 'Transferir' }).click();
  await page.getByLabel('Cuenta de destino').fill(receiver.number);
  await page.getByLabel('Cuenta de destino').blur();
  await expect(page.getByText(/^Titular: /)).toBeVisible();
  await page.getByLabel('Monto').fill(amount);
  await page.getByLabel('Concepto').fill('Prueba E2E');
  await page.getByRole('button', { name: 'Enviar transferencia' }).click();

  await expect(page.getByText('Completada')).toBeVisible();
  const token = await apiToken(request, receiver.email);
  const res = await request.get(`/api/v1/accounts/${receiver.accountId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(((await res.json()) as { balance: number }).balance).toBe(Number(amount));
});

test('a large transfer goes through risk review, then four-eyes approval with MFA step-up', async ({
  browser,
  request,
}) => {
  const sender = await newCustomer(request, 20_000);
  const receiver = await newCustomer(request, 0);
  const amount = oddAmount(6_000);
  // 6,000+ to a new beneficiary scores LARGE_AMOUNT + NEW_BENEFICIARY = 45: held for review, below decline (70)
  const transfer = await apiTransfer(request, sender, receiver, amount);
  expect(transfer.status).toBe('HELD_FOR_REVIEW');

  const compliance = await (await browser.newContext()).newPage();
  await uiLogin(compliance, 'cumplimiento@andina.ec');
  await compliance.goto('/backoffice/riesgo');
  const held = compliance.getByRole('listitem').filter({ hasText: usd(amount) });
  await held.getByLabel('Nota para auditoría').fill('Cliente confirmó por teléfono');
  await held.getByRole('button', { name: 'Liberar' }).click();
  await expect(held).toBeHidden();

  // Release puts it above the institution threshold, so a second person must approve
  const manager = await (await browser.newContext()).newPage();
  await mfaLogin(manager, 'gerente@andina.ec', GERENTE_SECRET);
  await manager.getByRole('link', { name: 'Aprobaciones' }).click();

  const pending = manager.getByRole('listitem').filter({ hasText: usd(amount) });
  await expect(pending.getByRole('button', { name: 'Aprobar y liquidar' })).toBeDisabled();
  // The login code is spent (single use), so step-up needs the next one
  await manager
    .getByLabel('Código de verificación para aprobar')
    .fill(await totp(GERENTE_SECRET, Date.now() + 30_000));
  await pending.getByRole('button', { name: 'Aprobar y liquidar' }).click();
  await expect(pending).toBeHidden();

  const token = await apiToken(request, sender.email);
  const res = await request.get(`/api/v1/transfers/${transfer.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(((await res.json()) as { status: string }).status).toBe('COMPLETED');
});
