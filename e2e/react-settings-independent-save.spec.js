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
  test('sector guidance is advisory and veterinary preset saves only with explicit approval', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');

    const sector = page.getByLabel('Rubro');
    const advisory = [
      ['store', 'Revisa productos, precios, disponibilidad, horarios, entregas y políticas de cambios. No confirmes stock sin verificar.'],
      ['hardware_store', 'Prioriza catálogo, stock, cotizaciones, pedidos y despacho. Revisa medidas, cantidades y disponibilidad antes de cerrar una venta.'],
      ['services', 'Revisa servicios, duraciones, tarifas, horarios, zona de cobertura y condiciones de reserva.'],
      ['restaurant', 'Revisa carta, precios, horarios, preguntas frecuentes, pedidos y reservas. No confirmes productos agotados como disponibles.'],
      ['clinic', 'Revisa especialidades, profesionales, duración de consultas, horarios, precios y reservas. No inventes disponibilidad ni indicaciones clínicas.'],
      ['veterinary', 'Revisa especies atendidas, servicios, profesionales, horarios, urgencias, precios y citas. No inventes disponibilidad ni recomendaciones veterinarias.'],
      ['salon', 'Revisa servicios, duraciones, precios, profesionales y disponibilidad para reservas.'],
      ['workshop', 'Revisa tipos de reparación, presupuestos, horarios, plazos y requisitos para recibir equipos.'],
      ['hospitality', 'Revisa tipos de alojamiento, tarifas, horarios de ingreso y salida y políticas de reserva.'],
      ['professional', 'Revisa servicios profesionales, tarifas, horarios, canales de contacto y modalidad de reserva.']
    ];

    for (const [preset, text] of advisory) {
      await sector.selectOption(preset);
      await expect(page.getByText('Sugerencia para este rubro')).toBeVisible();
      await expect(page.getByText(text, { exact: true })).toBeVisible();
      expect(mutations(state)).toEqual([]);
    }

    // A sector is guidance only: it never populates price, stock, hours or channels.
    expect(state.data.profile.presetKey).toBe('services');
    expect(state.data.services).toHaveLength(1);
    expect(state.data.hours).toHaveLength(1);

    await sector.selectOption('veterinary');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(item => item.method + ' ' + item.path))
      .toEqual(['PUT /api/v1/business/profile']);
    expect(state.data.profile.presetKey).toBe('veterinary');
    expect(state.data.profile.sellsServices).toBe(true);
    expect(state.data.profile.usesReservations).toBe(true);

    await page.reload();
    await expect(page.getByLabel('Rubro')).toHaveValue('veterinary');
    await expect(page.getByText(advisory[5][1], { exact: true })).toBeVisible();

    await page.getByLabel('Rubro').selectOption('');
    await expect(page.getByText('Sugerencia para este rubro')).toHaveCount(0);
    expect(mutations(state)).toHaveLength(1);
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(state.data.profile.presetKey).toBeNull();
    expect(mutations(state).map(item => item.method + ' ' + item.path))
      .toEqual(['PUT /api/v1/business/profile', 'PUT /api/v1/business/profile']);
  });

  test('an operator sees persisted veterinary guidance without editing or side effects', async ({ page }) => {
    const state = await boot(page, { roles: ['OPERATOR'] });
    state.data.profile.presetKey = 'veterinary';
    await page.goto('/app/settings');

    await expect(page.getByLabel('Rubro')).toHaveValue('veterinary');
    await expect(page.getByLabel('Rubro')).toBeDisabled();
    await expect(page.getByText('Sugerencia para este rubro')).toBeVisible();
    await expect(page.getByText(
      'Revisa especies atendidas, servicios, profesionales, horarios, urgencias, precios y citas. No inventes disponibilidad ni recomendaciones veterinarias.',
      { exact: true }
    )).toBeVisible();
    await expect(page.getByRole('button', { name: 'Guardar cambios' })).toHaveCount(0);
    expect(mutations(state)).toEqual([]);
  });

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

  test('twelve visual sector cards are accessible, keyboard navigable and never save before approval', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');

    const picker = page.getByRole('group', { name: 'Tipos de negocio' });
    const labels = [
      'Clínica', 'Dentista', 'Veterinaria', 'Peluquería', 'Restaurante',
      'Ferretería', 'Tienda', 'Taller mecánico', 'Estudio jurídico',
      'Inmobiliaria', 'Gimnasio', 'Hotel', 'Otro negocio'
    ];
    await expect(picker.getByRole('button')).toHaveCount(13);
    for (const label of labels) await expect(picker.getByRole('button', { name: label, exact: true })).toBeVisible();

    const restaurant = picker.getByRole('button', { name: 'Restaurante', exact: true });
    const hardware = picker.getByRole('button', { name: 'Ferretería', exact: true });
    await restaurant.hover();
    await expect(restaurant).not.toHaveCSS('box-shadow', 'none');
    expect(mutations(state)).toEqual([]);

    await restaurant.focus();
    await expect(restaurant).not.toHaveCSS('box-shadow', 'none');
    await page.keyboard.press('ArrowRight');
    await expect(hardware).toBeFocused();
    await expect(hardware).toHaveAttribute('aria-pressed', 'false');
    expect(mutations(state)).toEqual([]);

    await page.keyboard.press('Enter');
    await expect(hardware).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByLabel('Rubro')).toHaveValue('hardware_store');
    expect(mutations(state)).toEqual([]);

    const dental = picker.getByRole('button', { name: 'Dentista', exact: true });
    await dental.click();
    await expect(dental).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByLabel('Rubro')).toHaveValue('dental');
    expect(mutations(state)).toEqual([]);

    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    expect(mutations(state).map(item => item.method + ' ' + item.path)).toEqual(['PUT /api/v1/business/profile']);
    expect(state.data.profile.presetKey).toBe('dental');

    await page.reload();
    await expect(page.getByRole('group', { name: 'Tipos de negocio' })
      .getByRole('button', { name: 'Dentista', exact: true })).toHaveAttribute('aria-pressed', 'true');
  });

  test('sector cards are view-only for operators and do not trigger mutations', async ({ page }) => {
    const state = await boot(page, { roles: ['OPERATOR'] });
    await page.goto('/app/settings');
    const picker = page.getByRole('group', { name: 'Tipos de negocio' });
    await expect(picker.getByRole('button')).toHaveCount(13);
    await expect(picker.getByRole('button', { name: 'Dentista', exact: true })).toBeDisabled();
    expect(mutations(state)).toEqual([]);
  });

  test('all twelve business guides ask tailored questions without creating fictitious data', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');
    const picker = page.getByRole('group', { name: 'Tipos de negocio' });
    const guide = page.getByRole('region', { name: 'Preparación del negocio' });
    const cases = [
      ['Clínica', 'especialidades y profesionales'],
      ['Dentista', 'tratamientos y odontólogos'],
      ['Veterinaria', 'especies atendidas'],
      ['Peluquería', 'estilistas y servicios'],
      ['Restaurante', 'menú y alérgenos'],
      ['Ferretería', 'catálogo y existencias'],
      ['Tienda', 'tallas, variantes'],
      ['Taller mecánico', 'vehículos admitidos'],
      ['Estudio jurídico', 'áreas legales'],
      ['Inmobiliaria', 'propiedades vigentes'],
      ['Gimnasio', 'planes y cupos'],
      ['Hotel', 'habitaciones y ocupación']
    ];
    for (const [label, expected] of cases) {
      await picker.getByRole('button', { name: label, exact: true }).click();
      await expect(guide).toContainText(expected);
      await expect(guide).toContainText('Si falta información');
      await expect(guide.getByRole('button', { name: 'Ir a Servicios' })).toBeVisible();
      await expect(guide.getByRole('button', { name: 'Ir a Horarios' })).toBeVisible();
      await expect(guide.getByRole('button', { name: 'Ir a Conocimiento' })).toBeVisible();
    }
    expect(mutations(state)).toEqual([]);
    expect(state.data.services).toHaveLength(1);
    expect(state.data.knowledge).toHaveLength(1);
    await picker.getByRole('button', { name: 'Otro negocio', exact: true }).click();
    await expect(guide).toContainText('actividad principal');
    expect(mutations(state)).toEqual([]);
  });

  test('guided setup navigation preserves unsaved industry choice and data without auto-saving', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');
    await page.getByRole('group', { name: 'Tipos de negocio' })
      .getByRole('button', { name: 'Restaurante', exact: true }).click();
    const guide = page.getByRole('region', { name: 'Preparación del negocio' });
    await guide.getByRole('button', { name: 'Ir a Conocimiento' }).click();
    await expect(page.getByRole('tabpanel', { name: 'Conocimiento' })).toBeVisible();
    expect(mutations(state)).toEqual([]);
    await page.getByRole('tab', { name: 'Negocio' }).click();
    await expect(page.getByLabel('Rubro')).toHaveValue('restaurant');
    await expect(page.locator('footer [role="status"]')).toContainText('Cambios sin guardar');
    await guide.getByRole('button', { name: 'Ir a Horarios' }).click();
    await expect(page.getByRole('tabpanel', { name: 'Horarios' })).toBeVisible();
    expect(mutations(state)).toEqual([]);
  });

  test('sector evidence uses only saved data and does not certify live calls', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app/settings');

    const evidence = page.getByRole('region', { name: 'Datos guardados para la atención' });
    await expect(evidence).toContainText('Rubro guardado');
    await expect(evidence).toContainText('1 servicio registrado');
    await expect(evidence).toContainText('1 horario registrado');
    await expect(evidence).toContainText('1 respuesta registrada');
    await expect(evidence).toContainText('no certifican llamadas reales');

    await page.getByRole('group', { name: 'Tipos de negocio' })
      .getByRole('button', { name: 'Hotel', exact: true }).click();
    await expect(evidence).toContainText('Rubro sin guardar');
    await expect(evidence).toContainText('1 servicio registrado');
    expect(mutations(state)).toEqual([]);

    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.locator('footer [role="status"]')).toContainText('Guardado');
    await expect(evidence).toContainText('Rubro guardado');
    expect(mutations(state).map(item => item.method + ' ' + item.path))
      .toEqual(['PUT /api/v1/business/profile']);
    await page.reload();
    await expect(evidence).toContainText('Rubro guardado');
  });

  test('empty sector evidence never reports fabricated services, hours or answers', async ({ page }) => {
    const state = await boot(page, { emptyCollections: true });
    await page.goto('/app/settings');

    const evidence = page.getByRole('region', { name: 'Datos guardados para la atención' });
    await expect(evidence).toContainText('0 servicios registrados');
    await expect(evidence).toContainText('0 horarios registrados');
    await expect(evidence).toContainText('0 respuestas registradas');
    await expect(evidence).toContainText('no certifican llamadas reales');
    expect(mutations(state)).toEqual([]);
  });
});
