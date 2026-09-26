const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockSettings(page, state = {}) {
  state.phone = state.phone || {
    id: 'phone-1', provider: 'TWILIO', externalId: 'PNdemo', phoneNumber: '+56911111111',
    active: true, whatsappEnabled: false, whatsappCertifiedAt: null
  };
  state.whatsappPatches = [];
  state.setupPayloads = [];
  state.profilePayloads = [];
  state.scheduleExceptionPuts = [];
  state.scheduleExceptionDeletes = [];
  state.activationPayloads = [];
  state.invitationCreates = [];
  state.invitationRevokes = [];
  state.invitations = state.invitations || [];
  state.managedPaymentActions = [];
  state.managedPayment = state.managedPayment || {
    available: true,
    configured: false,
    enabled: false,
    blockedByCustomConfiguration: false,
    provider: null,
    mode: null,
    webhookPath: null
  };
  state.activation = state.activation || {
    ready: false,
    completed: 6,
    total: 10,
    progressPercent: 60,
    blockers: ['FAQ_REVIEW_REQUIRED', 'CONVERSATION_TEST_REQUIRED', 'MUTATION_TESTS_REQUIRED', 'HUMAN_HANDOFF_TEST_REQUIRED'],
    steps: [
      { code: 'CORE_SETUP', label: 'Negocio configurado', complete: true, automatic: true, required: true, actionHref: '/settings.html#configuration' },
      { code: 'TECHNICAL_READINESS', label: 'Canales y operación listos', complete: true, automatic: true, required: true, actionHref: '/' },
      { code: 'PRICES_CONFIRMED', label: 'Precios confirmados', complete: true, automatic: false, required: true, actionHref: '/settings.html#configuration' },
      { code: 'FAQ_REVIEWED', label: 'FAQ revisada', complete: false, automatic: false, required: true, actionHref: '/settings.html#configuration' },
      { code: 'POLICIES_APPROVED', label: 'Políticas aprobadas', complete: true, automatic: false, required: true, actionHref: '/settings.html#configuration' },
      { code: 'AGENT_INSTRUCTIONS_APPROVED', label: 'Recepcionista aprobada', complete: true, automatic: false, required: true, actionHref: '/settings.html#configuration' },
      { code: 'PILOT_SCOPE_APPROVED', label: 'Alcance del piloto aprobado', complete: true, automatic: false, required: true, actionHref: '/settings.html' },
      { code: 'CONVERSATION_TEST_COMPLETED', label: 'Conversación de prueba completada', complete: false, automatic: false, required: true, actionHref: '/' },
      { code: 'MUTATION_TESTS_COMPLETED', label: 'Reservas / pedidos / pagos probados', complete: false, automatic: false, required: true, actionHref: '/' },
      { code: 'HUMAN_HANDOFF_TESTED', label: 'Derivación humana probada', complete: false, automatic: false, required: true, actionHref: '/settings.html#configuration' }
    ]
  };
  state.scheduleExceptions = state.scheduleExceptions || [
    {
      id: 'exception-1',
      exceptionDate: '2026-12-25',
      closed: true,
      openTime: null,
      closeTime: null,
      reason: 'Navidad'
    }
  ];
  state.profile = state.profile || {
    businessId: 'business-1',
    presetKey: 'store',
    publicDescription: 'Tecnología y accesorios',
    publicPhone: '+56922223333',
    publicEmail: 'ventas@negocio.cl',
    websiteUrl: 'https://negocio.cl',
    addressLine: 'Av. Principal 123',
    commune: 'Conchalí',
    city: 'Santiago',
    region: 'Metropolitana',
    countryCode: 'CL',
    defaultCurrency: 'CLP',
    sellsProducts: true,
    sellsServices: true,
    usesReservations: false
  };

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({ email: 'admin@demo.cl', roles: state.roles || ['BUSINESS_ADMIN'] })));
  await page.route('**/api/v1/admin/invitations/*', async route => {
    if (route.request().method() === 'DELETE') {
      const id = route.request().url().split('/').pop();
      state.invitationRevokes.push(id);
      state.invitations = state.invitations.map(item =>
        item.id === id ? { ...item, status: 'REVOKED' } : item);
      await route.fulfill({ status: 204, body: '' });
      return;
    }
    await route.fulfill({ status: 405, body: '' });
  });
  await page.route('**/api/v1/admin/invitations', async route => {
    if (route.request().method() === 'POST') {
      const payload = route.request().postDataJSON();
      state.invitationCreates.push(payload);
      const created = {
        id: 'invite-' + (state.invitations.length + 1),
        businessId: '11111111-1111-1111-1111-111111111111',
        businessName: 'Negocio E2E',
        name: payload.name,
        email: payload.email,
        role: payload.role,
        expiresAt: '2026-09-29T12:00:00Z',
        status: 'PENDING',
        invitePath: '/invite.html?businessId=11111111-1111-1111-1111-111111111111&token=test-token'
      };
      state.invitations.unshift({ ...created, invitePath: null });
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(created) });
      return;
    }
    await route.fulfill(json(state.invitations));
  });
  await page.route('**/api/v1/payment-provider/managed-sandbox/enable', route => {
    state.managedPaymentActions.push('enable');
    state.managedPayment = {
      available: true,
      configured: true,
      enabled: true,
      blockedByCustomConfiguration: false,
      provider: 'mercadopago',
      mode: 'SANDBOX',
      webhookPath: '/webhooks/v1/payments/mercadopago/demo-key'
    };
    route.fulfill(json(state.managedPayment));
  });
  await page.route('**/api/v1/payment-provider/managed-sandbox/disable', route => {
    state.managedPaymentActions.push('disable');
    state.managedPayment = {
      ...state.managedPayment,
      configured: true,
      enabled: false
    };
    route.fulfill(json(state.managedPayment));
  });
  await page.route('**/api/v1/payment-provider/managed-sandbox', route =>
    route.fulfill(json(state.managedPayment)));
  await page.route('**/api/v1/business/profile', async route => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON();
      state.profilePayloads.push(payload);
      state.profile = { ...state.profile, ...payload };
    }
    await route.fulfill(json(state.profile));
  });
  await page.route(/\/api\/v1\/business$/, route => route.fulfill(json({
    name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', humanTransferPhone: '+56999999999'
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true, servicesConfigured: true, scheduleConfigured: true,
    knowledgeConfigured: true, humanTransferConfigured: true, phoneConfigured: true,
    readyForCalls: true, nextStep: 'READY'
  })));
  await page.route('**/api/v1/onboarding/activation', async route => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON();
      state.activationPayloads.push(payload);
      state.activation = {
        ...state.activation,
        ready: Object.values(payload).every(Boolean),
        completed: Object.values(payload).filter(Boolean).length + 2,
        progressPercent: Math.round((Object.values(payload).filter(Boolean).length + 2) * 10),
        steps: state.activation.steps.map(step => {
          const fieldByCode = {
            PRICES_CONFIRMED: 'pricesConfirmed',
            FAQ_REVIEWED: 'faqReviewed',
            POLICIES_APPROVED: 'policiesApproved',
            AGENT_INSTRUCTIONS_APPROVED: 'agentInstructionsApproved',
            PILOT_SCOPE_APPROVED: 'pilotScopeApproved',
            CONVERSATION_TEST_COMPLETED: 'conversationTestCompleted',
            MUTATION_TESTS_COMPLETED: 'mutationTestsCompleted',
            HUMAN_HANDOFF_TESTED: 'humanHandoffTested'
          };
          const field = fieldByCode[step.code];
          return field ? { ...step, complete: Boolean(payload[field]) } : step;
        })
      };
    }
    await route.fulfill(json(state.activation));
  });
  await page.route('**/api/v1/onboarding/setup', async route => {
    if (route.request().method() === 'PUT') {
      state.setupPayloads.push(route.request().postDataJSON());
      if (state.setupError) {
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: state.setupError }) });
        return;
      }
    }
    await route.fulfill(json({ readyForCalls: true }));
  });
  await page.route('**/api/v1/services', route => route.fulfill(json([
    { id: 'service-1', name: 'Consulta', durationMinutes: 30, price: 25000, description: 'Consulta general', active: true }
  ])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([
    { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
  ])));
  await page.route(/\/api\/v1\/business\/schedule-exceptions(?:\/([^/?]+))?$/, async route => {
    const request = route.request();
    const url = new URL(request.url());
    const parts = url.pathname.split('/').filter(Boolean);
    const date = parts.length > 4 ? decodeURIComponent(parts[parts.length - 1]) : null;

    if (request.method() === 'GET' && !date) {
      await route.fulfill(json(state.scheduleExceptions));
      return;
    }

    if (request.method() === 'PUT' && date) {
      const payload = request.postDataJSON();
      state.scheduleExceptionPuts.push({ date, payload });
      const existing = state.scheduleExceptions.find(item => item.exceptionDate === date);
      const saved = {
        id: existing?.id || 'exception-' + (state.scheduleExceptions.length + 1),
        exceptionDate: date,
        closed: payload.closed,
        openTime: payload.openTime,
        closeTime: payload.closeTime,
        reason: payload.reason
      };
      const index = state.scheduleExceptions.findIndex(item => item.exceptionDate === date);
      if (index >= 0) state.scheduleExceptions[index] = saved;
      else state.scheduleExceptions.push(saved);
      await route.fulfill(json(saved));
      return;
    }

    if (request.method() === 'DELETE' && date) {
      state.scheduleExceptionDeletes.push(date);
      state.scheduleExceptions = state.scheduleExceptions.filter(item => item.exceptionDate !== date);
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    await route.fulfill({ status: 405, body: '' });
  });
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([
    { id: 'knowledge-1', title: 'Ubicación', category: 'Información', content: 'Centro', active: true }
  ])));
  await page.route(/\/api\/v1\/ai-agent(?:\/voices)?$/, async route => {
    if (new URL(route.request().url()).pathname.endsWith('/voices')) {
      await route.fulfill(json([
        { code: 'natural', selection: 'marin', name: 'Natural', description: 'Equilibrada y conversacional' }
      ]));
      return;
    }
    if (route.request().method() === 'PUT') {
      state.agentPayload = route.request().postDataJSON();
      if (state.agentError) {
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: state.agentError }) });
        return;
      }
    }
    await route.fulfill(json({
      configured: true, name: 'Helvoca', language: 'es', voice: 'marin',
      greeting: 'Hola, gracias por llamar.', instructions: 'Responde brevemente.', active: true,
      capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
    }));
  });
  await page.route('**/api/v1/phone-numbers/phone-1/whatsapp', async route => {
    const payload = route.request().postDataJSON();
    state.whatsappPatches.push(payload);
    if (state.whatsappError) {
      await route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ message: state.whatsappError }) });
      return;
    }
    state.phone.whatsappEnabled = payload.enabled;
    await route.fulfill(json(state.phone));
  });
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false, configured: false, purchaseAvailable: false, provider: 'TWILIO', message: 'No disponible en E2E'
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([state.phone])));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    billingEnabled: true, checkoutConfigured: false, currentPlanCode: 'PRO', currentPlanName: 'Pro',
    currentMonthlyPriceClp: 69990, subscriptionStatus: 'ACTIVE', awaitingProviderVerification: false
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    plan: 'PRO', status: 'ACTIVE', serviceAllowed: true, maxConcurrentCalls: 10,
    includedMinutes: 500, usedMinutes: 23, overageMinutes: 0, billingProviderConnected: true
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({})));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([])));
  await page.route('**/api/v1/onboarding/analyze', route => route.fulfill(json(state.proposal || {
    businessName: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', sourceReadable: true,
    sourceSummary: 'Propuesta E2E', services: [], hours: [], knowledge: [], warnings: []
  })));
}

