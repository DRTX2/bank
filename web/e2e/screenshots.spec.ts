import { test, type Browser, type Page } from '@playwright/test';

import {
  apiToken,
  apiTransfer,
  GERENTE_SECRET,
  mfaLogin,
  newCustomer,
  oddAmount,
  PASSWORD,
  uiLogin,
} from './support';

/**
 * Regenerates the README screenshots from the running demo stack: `npm run screenshots` (after `make up`).
 * Skipped in the normal E2E run.
 */
const OUT = new URL('../../docs/screenshots/', import.meta.url).pathname;
const desktop = { viewport: { width: 1440, height: 900 } };

test.skip(!process.env.SCREENSHOTS, 'only when regenerating README screenshots');
test.describe.configure({ mode: 'serial' });

async function as(
  browser: Browser,
  email: string,
  options: Parameters<Browser['newPage']>[0] = desktop,
): Promise<Page> {
  const page = await browser.newPage(options);
  await uiLogin(page, email);
  return page;
}

async function shot(page: Page, name: string, path?: string): Promise<void> {
  if (path) await page.goto(path);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1_200);
  await page.screenshot({ path: `${OUT}${name}.png` });
}

test('customer screens', async ({ browser }) => {
  const login = await browser.newPage(desktop);
  await login.goto('/login');
  await shot(login, 'login');
  const page = await as(browser, 'ana@andina.ec');
  await shot(page, 'customer-overview', '/banca');
  await page.goto('/simuladores');
  await page.getByRole('button', { name: 'Calcular cuotas' }).click();
  await shot(page, 'simulators');
  await shot(page, 'my-loans', '/banca/creditos');
  const dark = await as(browser, 'ana@andina.ec', { ...desktop, colorScheme: 'dark' });
  await shot(dark, 'customer-overview-dark', '/banca');
  const mobile = await as(browser, 'ana@andina.ec', { viewport: { width: 390, height: 844 } });
  await shot(mobile, 'mobile-overview', '/banca');
});

test('backoffice screens', async ({ browser }) => {
  const admin = await as(browser, 'admin@andina.ec');
  await admin.goto('/backoffice/operaciones');
  await admin.getByRole('button', { name: 'Iniciar simulación' }).click();
  await admin.waitForTimeout(6_000);
  await shot(admin, 'operations-live');
  // Closes today's business day for Andina; the day-close E2E accepts an already closed day.
  const close = admin.getByRole('button', { name: 'Cerrar el día y abonar intereses' });
  if (await close.isVisible()) await close.click();
  await admin.getByRole('button', { name: 'Día cerrado' }).waitFor();
  await admin.getByRole('heading', { name: 'Cierre de día' }).scrollIntoViewIfNeeded();
  await admin.waitForTimeout(800);
  await admin.screenshot({ path: `${OUT}day-close.png` });
  await shot(admin, 'roles-matrix', '/backoffice/roles');
  const compliance = await as(browser, 'cumplimiento@andina.ec');
  await shot(compliance, 'risk-review', '/backoffice/riesgo');
});

test('MFA and step-up approval', async ({ browser, request }) => {
  // A transfer waiting for the manager: held by risk, released by compliance, now above the four-eyes threshold
  const sender = await newCustomer(request, 20_000);
  const receiver = await newCustomer(request, 0);
  const held = await apiTransfer(request, sender, receiver, oddAmount(7_400));
  await request.post(`/api/v1/transfers/${held.id}/review`, {
    headers: { Authorization: `Bearer ${await apiToken(request, 'cumplimiento@andina.ec')}` },
    data: { release: true, note: 'Proveedor verificado por teléfono' },
  });

  const page = await browser.newPage(desktop);
  await page.goto('/login');
  await page.getByLabel('Correo').fill('gerente@andina.ec');
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.getByLabel('Código de verificación').waitFor();
  await shot(page, 'mfa-login');
  await page.close();
  const manager = await browser.newPage(desktop);
  await mfaLogin(manager, 'gerente@andina.ec', GERENTE_SECRET);
  await shot(manager, 'approvals-step-up', '/backoffice/aprobaciones');
  await manager.goto('/backoffice/creditos');
  await manager.getByRole('button', { name: 'Ana Morales' }).click();
  await shot(manager, 'loans-portfolio');
  await manager.getByRole('button', { name: 'Camila Ortiz' }).click();
  await manager.getByRole('button', { name: 'Aprobar y desembolsar' }).scrollIntoViewIfNeeded();
  await shot(manager, 'loan-approval');
});
