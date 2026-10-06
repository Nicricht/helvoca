const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function boot(page, options = {}) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'settings-react-hardening'
  );

  const requests = [];

  page.on('request', request => {
    if (request.url().includes('/api/v1/')) {
      requests.push({ method: request.method(), path: new URL(request.url()).pathname });
    }
  });

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) return route.fulfill(json({ message: 'expired' }, 401));
    return route.fulfill(json({
      email: 'admin@demo.cl',
      roles: options.roles || ['BUSINESS_ADMIN'],
      permissions: options.permissions || ['BUSINESS_READ', 'BUSINESS_CONFIGURE']
    }));
  });

  await page.route(/\/api\/v1\/business$/, route => route.fulfill(json({
    name: 'Negocio Seguro',
    timezone: 'America/Santiago',
    language: 'es',
    humanTransferPhone: '+56999999999'
  })));

  await page.route('**/api/v1/business/profile', route => {
    if (options.profileError) return route.fulfill(json({ message: 'profile unavailable' }, 503));
    return route.fulfill(json({
      businessId: 'business-server-only',
      presetKey: 'services',
      publicDescription: 'Configuración de prueba',
      publicPhone: '+56922223333',
      publicEmail: 'hola@negocio.cl',
      websiteUrl: 'https://negocio.cl',
      addressLine: 'Av. Principal 123',
      commune: 'Santiago',
      city: 'Santiago',
      region: 'Metropolitana',
      countryCode: 'CL',
      defaultCurrency: 'CLP',
      sellsProducts: true,
      sellsServices: true,
      usesReservations: true
    }));
  });

  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: true,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'READY'
  })));

  await page.route('**/api/v1/services', route => route.fulfill(json([
    { id: 'service-1', name: 'Consulta', durationMinutes: 30, price: 25000, active: true }
  ])));

  await page.route('**/api/v1/business/hours', route => route.fulfill(json([
    { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
  ])));

  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([
    { id: 'knowledge-1', title: 'Ubicación', category: 'Información', content: 'Centro', active: true }
  ])));

  await page.route(/\/api\/v1\/ai-agent(?:\/voices)?$/, route => {
    if (new URL(route.request().url()).pathname.endsWith('/voices')) {
      return route.fulfill(json([{ code: 'natural', selection: 'marin', name: 'Natural' }]));
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

  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([])));

  await page.route('**/api/v1/onboarding/setup', async route => {
    const status = options.setupStatus || 200;
    if (status !== 200) {
      const messages = {
        400: 'invalid configuration',
        403: 'forbidden',
        409: 'configuration conflict'
      };
      return route.fulfill(json({ message: messages[status] || 'save failed' }, status));
    }
    return route.fulfill(json({ readyForCalls: true }));
  });

  return { requests };
}

test.describe('React settings hardening RED contract', () => {
  test('expired auth returns to the login surface', async ({ page }) => {
    await boot(page, { expired: true });
    await page.goto('/app/settings');
    await expect(page).toHaveURL(/\/app\/auth\/?$/);
  });

  for (const scenario of [
    { status: 400, message: /revisa los datos|invalid configuration/i },
    { status: 403, message: /sin permisos|forbidden/i },
    { status: 409, message: /conflicto|configuration conflict/i }
  ]) {
    test(`keeps edits recoverable after ${scenario.status} save response`, async ({ page }) => {
      await boot(page, { setupStatus: scenario.status });
      await page.goto('/app/settings');

      await page.getByLabel('Nombre del negocio').fill('Cambio pendiente');
      await page.getByRole('button', { name: 'Guardar cambios' }).click();

      await expect(page.getByRole('alert')).toContainText(scenario.message);
      await expect(page.getByLabel('Nombre del negocio')).toHaveValue('Cambio pendiente');
      await expect(page.getByRole('status')).toContainText('Cambios sin guardar');
    });
  }

  test('survives a partial profile failure and keeps other settings usable', async ({ page }) => {
    await boot(page, { profileError: true });
    await page.goto('/app/settings');

    await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();
    await expect(page.getByRole('alert')).toContainText(/perfil|parte de la configuración|profile unavailable/i);

    const nav = page.getByRole('tablist', { name: 'Secciones de configuración' });
    await nav.getByRole('tab', { name: 'Servicios' }).click();
    await expect(page.getByText('Consulta', { exact: true })).toBeVisible();
  });

  test('renders safely at 1440 768 and 390 with scrollable internal tabs', async ({ page }) => {
    await boot(page);

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/settings');

      await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth
      )).toBe(true);

      const tabs = page.getByRole('tablist', { name: 'Secciones de configuración' });
      await expect(tabs).toBeVisible();
      if (viewport.width === 390) {
        expect(await tabs.evaluate(node => node.scrollWidth >= node.clientWidth)).toBe(true);
      }
    }
  });

  test('rendering settings never triggers dangerous provider or communication side effects', async ({ page }) => {
    const { requests } = await boot(page);
    await page.goto('/app/settings');
    await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();

    const forbidden = requests.filter(request =>
      request.method !== 'GET' && (
        request.path.includes('/phone-numbers/provisioning')
        || request.path.includes('/channels/whatsapp')
        || request.path.includes('/billing')
        || request.path.includes('/payment-provider')
        || request.path.includes('/calls')
        || request.path.includes('/messages')
      )
    );

    expect(forbidden).toEqual([]);
  });
});
