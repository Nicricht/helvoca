const { test, expect } = require('@playwright/test');

test('self-service phone search stays hidden until chosen and never provisions until explicit confirmation', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  let provisionCalls = 0;
  let phones = [];
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
    phoneConfigured: phones.some(phone => phone.active),
    readyForCalls: phones.some(phone => phone.active),
    nextStep: phones.length ? 'OPTIONAL_HUMAN_TRANSFER' : 'CONNECT_PHONE_NUMBER'
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
    name: 'RecepVoz',
    language: 'es',
    voice: null,
    greeting: 'Hola, gracias por llamar.',
    instructions: null,
    active: true,
    capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
  })));
  await page.route('**/api/v1/ai-agent/voices', route => route.fulfill(json([])));
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
    await route.fulfill(json([
      {
        phoneNumber: '+12025550123',
        friendlyName: '(202) 555-0123',
        locality: 'Washington',
        region: 'DC',
        postalCode: '20001',
        isoCountry: 'US',
        addressRequirements: 'none',
        voiceCapable: true
      }
    ]));
  });
  await page.route('**/api/v1/phone-numbers/provisioning', async route => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    expect(route.request().postDataJSON()).toEqual({ phoneNumber: '+12025550123', confirmed: true });
    provisionCalls += 1;
    phones = [{ id: 'phone1', provider: 'TWILIO', externalId: 'PNdemo', phoneNumber: '+12025550123', active: true }];
    await route.fulfill(json(phones[0]));
  });
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json(phones)));

  await page.goto('/');
  await expect(page.locator('#dashboardView')).toBeVisible();
  await expect(page.locator('#advancedPanel')).toBeVisible();

  await page.getByRole('button', { name: 'Teléfono' }).click();
  await expect(page.getByRole('button', { name: 'Conectar mi número' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Buscar un número nuevo' })).toBeVisible();
  await expect(page.locator('#provisioningSearchForm')).toBeHidden();
  expect(provisionCalls).toBe(0);

  await page.getByRole('button', { name: 'Buscar un número nuevo' }).click();
  await expect(page.locator('#provisioningSearchForm')).toBeVisible();
  await expect(page.locator('#provisioningStatus')).toHaveText('APROVISIONAMIENTO DISPONIBLE');

  await page.locator('#provisioningSearchForm [name=country]').fill('US');
  await page.locator('#provisioningSearchForm [name=areaCode]').fill('202');
  await page.getByRole('button', { name: 'Buscar números disponibles' }).click();

  await expect(page.getByText('+12025550123')).toBeVisible();
  expect(provisionCalls).toBe(0);

  page.once('dialog', async dialog => {
    expect(dialog.message()).toContain('puede aplicar cargos');
    await dialog.accept();
  });
  await page.getByRole('button', { name: 'Aprovisionar' }).click();

  await expect.poll(() => provisionCalls).toBe(1);
  await expect(page.locator('#provisioningMessage')).toContainText('quedó conectado y activo');
  await expect(page.locator('#phoneList')).toContainText('+12025550123');
});