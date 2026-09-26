const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockReadyTenant(page, options = {}) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', humanTransferPhone: null
  })));
  await page.route('**/api/v1/onboarding/guide', route => route.fulfill(json(
    options.activationGuide || {
      readyForPilot: true,
      completed: 7,
      total: 7,
      progressPercent: 100,
      nextStep: null,
      steps: [
        { code: 'ACCOUNT', label: 'Cuenta y administrador', complete: true, detail: 'OK' },
        { code: 'BUSINESS_SETUP', label: 'Datos, oferta y horarios', complete: true, detail: 'OK' },
        { code: 'PHONE', label: 'Teléfono y voz', complete: true, detail: 'OK' },
        { code: 'WHATSAPP', label: 'WhatsApp', complete: true, detail: 'OK' },
        { code: 'COMMERCIAL', label: 'Catálogo, venta y pagos', complete: true, detail: 'OK' },
        { code: 'ACTIVATION', label: 'Validación del negocio', complete: true, detail: 'OK' },
        { code: 'PILOT', label: 'Control del piloto', complete: true, detail: 'OK' }
      ]
    }
  )));
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
  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap', route => route.fulfill(json({
    enabled: true,
    available: true,
    appId: '123456789',
    configId: '987654321',
    graphApiVersion: 'v26.0'
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

  const teamState = options.teamState || {
    users: [{ id: 'user-admin', name: 'Administrador Inicial', email: 'admin@demo.cl', active: true, roles: ['BUSINESS_ADMIN'] }],
    invitations: []
  };
  await page.route('**/api/v1/admin/users', route => route.fulfill(json(teamState.users)));
  await page.route('**/api/v1/admin/invitations', async route => {
    const request = route.request();
    if (request.method() === 'POST') {
      const payload = request.postDataJSON();
      const invitation = {
        id: `invite-${teamState.invitations.length + 1}`,
        businessId: '11111111-1111-1111-1111-111111111111',
        businessName: 'Negocio E2E',
        name: payload.name,
        email: payload.email,
        role: payload.role,
        expiresAt: '2026-09-29T12:00:00Z',
        status: 'PENDING',
        invitePath: '/invite.html?businessId=11111111-1111-1111-1111-111111111111&token=e2e-invite-token'
      };
      teamState.invitations.unshift(invitation);
      await route.fulfill({ status: 201, ...json(invitation) });
      return;
    }
    await route.fulfill(json(teamState.invitations));
  });
}

test('auth tabs and simplified registration controls are usable', async ({ page }) => {
  await page.goto('/');

  await expect(page.locator('#registerForm')).toBeVisible();
  await expect(page.locator('#loginForm')).toBeHidden();
  await expect(page.locator('#registerForm input')).toHaveCount(3);
  await expect(page.locator('#registerForm [name="businessName"]')).toBeVisible();
  await expect(page.locator('#registerForm [name="email"]')).toBeVisible();
  await expect(page.locator('#registerForm [name="password"]')).toBeVisible();
  await expect(page.locator('#registerForm [name="adminName"]')).toHaveCount(0);
  await expect(page.locator('#registerForm [name="sourceUrl"]')).toHaveCount(0);
  await expect(page.locator('#registerForm button[type="submit"]')).toHaveText('Crear cuenta');

  await page.locator('#loginTab').click();
  await expect(page.locator('#loginForm')).toBeVisible();
  await expect(page.locator('#registerForm')).toBeHidden();

  await page.locator('#registerTab').click();
  await expect(page.locator('#registerForm')).toBeVisible();
  await expect(page.locator('#loginForm')).toBeHidden();
});

