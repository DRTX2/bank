import { expect, test } from '@playwright/test';

import { apiToken, newCustomer, uiLogin } from './support';

test('a customer cannot reach the backoffice, in the UI or the API', async ({ page, request }) => {
  const customer = await newCustomer(request, 0);
  await uiLogin(page, customer.email);
  await expect(page.getByRole('link', { name: 'Usuarios' })).toHaveCount(0);
  await page.goto('/backoffice/usuarios');
  await expect(page.getByText('Tu rol no tiene acceso a esta sección.')).toBeVisible();

  const token = await apiToken(request, customer.email);
  const res = await request.get('/api/v1/users', { headers: { Authorization: `Bearer ${token}` } });
  expect(res.status()).toBe(403);
});

test('another institution’s staff cannot see Andina customers', async ({ request }) => {
  const customer = await newCustomer(request, 0);
  const pacifico = await apiToken(request, 'cajero@pacifico.ec', 'PACF');
  const res = await request.get(`/api/v1/accounts/${customer.accountId}`, {
    headers: { Authorization: `Bearer ${pacifico}` },
  });
  expect(res.status()).toBe(404);
});
