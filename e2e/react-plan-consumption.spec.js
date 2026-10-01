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
    planName: 'Pro',
    status: 'ACTIVE',
    serviceAllowed: true,
    maxConcurrentCalls: 10,
    includedMinutes: 500,
    usedMinutes: 72,
    overageMinutes: 0,
    currentPeriodStart: '2026-09-01T00:00:00Z',
    currentPeriodEnd: '2026-10-31T00:00:00Z',
    graceUntil: null,
    billingProviderConnected: false,
    entitlements: [],
    legacyFallback: false,
    ...overrides
  };
}

async function bootAuthenticated(page, options = {}) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@negocio.cl',
    roles: options.roles || ['BUSINESS_ADMIN']
  })));

  await page.route('**/api/v1/subscription', route => {
    if (options.subscriptionError) {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'subscription unavailable' })
      });
    }
    return route.fulfill(json(subscription(options.subscription)));
  });

  await page.route('**/api/v1/usage/status', route => route.fulfill(json({
    includedMinutes: 500,
    usedMinutes: 72,
    overageMinutes: 0,
    usagePercent: 14.4,
    alertLevel: 'NORMAL',
    overagePricePerMinuteClp: 109,
    estimatedOverageChargeClp: 0,
    safetyLimitMinutes: 5000,
    safetyRemainingMinutes: 4928,
    safetyExceeded: false
  })));

  await page.route('**/api/v1/usage/summary?**', route => {
    if (options.usageForbidden) {
      return route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'forbidden' })
      });
    }
    return route.fulfill(json([
      {
        meterKey: 'VOICE_SECONDS',
        unit: 'SECONDS',
        quantity: 4320,
        eventCount: 76
      },
      {
        meterKey: 'WHATSAPP_MESSAGES',
        unit: 'MESSAGES',
        quantity: 69,
        eventCount: 69
      }
    ]));
  });
}

test.describe('React Plan y consumo pilot', () => {
  test('renders the simplified real-data plan experience', async ({ page }) => {
    await bootAuthenticated(page);
    await page.goto('/app/plan');

    await expect(page.getByRole('heading', { level: 1, name: 'Plan y consumo' })).toBeVisible();
    await expect(page.getByTestId('plan-name')).toHaveText('Pro');
    await expect(page.getByTestId('plan-usage')).toContainText('72');
    await expect(page.getByTestId('plan-usage')).toContainText('500');
    await expect(page.getByTestId('remaining-minutes')).toContainText('428');
    await expect(page.getByTestId('voice-progress')).toHaveAttribute('aria-valuenow', '14');

    await expect(page.getByTestId('metric-calls')).toContainText('76');
    await expect(page.getByTestId('metric-minutes')).toContainText('72');
    await expect(page.getByTestId('metric-messages')).toContainText('69');

    await expect(page.getByTestId('projection')).toContainText('500');
    await expect(page.getByTestId('billing-state')).toContainText('Pagos automáticos aún no habilitados');

    await expect(page.getByRole('link', { name: /cambiar plan|ver planes/i })).toHaveAttribute('href', '/pricing.html');
    await expect(page.getByRole('button', { name: /pagar|cobrar|suscrib/i })).toHaveCount(0);

    await expect(page.locator('body')).not.toContainText('Protección excepcional');
    await expect(page.locator('body')).not.toContainText('Exceso estimado');
    await expect(page.locator('body')).not.toContainText('Llamadas simultáneas');
  });

  test('shows an intentional loading state before subscription data arrives', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
    await page.route('**/api/v1/auth/me', route => route.fulfill(json({
      email: 'admin@negocio.cl',
      roles: ['BUSINESS_ADMIN']
    })));

    let releaseSubscription;
    await page.route('**/api/v1/subscription', async route => {
      await new Promise(resolve => { releaseSubscription = resolve; });
      await route.fulfill(json(subscription()));
    });

    await page.goto('/app/plan');
    await expect(page.getByRole('status')).toContainText('Cargando');

    releaseSubscription();
  });

  test('keeps the screen useful when detailed usage is restricted', async ({ page }) => {
    await bootAuthenticated(page, { roles: ['OPERATOR'], usageForbidden: true });
    await page.goto('/app/plan');

    await expect(page.getByTestId('plan-name')).toHaveText('Pro');
    await expect(page.getByTestId('usage-restricted')).toContainText('propietarios y administradores');
    await expect(page.getByTestId('metric-calls')).toContainText('—');
    await expect(page.getByTestId('metric-messages')).toContainText('—');
  });

  test('shows a recoverable subscription error without inventing billing data', async ({ page }) => {
    await bootAuthenticated(page, { subscriptionError: true });
    await page.goto('/app/plan');

    await expect(page.getByRole('alert')).toContainText('No pudimos cargar tu plan');
    await expect(page.getByTestId('billing-state')).not.toContainText('$');
  });

  test('redirects to auth when the API reports an expired session', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'expired-token'));
    await page.route('**/api/v1/auth/me', route => route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'expired' })
    }));
    await page.route('**/api/v1/subscription', route => route.fulfill(json(subscription())));

    await page.goto('/app/plan');
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
  });

  test('has no page-level horizontal overflow on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await bootAuthenticated(page);
    await page.goto('/app/plan');

    await expect(page.getByTestId('plan-name')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  });
});