test('registration validates fields and enters the dashboard with the expected payload', async ({ page }) => {
  await mockReadyTenant(page, {
    activationGuide: {
      readyForPilot: false,
      completed: 1,
      total: 7,
      progressPercent: 14,
      nextStep: {
        code: 'BUSINESS_SETUP',
        label: 'Datos, oferta y horarios',
        complete: false,
        detail: 'Completa o importa los datos públicos, servicios y horarios que correspondan.',
        actionLabel: 'Preparar negocio',
        actionHref: '/#aiForm'
      },
      steps: [
        { code: 'ACCOUNT', label: 'Cuenta y administrador', complete: true, detail: 'Tu negocio y su administrador ya existen.' },
        { code: 'BUSINESS_SETUP', label: 'Datos, oferta y horarios', complete: false, detail: 'Pendiente', actionLabel: 'Preparar negocio', actionHref: '/#aiForm' },
        { code: 'PHONE', label: 'Teléfono y voz', complete: false, detail: 'Pendiente' },
        { code: 'WHATSAPP', label: 'WhatsApp', complete: false, detail: 'Pendiente' },
        { code: 'COMMERCIAL', label: 'Catálogo, venta y pagos', complete: false, detail: 'Pendiente' },
        { code: 'ACTIVATION', label: 'Validación del negocio', complete: false, detail: 'Pendiente' },
        { code: 'PILOT', label: 'Control del piloto', complete: false, detail: 'Pendiente' }
      ]
    }
  });

  let registerCalls = 0;
  let registerPayload = null;
  await page.route('**/api/v1/auth/register', async route => {
    registerCalls += 1;
    registerPayload = route.request().postDataJSON();
    await route.fulfill(json({ accessToken: 'register-token' }));
  });

  await page.goto('/');

  await page.locator('#registerForm [name="businessName"]').fill('Negocio QA');
  await page.locator('#registerForm [name="email"]').fill('qa@example.cl');
  await page.locator('#registerForm [name="password"]').fill('corta');
  await page.locator('#registerForm button[type="submit"]').click();
  expect(registerCalls).toBe(0);
  expect(await page.locator('#registerForm [name="password"]').evaluate(input => input.validity.valid)).toBe(false);

  await page.locator('#registerForm [name="password"]').fill('clave-segura-123');
  await page.locator('#registerForm button[type="submit"]').click();

  await expect.poll(() => registerCalls).toBe(1);
  expect(registerPayload.businessName).toBe('Negocio QA');
  expect(registerPayload.adminName).toBe('Negocio QA');
  expect(registerPayload.email).toBe('qa@example.cl');
  expect(registerPayload.password).toBe('clave-segura-123');
  expect(registerPayload.humanTransferPhone).toBeNull();
  expect(registerPayload.timezone).toBeTruthy();
  expect(registerPayload.language).toBeTruthy();

  await expect(page.locator('#dashboardView')).toBeVisible();
  await expect(page.locator('#authView')).toBeHidden();
  await expect(page.locator('#sessionBadge')).toHaveText('Sesión activa');
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBe('register-token');
  await expect(page.locator('#businessActivationGuide')).toBeVisible();
  await expect(page.locator('#activationGuideScore')).toHaveText('1/7');
  await expect(page.locator('#businessActivationGuide [data-code="ACCOUNT"]')).toHaveClass(/complete/);
  await expect(page.locator('#businessActivationGuide [data-code="BUSINESS_SETUP"]')).toHaveClass(/next/);
  await expect(page.locator('#activationGuideNext')).toContainText('Siguiente: Datos, oferta y horarios');
  await expect(page.locator('#activationGuideNext a')).toHaveText('Preparar negocio');
  await expect(page.locator('#activationGuideNext a')).toHaveAttribute('href', '/#aiForm');
  await expect(page.locator('#businessActivationGuide')).not.toContainText('Railway');
  await expect(page.locator('#businessActivationGuide')).not.toContainText('webhook');
});

test('login keeps errors visible and enters the dashboard after valid credentials', async ({ page }) => {
  await mockReadyTenant(page);

  let loginCalls = 0;
  await page.route('**/api/v1/auth/login', async route => {
    loginCalls += 1;
    if (loginCalls === 1) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Credenciales inválidas.' })
      });
      return;
    }
    await route.fulfill(json({ accessToken: 'login-token' }));
  });

  await page.goto('/');
  await page.locator('#loginTab').click();
  await page.locator('#loginForm [name="email"]').fill('qa@example.cl');
  await page.locator('#loginForm [name="password"]').fill('incorrecta');
  await page.locator('#loginForm button[type="submit"]').click();

  await expect(page.locator('#authMessage')).toBeVisible();
  await expect(page.locator('#authMessage')).toContainText('Credenciales inválidas');
  await expect(page.locator('#loginForm')).toBeVisible();
  await expect(page.locator('#dashboardView')).toBeHidden();
  expect(loginCalls).toBe(1);

  await page.locator('#loginForm [name="password"]').fill('clave-segura-123');
  await page.locator('#loginForm button[type="submit"]').click();

  await expect.poll(() => loginCalls).toBe(2);
  await expect(page.locator('#dashboardView')).toBeVisible();
  await expect(page.locator('#authView')).toBeHidden();
  await expect(page.locator('#sessionBadge')).toHaveText('Sesión activa');
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBe('login-token');
});

