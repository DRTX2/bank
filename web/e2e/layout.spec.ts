import { expect, test } from '@playwright/test';

import { uiLogin } from './support';

const pages: [string, string[]][] = [
  [
    'ana@andina.ec',
    [
      '/banca',
      '/banca/transferir',
      '/banca/pagos-masivos',
      '/banca/creditos',
      '/simuladores',
      '/transferencias',
    ],
  ],
  [
    'admin@andina.ec',
    ['/backoffice/operaciones', '/backoffice/usuarios', '/backoffice/roles', '/backoffice/auditoria'],
  ],
  ['cumplimiento@andina.ec', ['/backoffice/riesgo', '/backoffice/clientes']],
  ['auditor@andina.ec', ['/backoffice/creditos']],
];

for (const [email, paths] of pages) {
  test(`${email}: no page scrolls sideways`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await uiLogin(page, email);
    await expect(page).not.toHaveURL(/login/);
    for (const path of paths) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow, path).toBeLessThanOrEqual(0);
    }
    expect(errors).toEqual([]);
  });
}
