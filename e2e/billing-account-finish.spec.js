const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

function subscription(overrides = {}) {
  return {
    businessId: '11111111-1111-1111-1111-111111111111',
    plan: 'PRO',
    publicPlanCode: 'PRO',
    planName: 'Profesional',
    status: 'ACTIVE',
    serviceAllowed: true,
    maxConcurrentCalls: 2,
    includedMinutes: 500,
    usedMinutes: 325,
    overageMinutes: 0,
    currentPeriodStart: '2026-09-01T00:00:00Z',
    currentPeriodEnd: '2026-10-01T00:00:00Z',
    graceUntil: null,
    billingProviderConnected: true,
    entitlements: [],
    legacyFallback: false,
    ...overrides
  };
}

async function mockAccount(page, options = {}) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: options.email || 'admin@negocio.cl',
    roles: options.roles || ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json(
    options.subscription || subscription()
  )));
  await page.route('**/api/v1/usage/summary?**', route => {
    if (options.usageForbidden) {
      return route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'forbidden' })
      });
    }
    return route.fulfill(json(options.usage || [
      {
        meterKey: 'VOICE_SECONDS',
        unit: 'SECONDS',
        quantity: 19500,
        estimatedCostUsd: 12.30,
        actualCostUsd: 11.80,
        eventCount: 24
      },
      {
        meterKey: 'WHATSAPP_MESSAGES',
        unit: 'MESSAGES',
        quantity: 38,
        estimatedCostUsd: 0.80,
        actualCostUsd: 0.80,
        eventCount: 38
      }
    ]));
  });
}

test('account explains plan usage and billing state without exposing technical enums', async ({ page }) => {
  await mockAccount(page);
  await page.goto('/account.html');

  await expect(page.getByRole('heading', { level: 1, name: 'Plan y facturación' })).toBeVisible();
  await expect(page.locator('#accountEmail')).toHaveText('admin@negocio.cl');
  await expect(page.locator('#accountRole')).toHaveText('Administrador');
  await expect(page.locator('#planName')).toHaveText('Profesional');
  await expect(page.locator('#planStatus')).toHaveText('Activo');
  await expect(page.locator('#voiceUsage')).toContainText('325 de 500 min');
  await expect(page.locator('#voiceUsageProgress')).toHaveAttribute('aria-valuenow', '65');
  await expect(page.locator('#billingConnection')).toContainText('Facturación conectada');
  await expect(page.getByText('Llamadas de voz')).toBeVisible();
  await expect(page.getByText('Mensajes de WhatsApp')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('VOICE_SECONDS');
  await expect(page.locator('body')).not.toContainText('WHATSAPP_MESSAGES');
  await expect(page.locator('body')).not.toContainText('ACTIVE');

  const plans = page.getByRole('link', { name: 'Ver planes' });
  await expect(plans).toHaveAttribute('href', '/pricing.html');
  await expect(page.getByRole('button', { name: /pagar|cobrar|suscrib/i })).toHaveCount(0);
});

test('account keeps the core subscription useful when detailed usage is forbidden', async ({ page }) => {
  await mockAccount(page, { roles: ['OPERATOR'], usageForbidden: true });
  await page.goto('/account.html');

  await expect(page.locator('#planName')).toHaveText('Profesional');
  await expect(page.locator('#accountRole')).toHaveText('Operador');
  await expect(page.locator('#usageState')).toContainText('solo administradores');
  await expect(page.locator('#accountError')).toBeHidden();
});

test('account has an intentional subscription error state and remains responsive', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@negocio.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'subscription unavailable' })
  }));

  await page.goto('/account.html');

  await expect(page.locator('#accountError')).toBeVisible();
  await expect(page.locator('#accountError')).toContainText('No pudimos cargar tu plan');
  await expect(page.locator('#accountEmail')).toHaveText('admin@negocio.cl');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});
