const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

test('AI agent voice is selected from the backend catalog and never typed freely', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  let agentPuts = 0;
  page.on('request', request => {
    if (request.url().endsWith('/api/v1/ai-agent') && request.method() === 'PUT') agentPuts += 1;
  });

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
  await page.route('**/api/v1/ai-agent/voices', route => route.fulfill(json([
    { code: 'natural', selection: 'marin', name: 'Natural', description: 'Equilibrada y conversacional' },
    { code: 'professional', selection: 'cedar', name: 'Profesional', description: 'Clara y orientada a atención' },
    { code: 'friendly', selection: 'coral', name: 'Amigable', description: 'Cercana y cordial' }
  ])));
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: true,
    name: 'Helvoca',
    language: 'es',
    voice: 'marin',
    greeting: 'Hola, gracias por llamar.',
    instructions: null,
    active: true,
    capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([
    { id: 'phone1', provider: 'TWILIO', externalId: 'PNdemo', phoneNumber: '+12025550123', active: true }
  ])));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false, configured: false, purchaseAvailable: false, provider: 'TWILIO', message: 'No disponible en E2E'
  })));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    provider: null,
    billingEnabled: false,
    checkoutConfigured: false,
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
  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    businessId: '11111111-1111-1111-1111-111111111111',
    plan: 'BASIC',
    status: 'TRIALING',
    serviceAllowed: true,
    maxConcurrentCalls: 1,
    includedMinutes: 100,
    usedMinutes: 0,
    overageMinutes: 0,
    currentPeriodStart: '2026-09-01T00:00:00Z',
    currentPeriodEnd: '2026-09-15T00:00:00Z',
    graceUntil: null,
    billingProviderConnected: false,
    legacyFallback: false
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));

  await page.goto('/');
  await page.locator('#advancedToggleBtn').click();

  const selector = page.locator('#agentVoiceSelect');
  await expect(selector).toBeVisible();
  await expect(selector).toHaveValue('marin');
  await expect(page.locator('#agentVoiceHelp')).toContainText('Equilibrada y conversacional');
  await expect(page.locator('input[name="agentVoice"]')).toHaveCount(0);

  await selector.selectOption('cedar');
  await expect(selector).toHaveValue('cedar');
  await expect(page.locator('#agentVoiceHelp')).toContainText('Profesional');
  expect(agentPuts).toBe(0);
});
