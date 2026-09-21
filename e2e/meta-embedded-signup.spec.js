const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('Embedded Signup renders WABA candidates after secure authorization', async ({ page }) => {
  const authorizationBodies = [];

  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'e2e-token');
    window.FB = {
      init() {},
      login(callback) {
        callback({ authResponse: { code: 'meta-code-e2e' } });
      }
    };
  });

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap', route =>
    route.fulfill(json({
      enabled: true,
      available: true,
      appId: '123456789',
      configId: '987654321',
      graphApiVersion: 'v99.0'
    }))
  );

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
          messageTemplateNamespace: 'namespace-one',
          systemUserAssigned: true
        },
        {
          id: '1906385232743452',
          name: '<img src=x onerror=alert(1)>',
          currency: 'USD',
          timezoneId: 'America/New_York',
          messageTemplateNamespace: 'namespace-two',
          systemUserAssigned: false
        }
      ],
      wabaAfterCursor: 'next-page'
    }));
  });

  await page.route('**/api/v1/auth/me', route =>
    route.fulfill(json({ email: 'admin@demo.cl' }))
  );
  await page.route('**/api/v1/phone-numbers/provisioning/status', route =>
    route.fulfill(json({
      enabled: false,
      configured: false,
      purchaseAvailable: false,
      provider: 'TWILIO',
      message: 'No disponible en E2E'
    }))
  );
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([])));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({})));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({})));
  await page.route('**/api/v1/business/profile', route => route.fulfill(json({})));
  await page.route(/\/api\/v1\/business$/, route => route.fulfill(json({
    name: 'Negocio E2E',
    timezone: 'America/Santiago',
    language: 'es'
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: true,
    humanTransferConfigured: true,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'READY'
  })));
  await page.route('**/api/v1/services', route => route.fulfill(json([])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([])));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([])));
  await page.route(/\/api\/v1\/ai-agent(?:\/voices)?$/, route => route.fulfill(json([])));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({})));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([])));

  await page.goto('/settings.html');

  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();

  const connect = page.getByRole('button', { name: 'Conectar WhatsApp', exact: true });
  await expect(connect).toBeVisible();
  await connect.click();

  const continueButton = page.getByRole('button', { name: 'Continuar con Meta', exact: true });
  await expect(continueButton).toBeVisible();
  await continueButton.click();

  await expect.poll(() => authorizationBodies).toEqual([{ code: 'meta-code-e2e' }]);

  await expect(page.locator('#metaWhatsAppConnectMessage'))
    .toHaveText('Autorización completada. Encontramos 2 cuentas de WhatsApp Business.');

  const candidates = page.locator('#metaWhatsAppWabaCandidates');
  await expect(candidates).toBeVisible();
  await expect(candidates.locator('.meta-whatsapp-waba-card')).toHaveCount(2);
  await expect(candidates).toContainText('RecepVoz Peluquería');
  await expect(candidates).toContainText('ID 1906385232743451 · CLP · America/Santiago');
  await expect(candidates).toContainText('Acceso técnico de RecepVoz listo');
  await expect(candidates).toContainText('<img src=x onerror=alert(1)>');
  await expect(candidates.locator('img')).toHaveCount(0);
  await expect(candidates).toContainText('Acceso técnico pendiente de asignación');
  await expect(candidates).toContainText('Meta indica que existen más cuentas. Esta vista muestra la primera página.');
});
