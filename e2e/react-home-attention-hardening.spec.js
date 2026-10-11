const { test, expect } = require('./home-coverage-fixture');

const json = (body, status = 200) => ({
  status, contentType: 'application/json', body: JSON.stringify(body)
});

const onboarding = {
  businessProfileConfigured: true, servicesConfigured: true,
  scheduleConfigured: true, phoneConfigured: true, readyForCalls: true,
  knowledgeConfigured: true, humanTransferConfigured: true, nextStep: 'DONE'
};
const dashboard = {
  businessName: 'Atención Test', timezone: 'America/Santiago',
  localNow: '2026-10-11T12:00:00-03:00', callsToday: 1,
  callDurationSecondsToday: 60, bookingsToday: 0, newCustomersToday: 0,
  openRequests: 1, unansweredQuestions: 0, callFailuresToday: 0,
  estimatedCallCostTodayUsd: 0, recentCalls: [], recentRequests: [], unanswered: []
};
const analytics = {
  days: 7, timezone: 'America/Santiago', primaryCurrency: 'CLP',
  recepVozRevenue: 0, recepVozOrders: 0, bookingCurrency: 'CLP',
  recepVozBookingRevenue: 0, recepVozPaidBookings: 0
};
const initialAttention = [
  { id: 'req-1', kind: 'REQUEST', operationId: 'op-1', title: 'Solicitud de prueba',
    priority: 'HIGH', status: 'OPEN', createdAt: '2026-10-11T14:00:00Z' },
  { id: 'handoff-1', kind: 'HANDOFF', operationId: 'op-2', title: 'Transferencia de prueba',
    priority: 'URGENT', status: 'OPEN', createdAt: '2026-10-11T13:00:00Z' }
];

async function boot(page, opts = {}) {
  const state = {
    roles: opts.roles || ['BUSINESS_ADMIN'],
    permissions: opts.permissions || ['REQUESTS_READ', 'REQUESTS_MANAGE', 'OPERATIONS_READ'],
    items: structuredClone(opts.attention || initialAttention),
    attentionStatus: 200, requestStatus: 200, acknowledgeStatus: 200, resolveStatus: 200,
    requestGate: null, attentionGate: null,
    reads: 0, mutations: [], apiRequests: []
  };
  await page.addInitScript(() => {
    if (sessionStorage.getItem('__home_attention_test_seeded') !== '1') {
      sessionStorage.setItem('__home_attention_test_seeded', '1');
      sessionStorage.setItem('helvoca_access_token', 'attention-test-token');
    }
  });
  page.on('request', request => {
    if (request.url().includes('/api/v1/')) {
      state.apiRequests.push({ method: request.method(), url: request.url() });
    }
  });
  // Register the fallback first: route specificity is LIFO in Playwright.
  await page.route('**/api/v1/**', route => {
    return route.fulfill(json({ message: 'Unexpected API request in attention sandbox' }, 418));
  });
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'operator@example.test', roles: state.roles, permissions: state.permissions
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json(onboarding)));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json(dashboard)));
  await page.route('**/api/v1/commercial/analytics**', route => route.fulfill(json(analytics)));
  await page.route('**/api/v1/operations/attention', async route => {
    state.reads += 1;
    if (state.attentionGate) await state.attentionGate;
    return route.fulfill(json(state.attentionStatus === 200
      ? state.items : { message: 'Error al consultar la bandeja' }, state.attentionStatus));
  });
  await page.route('**/api/v1/requests/req-1/status', async route => {
    const requestedStatus = route.request().postDataJSON()?.status;
    state.mutations.push({ method: route.request().method(), action: 'request', requestedStatus });
    if (state.requestGate) await state.requestGate;
    if (state.requestStatus !== 200) {
      return route.fulfill(json({ message: 'Error de solicitud simulada' }, state.requestStatus));
    }
    state.items = state.items
      .map(item => item.id === 'req-1' ? { ...item, status: requestedStatus } : item)
      .filter(item => item.status !== 'RESOLVED');
    return route.fulfill(json({ id: 'req-1', status: requestedStatus }));
  });
  await page.route('**/api/v1/handoffs/handoff-1/acknowledge', route => {
    state.mutations.push({ method: route.request().method(), action: 'acknowledge' });
    if (state.acknowledgeStatus !== 200) {
      return route.fulfill(json({ message: 'Error de escalamiento simulado' }, state.acknowledgeStatus));
    }
    state.items = state.items.map(item =>
      item.id === 'handoff-1' ? { ...item, status: 'ACKNOWLEDGED' } : item);
    return route.fulfill(json({ id: 'handoff-1', status: 'ACKNOWLEDGED' }));
  });
  await page.route('**/api/v1/handoffs/handoff-1/resolve', route => {
    state.mutations.push({ method: route.request().method(), action: 'resolve' });
    if (state.resolveStatus !== 200) {
      return route.fulfill(json({ message: 'Error de cierre simulado' }, state.resolveStatus));
    }
    state.items = state.items.filter(item => item.id !== 'handoff-1');
    return route.fulfill(json({ id: 'handoff-1', status: 'RESOLVED' }));
  });
  return state;
}

