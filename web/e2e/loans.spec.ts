import { expect, test } from '@playwright/test';

import { apiToken, newCustomer, PASSWORD, uiLogin } from './support';

test('a manager grants a loan and the customer sees the money and the schedule', async ({
  page,
  browser,
  request,
}) => {
  const customer = await newCustomer(request, 0);
  // A manager without MFA, so this test never waits for a TOTP window (the approvals test covers step-up)
  const admin = await apiToken(request, 'admin@andina.ec');
  const manager = `e2e-gerente-${crypto.randomUUID().slice(0, 8)}@andina.ec`;
  const created = await request.post('/api/v1/users', {
    headers: { Authorization: `Bearer ${admin}` },
    data: { email: manager, fullName: 'Gerente E2E', password: PASSWORD, roles: ['BRANCH_MANAGER'] },
  });
  expect(created.status()).toBe(201);

  await uiLogin(page, manager);
  await page.goto('/backoffice/creditos');
  await page.getByLabel('Cuenta del cliente').selectOption({ value: customer.accountId });
  await page.getByLabel('Monto').fill('1200');
  await page.getByLabel('Tasa anual (%)').fill('12');
  await page.getByLabel('Plazo (meses)').fill('12');
  await page.getByRole('button', { name: 'Otorgar y desembolsar' }).click();
  await expect(page.getByRole('heading', { name: 'Tabla de pagos' })).toBeVisible();

  // 1,200.00 at 12 % over 12 months: French installment of 106.62, credited to the customer's account
  const own = await browser.newPage();
  await uiLogin(own, customer.email);
  await own.goto('/banca/creditos');
  await expect(own.getByText('Próxima cuota:')).toBeVisible();
  await expect(own.getByRole('cell', { name: /106,62/ }).first()).toBeVisible();
  await own.goto('/banca');
  await expect(own.getByText('Desembolso de crédito')).toBeVisible();
});
