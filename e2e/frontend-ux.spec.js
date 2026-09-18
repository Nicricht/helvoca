const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockReadyTenant(page) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({ email: 'admin@demo.cl' })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', humanTransferPhone: null
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: true,
    humanTransferConfigured: false,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'OPTIONAL_HUMAN_TRANSFER'
  })));
  await page.route('**/api/v1/services', route => route.fulfill(json([
    { id: 'svc1', name: 'Peluquería', durationMinutes: 30, price: 25000, active: true }
  ])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([
    { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' },
    { dayOfWeek: 2, openTime: '09:00:00', closeTime: '18:00:00' }
  ])));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([
    { id: 'kb1', title: 'Agenda online', category: 'Información', content: 'Sí', active: true }
  ])));
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: true,
    name: 'Helvoca',
    language: 'es',
    voice: null,
    greeting: 'Hola, gracias por llamar.',
    instructions: null,
    active: true,
    capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES', 'CREATE_BOOKING']
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([
    { id: 'phone1', provider: 'TWILIO', externalId: 'PNdemo', phoneNumber: '+56911111111', active: true }
  ])));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false,
    configured: false,
    purchaseAvailable: false,
    provider: 'TWILIO',
    message: 'No disponible en E2E'
  })));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    provider: 'mercadopago',
    billingEnabled: true,
    checkoutConfigured: true,
    currentPlanCode: 'PRO',
    currentPlanName: 'Pro',
    currentMonthlyPriceClp: 69990,
    subscriptionStatus: 'ACTIVE',
    pendingPlanCode: null,
    pendingPlanName: null,
    pendingMonthlyPriceClp: null,
    checkoutUrl: null,
    awaitingProviderVerification: false
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    businessId: '11111111-1111-1111-1111-111111111111',
    plan: 'PRO',
    status: 'ACTIVE',
    serviceAllowed: true,
    maxConcurrentCalls: 10,
    includedMinutes: 500,
    usedMinutes: 23,
    overageMinutes: 0,
    currentPeriodStart: '2026-09-01T00:00:00Z',
    currentPeriodEnd: '2026-10-01T00:00:00Z',
    graceUntil: null,
    billingProviderConnected: true,
    legacyFallback: false
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([
    { code: 'PRO', name: 'Pro', monthlyPriceClp: 69990, includedMinutes: 500, maxConcurrentCalls: 10, overagePerMinuteClp: 109, customPricing: false, recommended: false },
    { code: 'ENTERPRISE', name: 'Enterprise', monthlyPriceClp: 119990, includedMinutes: 1000, maxConcurrentCalls: 10, overagePerMinuteClp: null, customPricing: true, recommended: false }
  ])));
}

test('ready customer sees an operational dashboard with progressive disclosure', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyTenant(page);

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Helvoca está atendiendo 🟢');
  await expect(page.getByText('Prepara tu negocio en minutos')).toHaveCount(0);
  await expect(page.locator('#readyBanner')).toBeHidden();
  await expect(page.locator('#nextStepBanner')).toBeHidden();

  await expect(page.locator('#advancedPanel')).toBeHidden();
  await expect(page.locator('#commercialStatusCard')).toBeHidden();
  await expect(page.locator('.nav-config')).toHaveAttribute('href', '/settings.html');
  await expect(page.locator('#homeBusinessWorkspace')).toBeVisible();
});
