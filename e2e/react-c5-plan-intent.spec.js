const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

const offers = [
  { code: 'EMPRENDE', name: 'Emprende', monthlyPriceClp: 24990, includedMinutes: 100, maxConcurrentCalls: 1, customPricing: false, recommended: false },
  { code: 'NEGOCIO', name: 'Negocio', monthlyPriceClp: 39990, includedMinutes: 250, maxConcurrentCalls: 3, customPricing: false, recommended: true },
  { code: 'PRO', name: 'Pro', monthlyPriceClp: 69990, includedMinutes: 500, maxConcurrentCalls: 10, customPricing: false, recommended: false },
  { code: 'ENTERPRISE', name: 'Enterprise', monthlyPriceClp: 119990, includedMinutes: 1000, maxConcurrentCalls: 10, customPricing: true, recommended: false }
];

test.describe('C5-A: public plan interest without automated payment', () => {
  test('safe plan cards hand off plan choice to registration but do not call checkout', async ({ page }) => {
    const mutations = [];
    await page.route('**/api/v1/**', route => {
      if (route.request().method() !== 'GET') {
        mutations.push(route.request().url());
      }
      return route.fulfill(json(offers));
    });

    await page.goto('/app/pricing');
    await expect(page.getByRole('heading', { name: 'Negocio' })).toBeVisible();
    await expect(page.locator('article').filter({ has: page.getByRole('heading', { name: 'Emprende' }) }).getByRole('link', { name: 'Empezar' })).toHaveAttribute('href', '/app/auth?plan=EMPRENDE');
    await expect(page.locator('article').filter({ has: page.getByRole('heading', { name: 'Negocio' }) }).getByRole('link', { name: 'Empezar' })).toHaveAttribute('href', '/app/auth?plan=NEGOCIO');
    await expect(page.locator('article').filter({ has: page.getByRole('heading', { name: 'Pro', exact: true }) }).getByRole('link', { name: 'Empezar' })).toHaveAttribute('href', '/app/auth?plan=PRO');
    await expect(page.locator('article').filter({ has: page.getByRole('heading', { name: 'Enterprise' }) }).getByRole('link', { name: 'Cotizar' })).toHaveAttribute('href', '/');
    expect(mutations).toEqual([]);

    await page.locator('article').filter({ has: page.getByRole('heading', { name: 'Negocio' }) }).getByRole('link', { name: 'Empezar' }).click();
    await expect(page).toHaveURL(/\/app\/auth\?plan=NEGOCIO$/);
    await expect(page.getByTestId('selected-public-plan')).toContainText('Plan de interés: Negocio');
    await expect(page.getByTestId('selected-public-plan')).toContainText('no inicia un cobro');
    expect(mutations).toEqual([]);
  });

  test('registration retains selected interest and never auto-submits checkout', async ({ page }) => {
    let checkoutCalls = 0;
    let registerCalls = 0;
    await page.route('**/api/v1/**', route => {
      const req = route.request();
      const url = new URL(req.url());
      if (url.pathname === '/api/v1/auth/register' && req.method() === 'POST') {
        registerCalls++;
        return route.fulfill(json({
          accessToken: 'c5-registered-test-token',
          user: { email: 'owner@ejemplo.cl', roles: ['BUSINESS_ADMIN'] }
        }));
      }
      if (url.pathname === '/api/v1/billing/checkout') {
        checkoutCalls++;
        return route.fulfill(json({ message: 'Should not have been called' }, 503));
      }
      if (url.pathname === '/api/v1/auth/me') {
        return route.fulfill(json({ email: 'owner@ejemplo.cl', roles: ['BUSINESS_ADMIN'] }));
      }
      if (url.pathname === '/api/v1/subscription') {
        return route.fulfill(json({
          plan: 'BASIC', publicPlanCode: 'EMPRENDE', planName: 'Emprende',
          status: 'TRIALING', includedMinutes: 100, usedMinutes: 0
        }));
      }
      if (url.pathname === '/api/v1/billing/status') {
        return route.fulfill(json({
          billingEnabled: false, checkoutConfigured: false, currentPlanCode: 'EMPRENDE',
          currentPlanName: 'Emprende', subscriptionStatus: 'TRIALING'
        }));
      }
      if (url.pathname === '/api/v1/public/pricing') return route.fulfill(json(offers));
      if (url.pathname === '/api/v1/usage/status') return route.fulfill(json({ includedMinutes: 100, usedMinutes: 0 }));
      if (url.pathname === '/api/v1/usage/summary') return route.fulfill(json([]));
      return route.fulfill(json({}));
    });

    await page.goto('/app/auth?plan=NEGOCIO');
    await expect(page.getByTestId('selected-public-plan')).toContainText('Negocio');
    await page.getByLabel('Nombre del negocio').fill('Negocio Piloto');
    await page.getByLabel('Correo').fill('owner@ejemplo.cl');
    await page.getByLabel('Contraseña').fill('clave-de-prueba-123');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    await expect(page).toHaveURL(/\/app\/plan\?plan=NEGOCIO$/);
    await expect(page.getByTestId('plan-intent-unpaid')).toContainText('Elegiste Negocio');
    await expect(page.getByTestId('plan-intent-unpaid')).toContainText('ningún cambio ni cobro');
    await expect(page.getByTestId('billing-state')).toContainText('Pagos automáticos aún no habilitados');
    expect(registerCalls).toBe(1);
    expect(checkoutCalls).toBe(0);
  });

  test('unknown plan query cannot become a selected plan or a checkout', async ({ page }) => {
    let checkoutCalls = 0;
    await page.route('**/api/v1/billing/checkout', route => {
      checkoutCalls++;
      return route.fulfill(json({}, 503));
    });
    await page.goto('/app/auth?plan=ENTERPRISE%26checkout%3Dtrue');
    await expect(page.getByRole('button', { name: 'Crear cuenta' })).toBeVisible();
    await expect(page.getByTestId('selected-public-plan')).toHaveCount(0);
    expect(checkoutCalls).toBe(0);
  });

  test('existing platform admin never inherits a business plan redirect', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'platform-test-token'));
    await page.route('**/api/v1/auth/me', route => route.fulfill(json({
      email: 'admin@helvoca.cl', roles: ['PLATFORM_ADMIN']
    })));
    await page.route('**/api/v1/**', route => route.fulfill(json({})));
    await page.goto('/app/auth?plan=PRO');
    await expect(page).toHaveURL(/\/app\/platform\/?$/);
  });
});
