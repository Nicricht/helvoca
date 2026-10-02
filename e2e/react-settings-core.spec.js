const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootSettingsCore(page, options = {}) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'settings-react-red'
  );

  const state = {
    setupPayloads: [],
    profilePayloads: [],
    agentPayloads: [],
    forbiddenWrites: [],
    business: {
      name: 'Barbería Norte',
      timezone: 'America/Santiago',
      language: 'es',
      humanTransferPhone: '+56999999999'
    },
    profile: {
      businessId: '11111111-1111-1111-1111-111111111111',
      presetKey: 'services',
      publicDescription: 'Barbería y cuidado personal',
      publicPhone: '+56922223333',
      publicEmail: 'hola@barberianorte.cl',
      websiteUrl: 'https://barberianorte.cl',
      addressLine: 'Av. Norte 123',
      commune: 'Conchalí',
      city: 'Santiago',
      region: 'Metropolitana',
      countryCode: 'CL',
      defaultCurrency: 'CLP',
      sellsProducts: true,
      sellsServices: true,
      usesReservations: true
    }
  };

  // Safety net: no unmocked mutation is allowed to escape this E2E contract.
  await page.route('**/api/v1/**', async route => {
    const method = route.request().method();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      state.forbiddenWrites.push({
        method,
        url: new URL(route.request().url()).pathname
      });
      return route.fulfill(json({ message: 'Unexpected mutation blocked by RED contract' }, 501));
    }
    return route.continue();
  });

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) {
      return route.fulfill(json({ message: 'expired' }, 401));
    }
    return route.fulfill(json({
      email: 'admin@demo.cl',
      roles: options.roles || ['BUSINESS_ADMIN'],
      permissions: options.permissions || [
        'BUSINESS_READ',
        'BUSINESS_MANAGE',
        'CATALOG_READ',
        'CATALOG_MANAGE'
      ]
    }));
  });

  await page.route(/\/api\/v1\/business$/, route => route.fulfill(json(state.business)));

  await page.route('**/api/v1/business/profile', async route => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON();
      state.profilePayloads.push(payload);
      state.profile = { ...state.profile, ...payload };
    }
    return route.fulfill(json(state.profile));
  });

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

  await page.route('**/api/v1/services', route => route.fulfill(json([
    {
      id: 'service-1',
      name: 'Corte clásico',
      description: 'Corte de cabello',
      durationMinutes: 30,
      price: 15000,
      active: true
    }
  ])));

  await page.route('**/api/v1/business/hours', route => route.fulfill(json([
    { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
  ])));

  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([
    {
      id: 'knowledge-1',
      title: 'Estacionamiento',
      category: 'Información',
      content: 'Hay estacionamiento para clientes.',
      active: true
    }
  ])));

  await page.route(/\/api\/v1\/ai-agent(?:\/voices)?$/, async route => {
    if (new URL(route.request().url()).pathname.endsWith('/voices')) {
      return route.fulfill(json([
        { code: 'natural', selection: 'marin', name: 'Natural', description: 'Conversacional' }
      ]));
    }
    if (route.request().method() === 'PUT') {
      state.agentPayloads.push(route.request().postDataJSON());
    }
    return route.fulfill(json({
      configured: true,
      name: 'Helvoca',
      language: 'es',
      voice: 'marin',
      greeting: 'Hola, gracias por llamar.',
      instructions: 'Responde brevemente.',
      active: true,
      capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
    }));
  });

  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([
    {
      id: 'phone-1',
      provider: 'TWILIO',
      externalId: 'PNdemo',
      phoneNumber: '+56911111111',
      active: true,
      whatsappEnabled: false
    }
  ])));

  await page.route('**/api/v1/onboarding/setup', async route => {
    if (route.request().method() !== 'PUT') {
      return route.fulfill(json({ message: 'Method not allowed' }, 405));
    }

    state.setupPayloads.push(route.request().postDataJSON());

    if (options.setupError) {
      return route.fulfill(json({ message: options.setupError }, 500));
    }

    if (options.setupDelayMs) {
      await new Promise(resolve => setTimeout(resolve, options.setupDelayMs));
    }

    return route.fulfill(json({ readyForCalls: true }));
  });

  return state;
}

