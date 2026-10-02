const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

test('React Settings selects the AI voice from the backend catalog and never exposes free-text voice entry', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  let agentPuts = 0;
  page.on('request', request => {
    if (request.url().endsWith('/api/v1/ai-agent') && request.method() === 'PUT') agentPuts += 1;
  });

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN'],
    permissions: ['BUSINESS_READ', 'BUSINESS_MANAGE']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', humanTransferPhone: null
  })));
  await page.route('**/api/v1/business/profile', route => route.fulfill(json({ defaultCurrency: 'CLP' })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({ readyForCalls: true })));
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
    instructions: '',
    active: true,
    capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([])));

  await page.goto('/app/settings?section=receptionist');

  const selector = page.getByLabel('Voz');
  await expect(selector).toBeVisible();
  await expect(selector).toHaveValue('marin');
  await expect(selector.locator('option')).toHaveText(['Natural', 'Profesional', 'Amigable']);
  await expect(page.locator('input[name="agentVoice"]')).toHaveCount(0);

  await selector.selectOption('cedar');
  await expect(selector).toHaveValue('cedar');
  expect(agentPuts).toBe(0);
});