test('ready customer sees operations on home and configuration on settings', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyTenant(page);
  await page.route('https://connect.facebook.net/**/sdk.js', route => route.fulfill({
    status: 200,
    contentType: 'application/javascript',
    body: 'window.FB={init:(options)=>{window.__fbInitOptions=options;},login:(callback,options)=>{window.__fbLoginOptions=options;callback({authResponse:{code:"temporary-code-for-e2e"}});}}; if(window.fbAsyncInit) window.fbAsyncInit();'
  }));
  let embeddedSignupCodeHandoff = null;
  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/authorization-code', async route => {
    embeddedSignupCodeHandoff = route.request().postDataJSON();
    await route.fulfill(json({
      state: 'AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED',
      accepted: true,
      retained: false,
      exchangePending: false,
      wabas: [{
        id: '1906385232743451',
        name: 'Negocio E2E WhatsApp',
        currency: 'CLP',
        timezoneId: 'America/Santiago',
        messageTemplateNamespace: 'e2e',
        systemUserAssigned: true
      }],
      wabaAfterCursor: null
    }));
  });

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Negocio E2E está atendiendo 🟢');
  await expect(page.locator('#readyBanner')).toBeHidden();
  await expect(page.locator('#nextStepBanner')).toBeHidden();
  await expect(page.locator('#advancedPanel')).toBeHidden();
  await expect(page.locator('#commercialStatusCard')).toBeHidden();
  await expect(page.locator('.nav-config')).toHaveAttribute('href', '/settings.html');

  await page.goto('/settings.html');

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Mi negocio');
  await expect(page.locator('#advancedPanel')).toBeVisible();
  await expect(page.locator('#statusGrid')).toBeHidden();
  await expect(page.locator('.nav-config')).toHaveClass(/active/);
  await expect(page.getByRole('button', { name: '🏪 Negocio', exact: true })).toBeVisible();
  await expect(page.locator('#configBusinessPanel')).toBeVisible();

  await expect(page.locator('#commercialStatusCard')).toBeVisible();
  await expect(page.locator('#commercialPlans')).toBeHidden();
  await expect(page.getByText('Pro · 477 min')).toBeVisible();
  await page.getByRole('button', { name: 'Gestionar', exact: true }).click();
  await expect(page.locator('#commercialPlans')).toBeVisible();

  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();
  await expect(page.locator('#phoneCompactSummary').getByText('+56911111111')).toBeVisible();
  await page.getByRole('button', { name: 'Cambiar' }).click();
  await expect(page.getByRole('button', { name: 'Conectar mi número' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Buscar un número nuevo' })).toBeVisible();
  await expect(page.locator('#provisioningSearchForm')).toBeHidden();
  await expect(page.getByRole('button', { name: 'Conectar WhatsApp', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Conectar WhatsApp', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Continuar con Meta', exact: true })).toBeVisible();
  await expect(page.locator('#metaWhatsAppConnectMessage')).toContainText('SDK de Meta preparado');
  expect(await page.evaluate(() => window.__fbInitOptions)).toEqual({
    appId: '123456789',
    xfbml: false,
    version: 'v26.0'
  });

  await page.getByRole('button', { name: 'Continuar con Meta', exact: true }).click();
  expect(await page.evaluate(() => window.__fbLoginOptions)).toEqual({
    config_id: '987654321',
    auth_type: 'rerequest',
    response_type: 'code',
    override_default_response_type: true,
    extras: { setup: {} }
  });
  await expect.poll(() => embeddedSignupCodeHandoff).toEqual({ code: 'temporary-code-for-e2e' });
  await expect(page.locator('#metaWhatsAppConnectMessage'))
    .toHaveText('Autorización completada. Encontramos 1 cuenta de WhatsApp Business.');
  await expect(page.locator('#metaWhatsAppWabaCandidates')).toContainText('Negocio E2E WhatsApp');
  await expect(page.locator('#metaWhatsAppConnectMessage')).not.toContainText('temporary-code-for-e2e');
});

test('assisted onboarding hands the same business to an invited administrator', async ({ page }) => {
  const teamState = {
    users: [{ id: 'user-admin', name: 'Administrador Inicial', email: 'admin@demo.cl', active: true, roles: ['BUSINESS_ADMIN'] }],
    invitations: []
  };
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyTenant(page, { teamState });

  await page.route('**/api/v1/auth/invitations/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'POST' && url.pathname.endsWith('/accept')) {
      const invitation = teamState.invitations[0];
      invitation.status = 'ACCEPTED';
      teamState.users.push({
        id: 'user-owner',
        name: invitation.name,
        email: invitation.email,
        active: true,
        roles: [invitation.role]
      });
      await route.fulfill(json({
        accessToken: 'owner-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: {
          id: 'user-owner',
          businessId: invitation.businessId,
          name: invitation.name,
          email: invitation.email,
          roles: [invitation.role]
        }
      }));
      return;
    }

    const invitation = teamState.invitations[0];
    await route.fulfill(json({
      ...invitation,
      invitePath: null
    }));
  });

  await page.goto('/settings.html');

  const teamCard = page.locator('#teamInvitationsCard');
  await expect(teamCard).toBeVisible();
  await expect(page.locator('#teamMembersList')).toContainText('Administrador Inicial');
  await expect(page.locator('#teamMembersList')).toContainText('admin@demo.cl');

  await page.locator('#teamInviteForm [name="name"]').fill('Dueña Negocio');
  await page.locator('#teamInviteForm [name="email"]').fill('duena@negocio.cl');
  await page.locator('#teamInviteForm [name="role"]').selectOption('BUSINESS_ADMIN');
  await page.locator('#teamInviteForm button[type="submit"]').click();

  await expect(page.locator('#teamInviteUrl')).toHaveValue(/invite\.html\?businessId=.*token=e2e-invite-token/);
  await expect(page.locator('#teamInviteList')).toContainText('Dueña Negocio');
  await expect(page.locator('#teamInviteList')).toContainText('Pendiente');

  const inviteUrl = await page.locator('#teamInviteUrl').inputValue();
  await page.goto(inviteUrl);

  await expect(page.locator('#inviteTitle')).toHaveText('Únete a Negocio E2E');
  await expect(page.locator('#inviteMeta')).toContainText('duena@negocio.cl');
  await page.locator('#inviteAcceptForm [name="password"]').fill('ClaveSeguraPiloto123');
  await page.locator('#inviteAcceptForm [name="confirmPassword"]').fill('ClaveSeguraPiloto123');
  await page.locator('#inviteAcceptForm button[type="submit"]').click();

  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBe('owner-token');
  await expect(page).toHaveURL(/\/$/);

  await page.goto('/settings.html');
  await expect(page.locator('#teamMembersList')).toContainText('Dueña Negocio');
  await expect(page.locator('#teamMembersList')).toContainText('duena@negocio.cl');
  await expect(page.locator('#teamMembersList')).toContainText('ACTIVO');
  await expect(page.locator('#teamInviteList')).toContainText('Aceptada');
});

