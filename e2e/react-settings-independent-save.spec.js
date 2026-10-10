const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) });

async function boot(page, options = {}) {
  await page.addInitScript(token => sessionStorage.setItem('helvoca_access_token', token), 'settings-independent-save');

  const writes = [];
  const data = {
    business: { name: 'Mi negocio', timezone: 'America/Santiago', language: 'es', humanTransferPhone: '+56999999999' },
    profile: {
      presetKey: 'services', publicDescription: 'Descripción inicial', publicPhone: null,
      publicEmail: null, websiteUrl: null, addressLine: null, commune: null, city: null,
      region: null, countryCode: 'CL', defaultCurrency: 'CLP',
      sellsProducts: null, sellsServices: true, usesReservations: true
    },
    services: [{ id: 's1', name: 'Consulta', description: 'Inicial', durationMinutes: 30, price: 15000, active: true }],
    hours: [{ dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }],
    knowledge: [{ id: 'k1', title: 'Dirección', category: 'General', content: 'Calle 1', active: true }],
    agent: { name: 'Helvoca', voice: 'marin', language: 'es', greeting: 'Hola', instructions: 'Breve', active: true, capabilities: [] }
  };
  if (options.emptyCollections) { data.services = []; data.hours = []; data.knowledge = []; }
  let failProfileOnce = Boolean(options.failProfileOnce);

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const payload = !['GET', 'HEAD', 'OPTIONS'].includes(method) ? request.postDataJSON() : null;
    if (method !== 'GET') writes.push({ path, method, payload });

    if (path === '/api/v1/auth/me') return route.fulfill(json({
      roles: options.roles || ['BUSINESS_ADMIN'],
      permissions: options.permissions || ['BUSINESS_READ', 'BUSINESS_CONFIGURE']
    }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({ readyForCalls: true }));
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([]));
    if (path === '/api/v1/ai-agent/voices') return route.fulfill(json([{ selection: 'marin', name: 'Natural' }]));

    if (path === '/api/v1/business' && method === 'GET') return route.fulfill(json(data.business));
    if (path === '/api/v1/business' && method === 'PATCH') {
      data.business = { ...data.business, ...payload };
      return route.fulfill(json(data.business));
    }
    if (path === '/api/v1/business/profile') {
      if (method === 'PUT') {
        if (failProfileOnce) { failProfileOnce = false; return route.fulfill(json({ message: 'Perfil temporalmente indisponible' }, 503)); }
        data.profile = { ...data.profile, ...payload };
      }
      return route.fulfill(json(data.profile));
    }
    if (path === '/api/v1/ai-agent') {
      if (method === 'PUT') data.agent = { ...data.agent, ...payload };
      return route.fulfill(json(data.agent));
    }
    if (path === '/api/v1/services' && method === 'GET') return route.fulfill(json(data.services));
    if (path === '/api/v1/services' && method === 'POST') {
      const item = { id: 's' + (data.services.length + 1), ...payload };
      data.services.push(item);
      return route.fulfill(json(item));
    }
    if (path.startsWith('/api/v1/services/') && method === 'PATCH') {
      const item = data.services.find(x => x.id === path.split('/').at(-1));
      Object.assign(item, payload);
      return route.fulfill(json(item));
    }
    if (path.startsWith('/api/v1/services/') && method === 'DELETE') {
      const item = data.services.find(x => x.id === path.split('/').at(-1));
      item.active = false;
      return route.fulfill(json(null));
    }
    if (path === '/api/v1/business/hours') {
      if (method === 'PUT') data.hours = payload.hours;
      return route.fulfill(json(data.hours));
    }
    if (path === '/api/v1/knowledge' && method === 'GET') return route.fulfill(json(data.knowledge));
    if (path === '/api/v1/knowledge' && method === 'POST') {
      const item = { id: 'k' + (data.knowledge.length + 1), ...payload };
      data.knowledge.push(item);
      return route.fulfill(json(item));
    }
    if (path.startsWith('/api/v1/knowledge/') && method === 'PATCH') {
      const item = data.knowledge.find(x => x.id === path.split('/').at(-1));
      Object.assign(item, payload);
      return route.fulfill(json(item));
    }
    if (path.startsWith('/api/v1/knowledge/') && method === 'DELETE') {
      const item = data.knowledge.find(x => x.id === path.split('/').at(-1));
      item.active = false;
      return route.fulfill(json(null));
    }
    return route.fulfill(json({ error: 'Unexpected request', path, method }, 501));
  });

  return { writes, data };
}

function mutations(state) { return state.writes.filter(x => x.method !== 'GET'); }

test.describe('Settings independent save contract', () => {
  test('changing business name saves only business metadata and never syncs collections', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');
    await page.getByLabel('Nombre del negocio').fill('Mi negocio nuevo');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['PATCH /api/v1/business']);
    expect(state.data.business.name).toBe('Mi negocio nuevo');
  });

  test('changing only public description updates only the business profile', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');
    await page.getByLabel('Descripción pública').fill('Nueva descripción');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['PUT /api/v1/business/profile']);
  });

  test('receptionist can be saved with no services or hours and does not mutate other sections', async ({ page }) => {
    const state = await boot(page, { emptyCollections: true });
    await page.goto('/app/settings?section=receptionist');
    await page.getByLabel('Saludo inicial').fill('Bienvenidos al negocio');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['PUT /api/v1/ai-agent']);
  });

  test('service editing uses item PATCH rather than destructive onboarding replacement', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings?section=services');
    await page.getByLabel('Precio').fill('19990');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['PATCH /api/v1/services/s1']);
    expect(state.data.services[0].price).toBe(19990);
  });

  test('removing a service only deactivates that item', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings?section=services');
    await page.getByRole('button', { name: 'Eliminar servicio Consulta' }).click();
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['DELETE /api/v1/services/s1']);
  });

  test('hours save sends hours only without changing services, agent or knowledge', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings?section=hours');
    await page.getByLabel('Cierre 1').fill('19:00');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['PUT /api/v1/business/hours']);
  });

  test('knowledge update uses item PATCH instead of a full onboarding sync', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings?section=knowledge');
    await page.getByRole('tabpanel', { name: 'Conocimiento' })
      .getByRole('textbox', { name: 'Respuesta', exact: true }).fill('Nueva respuesta');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['PATCH /api/v1/knowledge/k1']);
  });

  test('unsaved edits survive tab changes and saving another section does not discard them', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');
    await page.getByLabel('Descripción pública').fill('Cambio pendiente');
    await page.getByRole('tab', { name: 'Recepcionista IA' }).click();
    await page.getByLabel('Saludo inicial').fill('Nuevo saludo');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual(['PUT /api/v1/ai-agent']);
    await page.getByRole('tab', { name: 'Negocio' }).click();
    await expect(page.getByLabel('Descripción pública')).toHaveValue('Cambio pendiente');
    await expect(page.locator('footer [role="status"]')).toContainText('Cambios sin guardar');
  });

  test('partial business save failure retries only the failed profile mutation', async ({ page }) => {
    const state = await boot(page, { failProfileOnce: true });
    await page.goto('/app/settings');
    await page.getByLabel('Nombre del negocio').fill('Nombre actualizado');
    await page.getByLabel('Descripción pública').fill('Perfil pendiente');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByRole('alert')).toContainText('Perfil temporalmente indisponible');
    await expect(page.locator('footer [role="status"]')).toContainText('Cambios sin guardar');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(x => x.method + ' ' + x.path)).toEqual([
      'PATCH /api/v1/business', 'PUT /api/v1/business/profile', 'PUT /api/v1/business/profile'
    ]);
  });
});
