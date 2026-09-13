const { test, expect } = require('@playwright/test');

test('commercial status dashboard is read-only and shows pending activation state', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  let billingPosts = 0;
  page.on('request', request => {
    if (request.url().includes('/api/v1/billing/') && request.method() !== 'GET') billingPosts += 1;
  });

  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

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
  await page.route('**/api/v1/subscription', async route => {
    expect(route.request().method()).toBe('GET');
    await route.fulfill(json({
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
    }));
  });

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
  expect(billingPosts).toBe(0);
});
