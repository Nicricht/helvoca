const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

test('React phone provisioning never provisions until explicit confirmation', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  let provisionCalls = 0;
  let phones = [];

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
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: true,
    name: 'RecepVoz',
    language: 'es',
    voice: '',
    greeting: 'Hola, gracias por llamar.',
    instructions: '',
    active: true,
    capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
  })));
  await page.route('**/api/v1/ai-agent/voices', route => route.fulfill(json([])));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json(phones)));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: true,
    configured: true,
    purchaseAvailable: true,
    provider: 'TWILIO',
    message: 'Disponible'
  })));
  await page.route('**/api/v1/phone-numbers/provisioning/available?*', async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    expect(provisionCalls).toBe(0);
    await route.fulfill(json([{
      phoneNumber: '+12025550123',
      friendlyName: '(202) 555-0123',
      locality: 'Washington',
      region: 'DC',
      postalCode: '20001',
      isoCountry: 'US',
      addressRequirements: 'none',
      voiceCapable: true
    }]));
  });
  await page.route('**/api/v1/phone-numbers/provisioning', async route => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    expect(route.request().postDataJSON()).toEqual({ phoneNumber: '+12025550123', confirmed: true });
    provisionCalls += 1;
    phones = [{
      id: 'phone1',
      provider: 'TWILIO',
      externalId: 'PNdemo',
      phoneNumber: '+12025550123',
      active: true,
      whatsappEnabled: false
    }];
    await route.fulfill(json(phones[0]));
  });
  await page.route('**/api/v1/channels/whatsapp/meta/config', route => route.fulfill(json({
    status: 'NOT_CONFIGURED', configured: false, enabled: false
  })));
  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap', route => route.fulfill(json({
    enabled: true, available: false
  })));

  await page.goto('/app/settings?section=channels');

  await expect(page.getByRole('heading', { name: 'Telefonía' })).toBeVisible();
  expect(provisionCalls).toBe(0);

  await page.getByLabel('País ISO para búsqueda').fill('US');
  await page.getByLabel('Código de área para búsqueda').fill('202');
  await page.getByRole('button', { name: 'Buscar números' }).click();

  await expect(page.getByRole('button', { name: 'Aprovisionar' })).toBeVisible();
  expect(provisionCalls).toBe(0);

  page.once('dialog', async dialog => {
    expect(dialog.message()).toContain('Puede generar cargos');
    await dialog.accept();
  });
  await page.getByRole('button', { name: 'Aprovisionar' }).click();

  await expect.poll(() => provisionCalls).toBe(1);
  await expect(page.locator('section[aria-label="Telefonía"] [role="status"]')).toContainText('quedó conectado');
});