test('settings exposes the Mi negocio sections with simple navigation', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockSettings(page);
  await page.goto('/settings.html');

  await expect(page.locator('.dashboard-heading h1')).toHaveText('Mi negocio');
  const nav = page.locator('#advancedPanel .ux-config-nav');
  await expect(nav.getByRole('button')).toHaveText([
    '🏪 Negocio', '✂️ Servicios', '📅 Horarios', '💬 Respuestas', '🤖 Recepcionista', '📞 Canales'
  ]);
  await expect(page.locator('#configBusinessPanel')).toBeVisible();
  await expect(page.locator('#configAgentPanel')).toBeHidden();

  for (const name of ['businessName', 'presetKey', 'publicDescription', 'addressLine', 'publicPhone', 'publicEmail', 'websiteUrl']) {
    await expect(page.locator(`#setupForm [name="${name}"]`).locator('xpath=ancestor::label')).toBeVisible();
  }
  for (const name of [
    'humanTransferPhone', 'timezone', 'language', 'defaultCurrency',
    'commune', 'city', 'region', 'countryCode',
    'sellsProducts', 'sellsServices', 'usesReservations'
  ]) {
    await expect(page.locator(`#setupForm [name="${name}"]`).locator('xpath=ancestor::label')).toBeHidden();
  }
  await expect(page.getByRole('button', { name: '⚙️ Más opciones' })).toHaveCount(0);

  await nav.getByRole('button', { name: '🤖 Recepcionista', exact: true }).click();
  await expect(page.locator('#configAgentPanel')).toBeVisible();
  await expect(page.locator('#configBusinessPanel')).toBeHidden();
  await expect(page.locator('#agentCapabilities')).toBeHidden();
});