function assertNoProviders(state) {
  expect(state.apiRequests.filter(({ url }) =>
    /twilio|whatsapp|outbound|payments|dispatch/i.test(url)
  )).toEqual([]);
}
const panel = page => page.getByRole('region', { name: 'Necesita tu atención' });

test.describe('Inicio Parte 3: solicitudes y escalamiento humano', () => {
  test('no se modifica nada al abrir Inicio ni al consultar la bandeja', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app');
    await expect(panel(page)).toContainText('Solicitud de prueba');
    await expect(panel(page)).toContainText('Transferencia de prueba');
    expect(state.mutations).toEqual([]);
    await panel(page).getByRole('button', { name: 'Actualizar' }).click();
    await expect.poll(() => state.reads).toBeGreaterThanOrEqual(2);
    expect(state.mutations).toEqual([]);
    assertNoProviders(state);
  });

  test('solicitud pasa a IN_PROGRESS con un único PATCH autorizado', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel(page)).toContainText('En progreso');
    expect(state.mutations).toEqual([
      { method: 'PATCH', action: 'request', requestedStatus: 'IN_PROGRESS' }
    ]);
    assertNoProviders(state);
  });

  test('Cancelar evita cerrar una solicitud aunque se haya abierto su confirmación', async ({ page }) => {
    const state = await boot(page, { attention: [{
      ...initialAttention[0], status: 'IN_PROGRESS'
    }] });
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Marcar resuelta' }).click();
    await expect(panel(page).getByRole('button', { name: 'Confirmar cierre' })).toBeVisible();
    await panel(page).getByRole('button', { name: 'Cancelar' }).click();
    await expect(panel(page).getByRole('button', { name: 'Confirmar cierre' })).toHaveCount(0);
    await expect(panel(page)).toContainText('Solicitud de prueba');
    expect(state.mutations).toEqual([]);
  });

  test('error 409 al cerrar solicitud no crea estado resuelto y permite reintentar', async ({ page }) => {
    const state = await boot(page, { attention: [{
      ...initialAttention[0], status: 'IN_PROGRESS'
    }] });
    state.requestStatus = 409;
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Marcar resuelta' }).click();
    await panel(page).getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel(page).getByRole('alert')).toContainText('Error de solicitud simulada');
    await expect(panel(page)).toContainText('Solicitud de prueba');
    expect(state.items[0].status).toBe('IN_PROGRESS');
    state.requestStatus = 200;
    await panel(page).getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel(page)).toContainText('Sin intervenciones pendientes');
    expect(state.mutations.filter(entry => entry.action === 'request')).toHaveLength(2);
  });

  test('error 403 de solicitud respeta rol y no modifica estado local', async ({ page }) => {
    const state = await boot(page);
    state.requestStatus = 403;
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel(page).getByRole('alert')).toContainText('Error de solicitud simulada');
    await expect(panel(page)).toContainText('Abierta');
    expect(state.items[0].status).toBe('OPEN');
    expect(state.mutations).toHaveLength(1);
  });

  test('error 503 al tomar escalamiento conserva caso y permite recuperarlo', async ({ page }) => {
    const state = await boot(page);
    state.acknowledgeStatus = 503;
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Tomar caso' }).click();
    await expect(panel(page).getByRole('alert')).toContainText('Error de escalamiento simulado');
    await expect(panel(page)).toContainText('Sin atender');
    state.acknowledgeStatus = 200;
    await panel(page).getByRole('button', { name: 'Tomar caso' }).click();
    await expect(panel(page)).toContainText('Recibido');
    expect(state.mutations.filter(x => x.action === 'acknowledge')).toHaveLength(2);
  });

  test('cancelar confirmación de cierre de handoff no envía POST', async ({ page }) => {
    const state = await boot(page, { attention: [{
      ...initialAttention[1], status: 'ACKNOWLEDGED'
    }] });
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Cerrar escalamiento' }).click();
    await expect(panel(page).getByRole('button', { name: 'Confirmar cierre' })).toBeVisible();
    await panel(page).getByRole('button', { name: 'Cancelar' }).click();
    expect(state.mutations).toEqual([]);
    await expect(panel(page)).toContainText('Transferencia de prueba');
  });

  test('error 503 de cierre de handoff no finge resolución y permite segundo intento', async ({ page }) => {
    const state = await boot(page, { attention: [{
      ...initialAttention[1], status: 'ASSIGNED'
    }] });
    state.resolveStatus = 503;
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Cerrar escalamiento' }).click();
    await panel(page).getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel(page).getByRole('alert')).toContainText('Error de cierre simulado');
    await expect(panel(page)).toContainText('Transferencia de prueba');
    state.resolveStatus = 200;
    await panel(page).getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel(page)).toContainText('Sin intervenciones pendientes');
    expect(state.mutations.filter(x => x.action === 'resolve')).toHaveLength(2);
  });

  test('doble clic en el mismo tick genera una única mutación mientras HTTP está pendiente', async ({ page }) => {
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    const state = await boot(page);
    state.requestGate = pending;
    await page.goto('/app');
    await expect(panel(page).getByRole('button', { name: 'Empezar gestión' })).toBeVisible();
    await page.evaluate(() => {
      const button = [...document.querySelectorAll('button')]
        .find(el => el.textContent.includes('Empezar gestión'));
      if (!button) throw new Error('Missing button');
      button.click();
      button.click();
    });
    await expect.poll(() => state.mutations.length).toBe(1);
    await expect(panel(page).getByRole('button', { name: /Guardando/ })).toBeDisabled();
    release();
    await expect(panel(page)).toContainText('En progreso');
    expect(state.mutations).toHaveLength(1);
  });

  test('mientras se actualizan datos de atención las acciones esperan nueva lectura', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app');
    await expect(panel(page)).toContainText('Solicitud de prueba');
    let release;
    state.attentionGate = new Promise(resolve => { release = resolve; });
    await panel(page).getByRole('button', { name: 'Actualizar' }).click();
    await expect.poll(() => state.reads).toBeGreaterThanOrEqual(2);
    await expect(panel(page).getByRole('button', { name: 'Empezar gestión' })).toBeDisabled();
    expect(state.mutations).toEqual([]);
    release();
    await expect(panel(page).getByRole('button', { name: 'Empezar gestión' })).toBeEnabled();
  });

  test('503 en consulta de pendientes nunca muestra Todo bajo control; reintentar recupera bandeja', async ({ page }) => {
    const state = await boot(page);
    state.attentionStatus = 503;
    await page.goto('/app');
    await expect(panel(page).getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' }))
      .not.toContainText('Todo bajo control');
    state.attentionStatus = 200;
    await panel(page).getByRole('button', { name: 'Reintentar' }).click();
    await expect(panel(page)).toContainText('Solicitud de prueba');
    expect(state.mutations).toEqual([]);
  });

  test('rol STAFF sin autorización no consulta ni puede gestionar la bandeja', async ({ page }) => {
    const state = await boot(page, { roles: ['STAFF'], permissions: ['BOOKINGS_READ'] });
    await page.goto('/app');
    await expect(page.locator('main[data-visual-page="home"] [role="status"]')).toContainText('Tu rol no tiene acceso');
    expect(state.reads).toBe(0);
    expect(state.mutations).toEqual([]);
  });

  test('operador sin REQUESTS_MANAGE no puede actualizar solicitudes', async ({ page }) => {
    const state = await boot(page, { roles: ['OPERATOR'], permissions: ['REQUESTS_READ', 'OPERATIONS_READ'] });
    await page.goto('/app');
    await expect(panel(page)).toContainText('Solicitud de prueba');
    await expect(panel(page).getByRole('button', { name: 'Empezar gestión' })).toHaveCount(0);
    expect(state.mutations.filter(x => x.action === 'request')).toEqual([]);
  });

  test('operador con lectura de solicitudes conserva acceso al handoff permitido por su rol', async ({ page }) => {
    const state = await boot(page, {
      roles: ['OPERATOR'], permissions: ['REQUESTS_READ', 'OPERATIONS_READ']
    });
    await page.goto('/app');
    await expect(panel(page).getByRole('button', { name: 'Empezar gestión' })).toHaveCount(0);
    await panel(page).getByRole('button', { name: 'Tomar caso' }).click();
    await expect(panel(page)).toContainText('Recibido');
    expect(state.mutations).toEqual([{ method: 'POST', action: 'acknowledge' }]);
    assertNoProviders(state);
  });

  test('403 al consultar la bandeja nunca presenta estado todo resuelto', async ({ page }) => {
    const state = await boot(page);
    state.attentionStatus = 403;
    await page.goto('/app');
    await expect(panel(page).getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' }))
      .not.toContainText('Todo bajo control');
    expect(state.mutations).toEqual([]);
  });

  test('respuesta 200 nula no equivale a bandeja vacía ni Todo bajo control', async ({ page }) => {
    const state = await boot(page);
    state.items = null;
    await page.goto('/app');
    await expect(panel(page).getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    await expect(panel(page)).not.toContainText('Sin intervenciones pendientes');
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' }))
      .not.toContainText('Todo bajo control');
    state.items = [];
    await panel(page).getByRole('button', { name: 'Reintentar' }).click();
    await expect(panel(page)).toContainText('Sin intervenciones pendientes');
  });

  test('respuesta 200 con elemento malformado impide mostrar datos incompletos como válidos', async ({ page }) => {
    const state = await boot(page);
    state.items = [{ id: 'bad', kind: 'HANDOFF' }];
    await page.goto('/app');
    await expect(panel(page).getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    await expect(panel(page)).not.toContainText('Sin intervenciones pendientes');
  });


  for (const [label, malformed] of [
    ['null entry', [null]],
    ['scalar entry', [23]],
    ['unknown attention kind', [{ ...initialAttention[0], kind: 'UNKNOWN' }]],
    ['missing attention id', [{ ...initialAttention[0], id: '' }]],
    ['non-string attention status', [{ ...initialAttention[0], status: null }]]
  ]) {
    test('malformed HTTP 200 fails closed for ' + label + ' and recovers after valid retry', async ({ page }) => {
      const state = await boot(page);
      state.items = malformed;
      await page.goto('/app');
      await expect(panel(page).getByRole('alert')).toContainText('No pudimos verificar los pendientes');
      await expect(page.getByRole('region', { name: 'Estado de tu negocio' }))
        .not.toContainText('Todo bajo control');
      expect(state.mutations).toEqual([]);
      state.items = [];
      await panel(page).getByRole('button', { name: 'Reintentar' }).click();
      await expect(panel(page)).toContainText('Sin intervenciones pendientes');
      assertNoProviders(state);
    });
  }

  test('unsupported attention status is safely read-only, never sent to mutation API', async ({ page }) => {
    const state = await boot(page, { attention: [
      { ...initialAttention[0], status: 'FUTURE_STATUS' },
      { ...initialAttention[1], status: 'FUTURE_STATUS' }
    ] });
    await page.goto('/app');
    await expect(panel(page)).toContainText('Solicitud de prueba');
    await expect(panel(page)).toContainText('Transferencia de prueba');
    await expect(panel(page).getByRole('button', { name: 'Empezar gestión' })).toHaveCount(0);
    await expect(panel(page).getByRole('button', { name: 'Marcar resuelta' })).toHaveCount(0);
    await expect(panel(page).getByRole('button', { name: 'Tomar caso' })).toHaveCount(0);
    await expect(panel(page).getByRole('button', { name: 'Cerrar escalamiento' })).toHaveCount(0);
    expect(state.mutations).toEqual([]);
  });

  test('malformed case title or timestamp never crashes Home or guesses a date', async ({ page }) => {
    const state = await boot(page, { attention: [{
      ...initialAttention[0], title: '', createdAt: 'this-is-not-a-date', priority: 'LOW'
    }] });
    await page.goto('/app');
    await expect(panel(page)).toContainText('Caso sin título');
    await expect(panel(page)).toContainText('Fecha no disponible');
    await expect(panel(page).getByRole('button', { name: 'Empezar gestión' })).toBeVisible();
    expect(state.mutations).toEqual([]);
  });

  test('100 visible cases are presented as recent sample, not all-time workload', async ({ page }) => {
    const state = await boot(page, { attention: Array.from({ length: 100 }, (_, i) => ({
      ...initialAttention[0], id: 'request-' + i, operationId: 'operation-' + i,
      title: 'Caso de muestra ' + i, status: 'OPEN'
    })) });
    await page.goto('/app');
    await expect(panel(page)).toContainText('100 casos recientes');
    await expect(panel(page)).toContainText('hasta 100 casos recientes');
    await expect(panel(page).locator('article[data-kind="REQUEST"]')).toHaveCount(100);
    expect(state.mutations).toEqual([]);
  });

  test('confirmation without title announces a safe case fallback to assistive technology', async ({ page }) => {
    const state = await boot(page, { attention: [{
      ...initialAttention[0], title: '', status: 'IN_PROGRESS'
    }] });
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Marcar resuelta' }).click();
    await expect(panel(page).getByRole('group', { name: 'Confirmación de cierre: Caso sin título' })).toBeVisible();
    await panel(page).getByRole('button', { name: 'Cancelar' }).click();
    expect(state.mutations).toEqual([]);
  });

  test('non-Error network rejection is reported without marking a request complete', async ({ page }) => {
    const state = await boot(page);
    await page.addInitScript(() => {
      const originalFetch = window.fetch.bind(window);
      window.fetch = (resource, init) => {
        const url = typeof resource === 'string' ? resource : resource.url;
        if (new URL(url, location.origin).pathname === '/api/v1/requests/req-1/status') {
          return Promise.reject('raw mocked network rejection');
        }
        return originalFetch(resource, init);
      };
    });
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel(page).getByRole('alert')).toContainText('No pudimos actualizar el caso');
    await expect(panel(page)).toContainText('Solicitud de prueba');
    expect(state.mutations).toEqual([]);
    assertNoProviders(state);
  });

  test('doble clic en confirmación de cierre genera exactamente un PATCH', async ({ page }) => {
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    const state = await boot(page, { attention: [{
      ...initialAttention[0], status: 'IN_PROGRESS'
    }] });
    state.requestGate = pending;
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Marcar resuelta' }).click();
    await page.evaluate(() => {
      const button = [...document.querySelectorAll('button')]
        .find(el => el.textContent.trim() === 'Confirmar cierre');
      if (!button) throw new Error('Missing confirmation');
      button.click();
      button.click();
    });
    await expect.poll(() => state.mutations.length).toBe(1);
    release();
    await expect(panel(page)).toContainText('Sin intervenciones pendientes');
    expect(state.mutations).toEqual([{
      method: 'PATCH', action: 'request', requestedStatus: 'RESOLVED'
    }]);
  });

  test('HTTP 401 en PATCH limpia sesión y no marca caso como gestionado', async ({ page }) => {
    const state = await boot(page);
    state.requestStatus = 401;
    await page.goto('/app');
    await panel(page).getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
    expect(state.items[0].status).toBe('OPEN');
    expect(state.mutations).toHaveLength(1);
    assertNoProviders(state);
  });
});
