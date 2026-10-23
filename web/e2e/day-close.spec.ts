import { expect, test } from '@playwright/test';

import { apiToken, newCustomer, uiLogin } from './support';

// A business day closes once: a rerun on the same day finds it closed, so both paths must end in the same state.
test('operations closes the day and savers get one day of interest', async ({ page, request }) => {
  const saver = await newCustomer(request, 10_000);
  await uiLogin(page, 'admin@andina.ec');
  await page.goto('/backoffice/operaciones');
  const close = page.getByRole('button', { name: 'Cerrar el día y abonar intereses' });
  const closed = page.getByRole('button', { name: 'Día cerrado' });
  await expect(close.or(closed)).toBeVisible();
  const closedByThisRun = await close.isVisible();
  if (closedByThisRun) await close.click();

  await expect(closed).toBeDisabled();
  await expect(page.getByRole('cell', { name: /admin@andina\.ec|Sistema/ }).first()).toBeVisible();
  if (!closedByThisRun) return;

  // 10,000.00 at 3.00 % a year, actual/365: 0.82 for the day
  const token = await apiToken(request, saver.email);
  const res = await request.get(`/api/v1/accounts/${saver.accountId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(((await res.json()) as { balance: number }).balance).toBe(10000.82);
});