test('business admin creates a one-time team invitation without choosing another user password', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  const card = page.locator('#teamInvitationsCard');
  await expect(card).toBeVisible();
  await card.locator('input[name="name"]').fill('Camila Soto');
  await card.locator('input[name="email"]').fill('camila@negocio.cl');
  await card.locator('select[name="role"]').selectOption('OPERATOR');
  await card.getByRole('button', { name: 'Generar invitación' }).click();

  await expect.poll(() => state.invitationCreates.length).toBe(1);
  expect(state.invitationCreates[0]).toEqual({
    name: 'Camila Soto',
    email: 'camila@negocio.cl',
    role: 'OPERATOR'
  });
  await expect(page.locator('#teamInviteUrl')).toHaveValue(/invite\.html\?businessId=.*&token=test-token/);
  await expect(page.locator('#teamInviteMessage')).toContainText('Comparte este enlace');
  await expect(card).toContainText('Camila Soto');
  await expect(card).toContainText('Pendiente');
});

test('settings turns first customer onboarding into an operational activation checklist', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  const card = page.locator('#pilotActivationCard');
  await expect(card).toBeVisible();
  await expect(page.locator('#pilotActivationScore')).toHaveText('6/10');
  await expect(card.locator('[data-code="CORE_SETUP"]')).toHaveClass(/complete/);
  await expect(card.locator('[data-code="CORE_SETUP"] input')).toHaveCount(0);
  await expect(card.locator('[data-code="FAQ_REVIEWED"] input')).not.toBeChecked();

  await card.locator('[data-code="FAQ_REVIEWED"] input').check();
  await card.locator('[data-code="CONVERSATION_TEST_COMPLETED"] input').check();
  await card.locator('[data-code="MUTATION_TESTS_COMPLETED"] input').check();
  await card.locator('[data-code="HUMAN_HANDOFF_TESTED"] input').check();
  await page.locator('#pilotActivationSave').click();

  await expect.poll(() => state.activationPayloads.length).toBe(1);
  expect(state.activationPayloads[0]).toMatchObject({
    pricesConfirmed: true,
    faqReviewed: true,
    policiesApproved: true,
    agentInstructionsApproved: true,
    pilotScopeApproved: true,
    conversationTestCompleted: true,
    mutationTestsCompleted: true,
    humanHandoffTested: true
  });
  await expect(page.locator('#pilotActivationScore')).toHaveText('10/10');
  await expect(page.locator('#pilotActivationMessage')).toContainText('Checklist completo');
});

