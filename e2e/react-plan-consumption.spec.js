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

function billingStatus(overrides = {}) {
  return {
    provider: null,
    billingEnabled: false,
    checkoutConfigured: false,
    currentPlanCode: 'PRO',
    currentPlanName: 'Pro',
    currentMonthlyPriceClp: 69990,
    subscriptionStatus: 'ACTIVE',
    pendingPlanCode: null,
    pendingPlanName: null,
    pendingMonthlyPriceClp: null,
    checkoutUrl: null,
    awaitingProviderVerification: false,
    ...overrides
  };
}

function publicPlans() {
  return [
    { code: 'EMPRENDE', name: 'Emprende', monthlyPriceClp: 24990, includedMinutes: 100, maxConcurrentCalls: 1, overagePerMinuteClp: 149, customPricing: false, recommended: false },
    { code: 'NEGOCIO', name: 'Negocio', monthlyPriceClp: 39990, includedMinutes: 250, maxConcurrentCalls: 3, overagePerMinuteClp: 129, customPricing: false, recommended: true },
    { code: 'PRO', name: 'Pro', monthlyPriceClp: 69990, includedMinutes: 500, maxConcurrentCalls: 10, overagePerMinuteClp: 109, customPricing: false, recommended: false },
    { code: 'ENTERPRISE', name: 'Enterprise', monthlyPriceClp: 119990, includedMinutes: 1000, maxConcurrentCalls: 10, overagePerMinuteClp: null, customPricing: true, recommended: false }
  ];
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

  let billingStatusReads = 0;
  let checkoutCalls = 0;
  let checkoutPayload = null;

  await page.route('**/api/v1/billing/status', route => {
    billingStatusReads += 1;
    return route.fulfill(json(billingStatus(options.billingStatus)));
  });

  await page.route('**/api/v1/public/pricing', route => route.fulfill(json(options.publicPlans || publicPlans())));

  await page.route('**/api/v1/billing/checkout', async route => {
    checkoutCalls += 1;
    checkoutPayload = route.request().postDataJSON();
    const response = typeof options.checkoutResponse === 'function'
      ? await options.checkoutResponse(checkoutPayload)
      : options.checkoutResponse;
    return route.fulfill(json(response || {
      subscriptionId: 'pre-e2e-default',
      checkoutUrl: 'https://checkout.example.test/default',
      planCode: checkoutPayload.plan,
      planName: checkoutPayload.plan,
      monthlyPriceClp: 0,
      reused: false
    }));
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

  return {
    billingStatusReads: () => billingStatusReads,
    checkoutCalls: () => checkoutCalls,
    checkoutPayload: () => checkoutPayload
  };
}

test.describe('React Plan y consumo pilot', () => {
  test('renders the simplified real-data plan experience', async ({ page }) => {
    await bootAuthenticated(page);
    await page.goto('/app/index.html');

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

  test('starts a plan checkout only after confirmation and keeps the current plan until payment is verified', async ({ page }) => {
    await page.addInitScript(() => {
      window.__openedCheckoutUrls = [];
      window.open = url => {
        window.__openedCheckoutUrls.push(url);
        return { closed: false };
      };
    });

    const calls = await bootAuthenticated(page, {
      subscription: {
        plan: 'BASIC',
        publicPlanCode: 'EMPRENDE',
        planName: 'Emprende',
        includedMinutes: 100,
        usedMinutes: 37
      },
      billingStatus: {
        provider: null,
        billingEnabled: true,
        checkoutConfigured: true,
        currentPlanCode: 'EMPRENDE',
        currentPlanName: 'Emprende',
        currentMonthlyPriceClp: 24990,
        subscriptionStatus: 'TRIALING'
      },
      checkoutResponse: {
        subscriptionId: 'pre-e2e-1',
        checkoutUrl: 'https://checkout.example.test/pre-e2e-1',
        planCode: 'NEGOCIO',
        planName: 'Negocio',
        monthlyPriceClp: 39990,
        reused: false
      }
    });

    await page.goto('/app/plan');

    await expect(page.getByRole('heading', { name: 'Gestionar plan' })).toBeVisible();
    await expect(page.getByTestId('billing-current-plan')).toContainText('Emprende');
    expect(calls.checkoutCalls()).toBe(0);

    page.once('dialog', async dialog => {
      expect(dialog.message()).toContain('no se activará hasta verificar el pago');
      await dialog.dismiss();
    });
    await page.getByRole('button', { name: 'Elegir Negocio' }).click();
    expect(calls.checkoutCalls()).toBe(0);

    page.once('dialog', async dialog => {
      expect(dialog.message()).toContain('no se activará hasta verificar el pago');
      await dialog.accept();
    });
    await page.getByRole('button', { name: 'Elegir Negocio' }).click();

    await expect.poll(() => calls.checkoutCalls()).toBe(1);
    expect(calls.checkoutPayload()).toEqual({ plan: 'NEGOCIO' });
    await expect(page.getByTestId('billing-current-plan')).toContainText('Emprende');
    await expect(page.getByTestId('billing-pending-plan')).toContainText('Negocio');
    await expect(page.getByTestId('billing-pending-plan')).toContainText('verificar');
    await expect.poll(() => page.evaluate(() => window.__openedCheckoutUrls)).toEqual([
      'https://checkout.example.test/pre-e2e-1'
    ]);
  });

  test('continues an existing pending checkout without creating a duplicate checkout', async ({ page }) => {
    await page.addInitScript(() => {
      window.__openedCheckoutUrls = [];
      window.open = url => {
        window.__openedCheckoutUrls.push(url);
        return { closed: false };
      };
    });

    const calls = await bootAuthenticated(page, {
      subscription: {
        plan: 'BASIC',
        publicPlanCode: 'EMPRENDE',
        planName: 'Emprende',
        includedMinutes: 100,
        usedMinutes: 37
      },
      billingStatus: {
        provider: 'mercadopago',
        billingEnabled: true,
        checkoutConfigured: true,
        currentPlanCode: 'EMPRENDE',
        currentPlanName: 'Emprende',
        currentMonthlyPriceClp: 24990,
        subscriptionStatus: 'TRIALING',
        pendingPlanCode: 'NEGOCIO',
        pendingPlanName: 'Negocio',
        pendingMonthlyPriceClp: 39990,
        checkoutUrl: 'https://checkout.example.test/pending',
        awaitingProviderVerification: true
      }
    });

    await page.goto('/app/plan');

    await expect(page.getByTestId('billing-current-plan')).toContainText('Emprende');
    await expect(page.getByTestId('billing-pending-plan')).toContainText('Negocio');
    await page.getByRole('button', { name: /Continuar checkout/ }).click();

    expect(calls.checkoutCalls()).toBe(0);
    await expect.poll(() => page.evaluate(() => window.__openedCheckoutUrls)).toEqual([
      'https://checkout.example.test/pending'
    ]);
  });

  test('does not request admin-only billing management for an operator', async ({ page }) => {
    const calls = await bootAuthenticated(page, { roles: ['OPERATOR'], usageForbidden: true });
    await page.goto('/app/plan');

    await expect(page.getByTestId('plan-name')).toHaveText('Pro');
    await expect(page.getByRole('heading', { name: 'Gestionar plan' })).toHaveCount(0);
    expect(calls.billingStatusReads()).toBe(0);
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

    await page.goto('/app/index.html');
    await expect(page.getByRole('status')).toContainText('Cargando');
    await expect.poll(() => typeof releaseSubscription).toBe('function');

    releaseSubscription();
  });

  test('keeps the screen useful when detailed usage is restricted', async ({ page }) => {
    await bootAuthenticated(page, { roles: ['OPERATOR'], usageForbidden: true });
    await page.goto('/app/index.html');

    await expect(page.getByTestId('plan-name')).toHaveText('Pro');
    await expect(page.getByTestId('usage-restricted')).toContainText('propietarios y administradores');
    await expect(page.getByTestId('metric-calls')).toContainText('—');
    await expect(page.getByTestId('metric-messages')).toContainText('—');
  });

  test('shows a recoverable subscription error without inventing billing data', async ({ page }) => {
    await bootAuthenticated(page, { subscriptionError: true });
    await page.goto('/app/index.html');

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

    await page.goto('/app/index.html');
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
  });

  test('keeps the mobile plan CTA compact and polished', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await bootAuthenticated(page);
    await page.goto('/app/index.html');

    const cta = page.getByRole('link', { name: /cambiar plan|ver planes/i });
    await expect(cta).toBeVisible();

    const appearance = await cta.evaluate(element => {
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return {
        textDecorationLine: style.textDecorationLine,
        width: rect.width
      };
    });

    expect(appearance.textDecorationLine).toBe('none');
    expect(appearance.width).toBeLessThan(180);
  });

  test('stays contained at desktop, tablet and mobile widths', async ({ page }) => {
    await bootAuthenticated(page);

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/index.html');

      await expect(page.getByTestId('plan-name')).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth
      )).toBe(true);
    }
  });
});
