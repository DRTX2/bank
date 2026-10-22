import { expect, test } from '@playwright/test';

import { apiTransfer, newCustomer, oddAmount, uiLogin, usd } from './support';

test('payments reach the ops feed and the receiver over WebSocket, without reloading', async ({
  browser,
  request,
}) => {
  const sender = await newCustomer(request, 500);
  const receiver = await newCustomer(request, 0);

  const admin = await (await browser.newContext()).newPage();
  await uiLogin(admin, 'admin@andina.ec');
  await admin.goto('/backoffice/operaciones');
  const customer = await (await browser.newContext()).newPage();
  await uiLogin(customer, receiver.email);
  await expect(customer.getByText('Hola,')).toBeVisible();
  // Let both STOMP subscriptions settle before the event is published
  await admin.waitForTimeout(1_500);

  const amount = oddAmount(20);
  await apiTransfer(request, sender, receiver, amount);

  await expect(admin.getByRole('listitem').filter({ hasText: usd(amount) })).toBeVisible();
  await expect(customer.getByText(`Recibiste ${usd(amount)}`)).toBeVisible();
});