test('settings uses selectors for timezone language and voice', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockSettings(page);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();

  await expect(page.locator('#setupForm select[name="timezone"]').locator('xpath=ancestor::label')).toBeHidden();
  await expect(page.locator('#setupForm select[name="language"]').locator('xpath=ancestor::label')).toBeHidden();
  await expect(page.locator('#setupForm select[name="timezone"]')).toHaveValue('America/Santiago');
  await expect(page.locator('#setupForm select[name="language"]')).toHaveValue('es');

  await page.getByRole('button', { name: '🤖 Recepcionista', exact: true }).click();
  await expect(page.locator('#agentVoiceSelect')).toHaveValue('marin');
});



test('settings preset changes guidance only and never rewrites capability choices', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockSettings(page);
  await page.goto('/settings.html');

  const preset = page.locator('#setupForm [name="presetKey"]');
  const products = page.locator('#setupForm [name="sellsProducts"]');
  const services = page.locator('#setupForm [name="sellsServices"]');
  const reservations = page.locator('#setupForm [name="usesReservations"]');
  const suggestion = page.locator('#presetSuggestion');

  await expect(suggestion).toHaveText('Prioriza productos.');
  await expect(products).toHaveValue('true');
  await expect(services).toHaveValue('true');
  await expect(reservations).toHaveValue('false');

  await preset.selectOption('salon');
  await expect(suggestion).toHaveText('Prioriza servicios y reservas.');
  await expect(products).toHaveValue('true');
  await expect(services).toHaveValue('true');
  await expect(reservations).toHaveValue('false');

  await preset.selectOption('restaurant');
  await expect(suggestion).toHaveText('Prioriza productos y pedidos.');

  await preset.selectOption('clinic');
  await expect(suggestion).toHaveText('Prioriza servicios y reservas.');
});

