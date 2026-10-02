const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('React Embedded Signup renders WABA candidates after secure authorization', async ({ page }) => {
  const authorizationBodies = [];
  const phoneDiscoveryBodies = [];
  const unexpectedEmbeddedSignupRequests = [];

  page.on('request', request => {
    const pathname = new URL(request.url()).pathname;
    const prefix = '/api/v1/channels/whatsapp/meta/embedded-signup/';
    const allowed = new Set([
      prefix + 'bootstrap',
      prefix + 'authorization-code',
      prefix + 'waba/phone-numbers',
      prefix + 'waba/phone-number/validate',
      prefix + 'waba/phone-number/finalize'
    ]);
    if (pathname.startsWith(prefix) && !allowed.has(pathname)) unexpectedEmbeddedSignupRequests.push(pathname);
  });

  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'e2e-token');
    window.FB = {
      init() {},
      login(callback) {
        callback({ authResponse: { code: 'meta-code-e2e' } });
      }
    };
  });

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN'],
    permissions: ['BUSINESS_READ', 'BUSINESS_MANAGE']
  })));
  await page.route(/\/api\/v1\/business$/, route => route.fulfill(json({
    name: 'Negocio E2E',
    timezone: 'America/Santiago',
    language: 'es'
  })));
  await page.route('**/api/v1/business/profile', route => route.fulfill(json({ defaultCurrency: 'CLP' })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({ readyForCalls: true })));
  await page.route('**/api/v1/services', route => route.fulfill(json([])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([])));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([])));
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: true,
    name: 'RecepVoz',
    language: 'es',
    voice: '',
    greeting: 'Hola',
    instructions: '',
    active: true,
    capabilities: []
  })));
  await page.route('**/api/v1/ai-agent/voices', route => route.fulfill(json([])));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([])));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false,
    configured: false,
    purchaseAvailable: false,
    provider: 'TWILIO',
    message: 'No disponible'
  })));
  await page.route('**/api/v1/channels/whatsapp/meta/config', route => route.fulfill(json({
    status: 'NOT_CONFIGURED',
    configured: false,
    enabled: false
  })));
  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap', route => route.fulfill(json({
    enabled: true,
    available: true,
    appId: '123456789',
    configId: '987654321',
    graphApiVersion: 'v26.0'
  })));
  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/authorization-code', async route => {
    authorizationBodies.push(route.request().postDataJSON());
    await route.fulfill(json({
      state: 'AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED',
      accepted: true,
      retained: false,
      exchangePending: false,
      wabas: [
        {
          id: '1906385232743451',
          name: 'RecepVoz Peluquería',
          currency: 'CLP',
          timezoneId: 'America/Santiago',
          systemUserAssigned: true
        },
        {
          id: '1906385232743452',
          name: '<img src=x onerror=alert(1)>',
          currency: 'USD',
          timezoneId: 'America/New_York',
          systemUserAssigned: false
        }
      ]
    }));
  });
  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-numbers', async route => {
    phoneDiscoveryBodies.push(route.request().postDataJSON());
    await route.fulfill(json({
      state: 'PHONE_NUMBERS_DISCOVERED',
      appSubscribed: true,
      phoneNumbers: [{
        id: '12025550124',
        displayPhoneNumber: '+56 9 3333 4444',
        verifiedName: 'RecepVoz Sucursal',
        qualityRating: 'YELLOW',
        codeVerificationStatus: 'NOT_VERIFIED'
      }]
    }));
  });

  await page.goto('/app/settings?section=channels');

  await page.getByRole('button', { name: 'Conectar WhatsApp' }).click();
  await page.getByRole('button', { name: 'Continuar con Meta' }).click();

  await expect.poll(() => authorizationBodies).toEqual([{ code: 'meta-code-e2e' }]);
  await expect(page.getByText('RecepVoz Peluquería')).toBeVisible();
  await expect(page.getByText('<img src=x onerror=alert(1)>')).toBeVisible();
  await expect(page.locator('img[src="x"]')).toHaveCount(0);

  await page.getByRole('button', { name: /<img src=x onerror=alert\(1\)>/ }).click();
  await page.getByRole('button', { name: 'Continuar con esta cuenta' }).click();

  await expect.poll(() => phoneDiscoveryBodies).toEqual([{ wabaId: '1906385232743452' }]);
  await expect(page.getByText('+56 9 3333 4444')).toBeVisible();
  expect(unexpectedEmbeddedSignupRequests).toEqual([]);
});