test.describe('React Settings core RED contract', () => {
  test('reads existing configuration in one coherent surface with compact internal navigation', async ({ page }) => {
    await bootSettingsCore(page);
    await page.goto('/app/settings');

    await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();

    const nav = page.getByRole('tablist', { name: 'Secciones de configuración' });
    await expect(nav.getByRole('tab', { name: 'Negocio' })).toBeVisible();
    await expect(nav.getByRole('tab', { name: 'Recepcionista IA' })).toBeVisible();
    await expect(nav.getByRole('tab', { name: 'Servicios' })).toBeVisible();
    await expect(nav.getByRole('tab', { name: 'Horarios' })).toBeVisible();
    await expect(nav.getByRole('tab', { name: /Conocimiento|Respuestas/ })).toBeVisible();
    await expect(nav.getByRole('tab', { name: 'Canales' })).toBeVisible();
    await expect(nav.getByRole('tab', { name: 'Integraciones' })).toBeVisible();

    await expect(page.getByLabel('Nombre del negocio')).toHaveValue('Barbería Norte');
    await expect(page.getByLabel('Zona horaria')).toHaveValue('America/Santiago');
    await expect(page.getByLabel('Descripción pública')).toHaveValue('Barbería y cuidado personal');

    await nav.getByRole('tab', { name: 'Servicios' }).click();
    await expect(nav.getByRole('tab', { name: 'Servicios' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('Corte clásico')).toBeVisible();

    // Provider credentials/secrets must never be rendered as plain settings values.
    await expect(page.getByText(/auth token|access token|api secret|client secret/i)).toHaveCount(0);
    await expect(page.locator('input[type="password"]')).toHaveCount(0);
  });

  test('tracks dirty/saving/saved, avoids duplicate submit and never sends businessId', async ({ page }) => {
    const state = await bootSettingsCore(page, { setupDelayMs: 250 });
    await page.goto('/app/settings');

    const name = page.getByLabel('Nombre del negocio');
    const save = page.getByRole('button', { name: 'Guardar cambios' });

    await name.fill('Barbería Norte Centro');
    await expect(page.getByRole('status')).toContainText('Cambios sin guardar');

    await save.evaluate(button => {
      button.form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      button.form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });

    await expect(save).toBeDisabled();
    await expect(page.getByRole('status')).toContainText('Guardando');

    await expect.poll(() => state.setupPayloads.length).toBe(1);
    expect(state.setupPayloads[0]).toMatchObject({
      businessName: 'Barbería Norte Centro',
      timezone: 'America/Santiago',
      language: 'es'
    });
    expect(state.setupPayloads[0]).not.toHaveProperty('businessId');
    expect(state.profilePayloads.every(payload => !Object.hasOwn(payload, 'businessId'))).toBe(true);
    expect(state.agentPayloads.every(payload => !Object.hasOwn(payload, 'businessId'))).toBe(true);
    expect(state.forbiddenWrites).toEqual([]);

    await expect(page.getByRole('status')).toContainText('Guardado');
  });

  test('blocks invalid edits before any mutation request', async ({ page }) => {
    const state = await bootSettingsCore(page);
    await page.goto('/app/settings');

    await page.getByLabel('Nombre del negocio').fill('');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();

    await expect(page.getByRole('alert')).toContainText(/nombre.*obligatorio|revisa.*obligatorio/i);
    expect(state.setupPayloads).toHaveLength(0);
    expect(state.profilePayloads).toHaveLength(0);
    expect(state.agentPayloads).toHaveLength(0);
    expect(state.forbiddenWrites).toEqual([]);
  });

  test('keeps edited values dirty and recoverable when the server fails', async ({ page }) => {
    const state = await bootSettingsCore(page, { setupError: 'configuration unavailable' });
    await page.goto('/app/settings');

    await page.getByLabel('Nombre del negocio').fill('Barbería Norte Error');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();

    await expect(page.getByRole('alert')).toContainText(/no pudimos guardar|configuration unavailable|intenta nuevamente/i);
    await expect(page.getByLabel('Nombre del negocio')).toHaveValue('Barbería Norte Error');
    await expect(page.getByRole('status')).toContainText('Cambios sin guardar');
    expect(state.setupPayloads).toHaveLength(1);
    expect(state.forbiddenWrites).toEqual([]);
  });

  test('restricted roles can inspect core settings without mutation controls', async ({ page }) => {
    const state = await bootSettingsCore(page, {
      roles: ['OPERATOR'],
      permissions: ['BUSINESS_READ', 'CATALOG_READ']
    });
    await page.goto('/app/settings');

    await expect(page.getByLabel('Nombre del negocio')).toHaveValue('Barbería Norte');
    await expect(page.getByRole('button', { name: 'Guardar cambios' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /añadir|eliminar|activar|conectar/i })).toHaveCount(0);
    expect(state.setupPayloads).toHaveLength(0);
    expect(state.profilePayloads).toHaveLength(0);
    expect(state.agentPayloads).toHaveLength(0);
    expect(state.forbiddenWrites).toEqual([]);
  });
});
