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

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({ email: 'admin@demo.cl' })));
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
  await expect(page.locator('#setupForm [name="defaultCurrency"]').locator('xpath=ancestor::label')).toContainText('💰 Moneda');
  await expect(page.locator('#setupForm [name="humanTransferPhone"]').locator('xpath=ancestor::label')).toBeHidden();
  await expect(page.getByRole('button', { name: '⚙️ Más opciones' })).toBeVisible();

  await nav.getByRole('button', { name: '🤖 Recepcionista', exact: true }).click();
  await expect(page.locator('#configAgentPanel')).toBeVisible();
  await expect(page.locator('#configBusinessPanel')).toBeHidden();
  await expect(page.locator('#agentCapabilities')).toBeHidden();
});

test('settings uses selectors for timezone language and voice', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockSettings(page);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();
  await page.getByRole('button', { name: '⚙️ Más opciones' }).click();

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
  await page.getByRole('button', { name: '⚙️ Más opciones' }).click();

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
  await page.locator('#setupForm [name="city"]').fill('Santiago Centro');
  await page.locator('#setupForm [name="defaultCurrency"]').selectOption('USD');
  await page.locator('#setupForm [name="sellsProducts"]').selectOption('false');
  await page.locator('#setupForm [name="usesReservations"]').selectOption('true');
  await page.getByRole('button', { name: '💾 Guardar cambios', exact: true }).click();

  await expect.poll(() => state.profilePayloads.length).toBe(1);
  expect(state.profilePayloads[0]).toMatchObject({
    presetKey: 'store',
    publicDescription: 'Venta y soporte tecnológico',
    publicPhone: '+56922223333',
    publicEmail: 'ventas@negocio.cl',
    websiteUrl: 'https://negocio.cl',
    addressLine: 'Av. Principal 123',
    commune: 'Conchalí',
    city: 'Santiago Centro',
    region: 'Metropolitana',
    countryCode: 'CL',
    defaultCurrency: 'USD',
    sellsProducts: false,
    sellsServices: true,
    usesReservations: true
  });
});

test('settings validates required data and saves the complete business configuration', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const state = {};
  await mockSettings(page, state);
  await page.goto('/settings.html');

  await page.getByRole('button', { name: '🏪 Negocio', exact: true }).click();
  await page.getByRole('button', { name: '⚙️ Más opciones' }).click();
  await expect(page.locator('#setupForm [name="businessName"]')).toHaveValue('Negocio E2E');
  await expect(page.locator('#setupForm [name="humanTransferPhone"]')).toHaveValue('+56999999999');

  await page.getByRole('button', { name: '🤖 Recepcionista', exact: true }).click();
  await expect(page.locator('#setupForm [name="agentGreeting"]')).toHaveValue('Hola, gracias por llamar.');
  await expect(page.locator('#setupForm [name="agentInstructions"]')).toHaveValue('Responde brevemente.');

  const greeting = page.locator('#setupForm [name="agentGreeting"]');
  await greeting.fill('');
  await page.getByRole('button', { name: '💾 Guardar cambios', exact: true }).click();
  await expect(page.locator('#setupMessage')).toContainText('Define el saludo inicial del agente.');
  expect(state.setupPayloads).toEqual([]);

  await greeting.fill('Hola, te atiende Helvoca.');
  await page.locator('#setupForm [name="timezone"]').selectOption('America/Caracas');
  await page.locator('#setupForm [name="language"]').selectOption('es');
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
    businessName: 'Negocio E2E', timezone: 'America/Caracas', language: 'es',
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