test('settings activates managed Mercado Pago sandbox without exposing credentials', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();

  const card = page.locator('#managedPaymentSandbox');
  await expect(card).toBeVisible();
  await expect(card).toContainText('Pagos de prueba disponibles');
  await expect(page.locator('#managedPaymentSandboxEnable')).toBeVisible();
  await expect(card.locator('input[type="password"]')).toHaveCount(0);
  await expect(card).not.toContainText('credentialRef');
  await expect(card).not.toContainText('ACCESS_TOKEN');

  await page.locator('#managedPaymentSandboxEnable').click();

  await expect.poll(() => state.managedPaymentActions).toEqual(['enable']);
  await expect(card).toContainText('Mercado Pago Sandbox activo');
  await expect(page.locator('#managedPaymentSandboxDisable')).toBeVisible();
  await expect(page.locator('#managedPaymentSandboxMessage')).toContainText('Pagos de prueba activados');
});

test('settings exposes WhatsApp state and changes it only after an explicit click', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();
  await page.getByRole('button', { name: 'Cambiar' }).click();
  expect(state.whatsappPatches).toEqual([]);

  await expect(page.getByText('WhatsApp inactivo')).toBeVisible();
  await page.getByRole('button', { name: 'Activar WhatsApp' }).click();

  await expect.poll(() => state.whatsappPatches).toEqual([{ enabled: true }]);
  await expect(page.locator('#phoneMessage')).toContainText('WhatsApp habilitado');
  await expect(page.getByText('WhatsApp activo')).toBeVisible();
});

test('settings preserves valid proposal locale values that are not in the suggested lists', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {
    proposal: {
      businessName: 'Negocio Global', timezone: 'Pacific/Auckland', language: 'fr', sourceReadable: true,
      sourceSummary: 'Propuesta internacional', services: [], hours: [], knowledge: [], warnings: []
    }
  };
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('textbox', { name: 'Web, Instagram o Google Maps' }).fill('https://example.test');
  await page.getByRole('button', { name: 'Analizar', exact: true }).click();
  await page.getByRole('button', { name: 'Editar', exact: true }).click();

  await expect(page.locator('#setupForm [name="timezone"]')).toHaveValue('Pacific/Auckland');
  await expect(page.locator('#setupForm [name="language"]')).toHaveValue('fr');
});

test('settings shows WhatsApp API errors and leaves the explicit action usable', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = { whatsappError: 'WhatsApp todavía no está certificado' };
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '📞 Canales', exact: true }).click();
  await page.getByRole('button', { name: 'Cambiar' }).click();
  const action = page.getByRole('button', { name: 'Activar WhatsApp' });
  await action.click();

  await expect(page.locator('#phoneMessage')).toContainText('WhatsApp todavía no está certificado');
  await expect(action).toBeEnabled();
  await expect(page.getByText('WhatsApp inactivo')).toBeVisible();
});