test('primary and public navigation fit desktop tablet and mobile viewports', async ({ page }) => {
  test.setTimeout(60000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyTenant(page);
  const viewports = [
    { width: 1440, height: 900 },
    { width: 768, height: 900 },
    { width: 390, height: 844 }
  ];
  const expectNoPageOverflow = async () => {
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
  };

  for (const viewport of viewports) {
    await test.step(`${viewport.width}x${viewport.height}`, async () => {
      await page.setViewportSize(viewport);

      await page.goto('/');
      await expect(page.locator('#primaryNav')).toBeVisible();
      await expect(page.locator('.nav-config')).toHaveAttribute('href', '/settings.html');
      await expectNoPageOverflow();

      await page.goto('/settings.html');
      await expect(page.locator('.nav-config')).toHaveClass(/active/);
      await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();
      await expect(page.locator('#configBusinessPanel')).toBeVisible();
      await expectNoPageOverflow();

      await page.goto('/sales.html');
      await expect(page.locator('.nav nav a[href="/pricing.html"]')).toHaveAttribute('href', '/pricing.html');
      await expectNoPageOverflow();

      await page.goto('/pricing.html');
      await expect(page.locator('#plans .plan')).toHaveCount(2);
      await expect(page.getByRole('link', { name: 'Ver cómo funciona' })).toHaveAttribute('href', '/sales.html');
      await expectNoPageOverflow();
    });
  }
});

test('authentication and phone administration fit all target viewports', async ({ page }) => {
  test.setTimeout(45000);
  const viewports = [
    { width: 1440, height: 900 },
    { width: 768, height: 900 },
    { width: 390, height: 844 }
  ];
  const expectNoPageOverflow = async () => {
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
  };

  for (const viewport of viewports) {
    await test.step(`autenticación ${viewport.width}x${viewport.height}`, async () => {
      await page.setViewportSize(viewport);
      await page.goto('/');
      await expect(page.locator('#registerForm')).toBeVisible();
      await page.locator('#loginTab').click();
      await expect(page.locator('#loginForm')).toBeVisible();
      await expectNoPageOverflow();
    });
  }

  await page.evaluate(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([
    { id: 'phone1', phoneNumber: '+56911111111', active: true }
  ])));
  for (const viewport of viewports) {
    await test.step(`telefonía ${viewport.width}x${viewport.height}`, async () => {
      await page.setViewportSize(viewport);
      await page.goto('/phone-numbers.html');
      await expect(page.getByText('+56911111111')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Desvincular' })).toBeVisible();
      await expect(page.getByRole('link', { name: /Volver a Helvoca/ })).toHaveAttribute('href', '/');
      await expectNoPageOverflow();
    });
  }
});
