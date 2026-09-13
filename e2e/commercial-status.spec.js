const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockBaseDashboard(page) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({ email: 'admin@demo.cl' })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', humanTransferPhone: null
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: false,
    humanTransferConfigured: false,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'OPTIONAL_HUMAN_TRANSFER'
  })));
  await page.route('**/api/v1/services', route => route.fulfill(json([
    { id: 'svc1', name: 'Consulta', durationMinutes: 30, price: 25000, active: true }
  ])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([
    { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
  ])));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([])));
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: true,
    name: 'Helvoca',
    language: 'es',
    voice: null,
    greeting: 'Hola, gracias por llamar.',
    instructions: null,
    active: true,
    capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([
    { id: 'phone1', provider: 'TWILIO', externalId: 'PNdemo', phoneNumber: '+12025550123', active: true }
  ])));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false,
    configured: false,
    purchaseAvailable: false,
    provider: 'TWILIO',
    message: 'No disponible en E2E'
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([
    { code: 'EMPRENDE', name: 'Emprende', monthlyPriceClp: 24990, includedMinutes: 100, maxConcurrentCalls: 1, overagePerMinuteClp: 249, customPricing: false, recommended: false },
    { code: 'NEGOCIO', name: 'Negocio', monthlyPriceClp: 49990, includedMinutes: 300, maxConcurrentCalls: 3, overagePerMinuteClp: 199, customPricing: false, recommended: true },
    { code: 'PRO', name: 'Pro', monthlyPriceClp: 99990, includedMinutes: 600, maxConcurrentCalls: 10, overagePerMinuteClp: 169, customPricing: false, recommended: false },
    { code: 'ENTERPRISE', name: 'Enterprise', monthlyPriceClp: 199990, includedMinutes: 600, maxConcurrentCalls: 10, overagePerMinuteClp: null, customPricing: true, recommended: false }
  ])));
}

function activeSubscription() {
  return {
    businessId: '11111111-1111-1111-1111-111111111111',
    plan: 'BASIC',
    status: 'TRIALING',
    serviceAllowed: true,
    maxConcurrentCalls: 1,
    includedMinutes: 100,
    usedMinutes: 37,
    overageMinutes: 0,
    currentPeriodStart: '2026-09-01T00:00:00Z',
    currentPeriodEnd: '2026-09-15T00:00:00Z',
    graceUntil: null,
    billingProviderConnected: true,
    legacyFallback: false
  };
}

test('commercial dashboard shows confirmed and pending state without starting checkout on load', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  let billingPosts = 0;
  page.on('request', request => {
    if (request.url().includes('/api/v1/billing/') && request.method() !== 'GET') billingPosts += 1;
  });

  await mockBaseDashboard(page);
  await page.route('**/api/v1/billing/status', async route => {
    expect(route.request().method()).toBe('GET');
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill(json({
      provider: 'mercadopago',
      billingEnabled: true,
      checkoutConfigured: true,
      currentPlanCode: 'EMPRENDE',
      currentPlanName: 'Emprende',
      currentMonthlyPriceClp: 24990,
      subscriptionStatus: 'TRIALING',
      pendingPlanCode: 'NEGOCIO',
      pendingPlanName: 'Negocio',
      pendingMonthlyPriceClp: 49990,
      checkoutUrl: 'https://checkout.example.test/pending',
      awaitingProviderVerification: true
    }));
  });
  await page.route('**/api/v1/subscription', route => route.fulfill(json(activeSubscription())));

  await page.goto('/');

  const card = page.locator('#commercialStatusCard');
  await expect(card).toBeVisible();
  await expect(card.locator('#commercialPlan')).toHaveText('Emprende');
  await expect(card.locator('#commercialSubscriptionStatus')).toHaveText('TRIALING');
  await expect(card.locator('#commercialMinutes')).toHaveText('37 usados · 63 restantes');
  await expect(card.locator('#commercialOverage')).toHaveText('0 min');
  await expect(card.locator('#commercialStateBadge')).toHaveText('SERVICIO HABILITADO');
  await expect(card).toContainText('Plan pendiente: Negocio');
  await expect(card).toContainText('hasta que el backend reciba y verifique un cobro aprobado');
  await expect(card.getByRole('button', { name: 'Continuar checkout' })).toBeVisible();
  await expect(card.getByRole('button', { name: 'Cotización personalizada' })).toBeDisabled();
  expect(billingPosts).toBe(0);
});

test('plan checkout starts only after explicit confirmation and does not activate the plan locally', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'e2e-token');
    window.__openedCheckoutUrls = [];
    window.open = url => {
      window.__openedCheckoutUrls.push(url);
      return { closed: false };
    };
  });

  await mockBaseDashboard(page);
  let checkoutPosts = 0;
  let checkoutPayload = null;

  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    provider: null,
    billingEnabled: true,
    checkoutConfigured: true,
    currentPlanCode: 'EMPRENDE',
    currentPlanName: 'Emprende',
    currentMonthlyPriceClp: 24990,
    subscriptionStatus: 'TRIALING',
    pendingPlanCode: null,
    pendingPlanName: null,
    pendingMonthlyPriceClp: null,
    checkoutUrl: null,
    awaitingProviderVerification: false
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json(activeSubscription())));
  await page.route('**/api/v1/billing/checkout', async route => {
    checkoutPosts += 1;
    checkoutPayload = route.request().postDataJSON();
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill(json({
      subscriptionId: 'pre-e2e-1',
      checkoutUrl: 'https://checkout.example.test/pre-e2e-1',
      planCode: 'NEGOCIO',
      planName: 'Negocio',
      monthlyPriceClp: 49990,
      reused: false
    }));
  });

  await page.goto('/');

  const card = page.locator('#commercialStatusCard');
  await expect(card).toBeVisible();
  await expect(card.locator('#commercialPlan')).toHaveText('Emprende');
  expect(checkoutPosts).toBe(0);

  page.once('dialog', async dialog => {
    expect(dialog.message()).toContain('Helvoca no activará el plan hasta verificar el pago');
    await dialog.accept();
  });
  await card.getByRole('button', { name: 'Elegir Negocio' }).click();

  await expect.poll(() => checkoutPosts).toBe(1);
  expect(checkoutPayload).toEqual({ plan: 'NEGOCIO' });
  await expect(card.locator('#commercialPlan')).toHaveText('Emprende');
  await expect(card).toContainText('Plan pendiente: Negocio');
  await expect(card.locator('#commercialPlanMessage')).toContainText('Checkout creado para Negocio');
  await expect.poll(() => page.evaluate(() => window.__openedCheckoutUrls)).toEqual([
    'https://checkout.example.test/pre-e2e-1'
  ]);
});