test('settings loads and saves the public business profile', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();

  await expect(page.locator('#setupForm [name="presetKey"]')).toHaveValue('store');
  await expect(page.locator('#setupForm [name="publicDescription"]')).toHaveValue('Tecnología y accesorios');
  await expect(page.locator('#setupForm [name="publicPhone"]')).toHaveValue('+56922223333');
  await expect(page.locator('#setupForm [name="publicEmail"]')).toHaveValue('ventas@negocio.cl');
  await expect(page.locator('#setupForm [name="countryCode"]')).toHaveValue('CL');
  await expect(page.locator('#setupForm [name="city"]')).toHaveValue('Santiago');
  await expect(page.locator('#setupForm [name="defaultCurrency"]')).toHaveValue('CLP');
  await expect(page.locator('#setupForm [name="sellsProducts"]')).toHaveValue('true');
  await expect(page.locator('#setupForm [name="sellsServices"]')).toHaveValue('true');
  await expect(page.locator('#setupForm [name="usesReservations"]')).toHaveValue('false');

  await page.locator('#setupForm [name="publicDescription"]').fill('Venta y soporte tecnológico');
  await page.locator('#setupForm [name="publicPhone"]').fill('+56933334444');
  await page.getByRole('button', { name: '💾 Guardar cambios', exact: true }).click();

  await expect.poll(() => state.profilePayloads.length).toBe(1);
  expect(state.profilePayloads[0]).toMatchObject({
    presetKey: 'store',
    publicDescription: 'Venta y soporte tecnológico',
    publicPhone: '+56933334444',
    publicEmail: 'ventas@negocio.cl',
    websiteUrl: 'https://negocio.cl',
    addressLine: 'Av. Principal 123',
    commune: 'Conchalí',
    city: 'Santiago',
    region: 'Metropolitana',
    countryCode: 'CL',
    defaultCurrency: 'CLP',
    sellsProducts: true,
    sellsServices: true,
    usesReservations: false
  });
});

test('settings validates required data and saves the complete business configuration', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();
  await expect(page.locator('#setupForm [name="businessName"]')).toHaveValue('Negocio E2E');
  await expect(page.locator('#setupForm [name="humanTransferPhone"]')).toHaveValue('+56999999999');
  await expect(page.locator('#setupForm [name="humanTransferPhone"]').locator('xpath=ancestor::label')).toBeHidden();

  await page.getByRole('button', { name: '🤖 Recepcionista', exact: true }).click();
  await expect(page.locator('#setupForm [name="agentGreeting"]')).toHaveValue('Hola, gracias por llamar.');
  await expect(page.locator('#setupForm [name="agentInstructions"]')).toHaveValue('Responde brevemente.');

  const greeting = page.locator('#setupForm [name="agentGreeting"]');
  await greeting.fill('');
  await page.getByRole('button', { name: '💾 Guardar cambios', exact: true }).click();
  await expect(page.locator('#setupMessage')).toContainText('Define el saludo inicial del agente.');
  expect(state.setupPayloads).toEqual([]);

  await greeting.fill('Hola, te atiende Helvoca.');
  await page.locator('#setupForm [name="agentActive"]').uncheck();

  await page.getByRole('button', { name: 'Editar', exact: true }).click();
  await expect(page.locator('input[name="agentCapability"][value="GET_BUSINESS_INFORMATION"]')).toBeChecked();
  await page.locator('input[name="agentCapability"][value="TRANSFER_TO_HUMAN"]').check();

  await page.getByRole('button', { name: '✂️ Servicios', exact: true }).click();
  await expect(page.locator('#servicesList [data-field="name"]')).toHaveValue('Consulta');
  await page.getByRole('button', { name: '📅 Horarios', exact: true }).click();
  await expect(page.locator('#hoursGrid [data-field="openTime"]')).toHaveValue('09:00');
  await page.getByRole('button', { name: '💬 Respuestas', exact: true }).click();
  await expect(page.locator('#knowledgeList [data-field="title"]')).toHaveValue('Ubicación');

  await page.getByRole('button', { name: '💾 Guardar cambios', exact: true }).click();
  await expect.poll(() => state.setupPayloads.length).toBe(1);
  expect(state.setupPayloads[0]).toMatchObject({
    businessName: 'Negocio E2E', timezone: 'America/Santiago', language: 'es',
    humanTransferPhone: '+56999999999',
    services: [{ id: 'service-1', name: 'Consulta', durationMinutes: 30, price: 25000, description: 'Consulta general' }],
    hours: [{ dayOfWeek: 1, openTime: '09:00', closeTime: '18:00' }],
    knowledge: [{ id: 'knowledge-1', title: 'Ubicación', category: 'Información', content: 'Centro' }]
  });
  await expect.poll(() => state.agentPayload).toBeTruthy();
  expect(state.agentPayload).toMatchObject({
    name: 'Helvoca', language: 'es', greeting: 'Hola, te atiende Helvoca.',
    voice: 'marin', instructions: 'Responde brevemente.', active: false
  });
  expect(state.agentPayload.capabilities).toContain('TRANSFER_TO_HUMAN');
  await expect(page.locator('#setupMessage')).toContainText('Negocio y agente guardados correctamente.');
});

test('settings keeps the save action usable and shows backend errors', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = { setupError: 'No se pudo guardar la configuración' };
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();
  const save = page.getByRole('button', { name: '💾 Guardar cambios', exact: true });
  await save.click();

  await expect(page.locator('#setupMessage')).toContainText('No se pudo guardar la configuración');
  await expect(save).toBeEnabled();
  expect(state.setupPayloads).toHaveLength(1);
});

test('settings reports agent save errors after the business payload succeeds', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = { agentError: 'No se pudo guardar el agente' };
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();
  const save = page.getByRole('button', { name: '💾 Guardar cambios', exact: true });
  await save.click();

  await expect(page.locator('#setupMessage')).toContainText('No se pudo guardar el agente');
  await expect(save).toBeEnabled();
  expect(state.setupPayloads).toHaveLength(1);
  expect(state.agentPayload).toBeTruthy();
});


test('settings manages closed dates and special opening hours', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '📅 Horarios', exact: true }).click();

  const panel = page.locator('#scheduleExceptionsPanel');
  await expect(panel).toBeVisible();
  await expect(panel).toContainText('Días especiales');
  await expect(page.locator('[data-exception-date="2026-12-25"]')).toContainText('Cerrado todo el día');
  await expect(page.locator('[data-exception-date="2026-12-25"]')).toContainText('Navidad');

  const form = page.locator('#scheduleExceptionForm');
  await form.locator('[name="date"]').fill('2026-12-31');
  await form.locator('[name="kind"]').selectOption('special');
  await form.locator('[name="openTime"]').fill('09:30');
  await form.locator('[name="closeTime"]').fill('13:00');
  await form.locator('[name="reason"]').fill('Horario fin de año');
  await form.getByRole('button', { name: 'Guardar día' }).click();

  await expect.poll(() => state.scheduleExceptionPuts).toEqual([{
    date: '2026-12-31',
    payload: {
      closed: false,
      openTime: '09:30',
      closeTime: '13:00',
      reason: 'Horario fin de año'
    }
  }]);
  await expect(page.locator('[data-exception-date="2026-12-31"]')).toContainText('Horario especial · 09:30–13:00');
  await expect(page.locator('#scheduleExceptionMessage')).toContainText('Día especial guardado.');

  page.once('dialog', dialog => dialog.accept());
  await page.locator('[data-exception-date="2026-12-25"]').getByRole('button', { name: 'Eliminar' }).click();

  await expect.poll(() => state.scheduleExceptionDeletes).toEqual(['2026-12-25']);
  await expect(page.locator('[data-exception-date="2026-12-25"]')).toHaveCount(0);
  await expect(page.locator('#scheduleExceptionMessage')).toContainText('Día especial eliminado.');
});

test('settings lets operators review schedule exceptions without mutation controls', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = { roles: ['OPERATOR'] };
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '📅 Horarios', exact: true }).click();

  await expect(page.locator('#scheduleExceptionsReadonly')).toBeVisible();
  await expect(page.locator('#scheduleExceptionForm')).toBeHidden();
  await expect(page.locator('[data-exception-date="2026-12-25"]')).toContainText('Navidad');
  await expect(page.locator('[data-exception-date="2026-12-25"]').getByRole('button', { name: 'Editar' })).toHaveCount(0);
  await expect(page.locator('[data-exception-date="2026-12-25"]').getByRole('button', { name: 'Eliminar' })).toHaveCount(0);
  expect(state.scheduleExceptionPuts).toEqual([]);
  expect(state.scheduleExceptionDeletes).toEqual([]);
});
