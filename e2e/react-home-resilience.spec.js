const { test, expect } = require('@playwright/test');

// Part 2/8: all HTTP replies are local Playwright fixtures. No real services are called.
const json = (body, status = 200) => ({
  status, contentType: 'application/json', body: JSON.stringify(body)
});

const ready = {
  businessProfileConfigured: true, servicesConfigured: true,
  scheduleConfigured: true, phoneConfigured: true, readyForCalls: true,
  knowledgeConfigured: true, humanTransferConfigured: false, nextStep: 'DONE'
};
const incomplete = {
  ...ready, servicesConfigured: false, scheduleConfigured: false,
  phoneConfigured: false, readyForCalls: false, nextStep: 'ADD_SERVICE'
};
const dashboard = {
  businessName: 'Local de prueba', timezone: 'America/Santiago',
  localNow: '2026-10-11T12:00:00-03:00', callsToday: 3,
  callDurationSecondsToday: 120, bookingsToday: 2,
  newCustomersToday: 0, openRequests: 0, unansweredQuestions: 0,
  callFailuresToday: 0, estimatedCallCostTodayUsd: 0,
  recentCalls: [], recentRequests: [], unanswered: []
};
const analytics = {
  days: 7, timezone: 'America/Santiago', primaryCurrency: 'CLP',
  recepVozRevenue: 0, recepVozOrders: 0, bookingCurrency: 'CLP',
  recepVozBookingRevenue: 0, recepVozPaidBookings: 0
};

async function boot(page, opts = {}) {
  const state = {
    authStatus: opts.authStatus ?? 200,
    onboardingStatus: opts.onboardingStatus ?? 200,
    dashboardStatus: opts.dashboardStatus ?? 200,
    dashboardNetworkError: opts.dashboardNetworkError ?? false,
    onboarding: Object.prototype.hasOwnProperty.call(opts, 'onboarding') ? opts.onboarding : ready,
    authPayload: Object.prototype.hasOwnProperty.call(opts, 'authPayload') ? opts.authPayload :
      { email: 'owner@example.test', roles: ['BUSINESS_ADMIN'], permissions: ['REQUESTS_READ'] },
    counts: { auth: 0, onboarding: 0, dashboard: 0, analytics: 0, attention: 0 },
    methods: [],
    dashboardGate: opts.dashboardGate ?? null,
    onboardingGate: opts.onboardingGate ?? null
  };

  await page.addInitScript(token => {
    // The seed flag survives redirects, so a 401 never resurrects a cleared session.
    if (sessionStorage.getItem('__home_part2_seeded') === '1') return;
    sessionStorage.setItem('__home_part2_seeded', '1');
    if (token) sessionStorage.setItem('helvoca_access_token', token);
  }, opts.token === null ? null : 'part2-mock-token');

  page.on('request', request => {
    if (request.url().includes('/api/v1/')) {
      state.methods.push({ method: request.method(), url: request.url() });
    }
  });
  // Unexpected external/provider calls are failed locally, not forwarded.
  await page.route('**/api/v1/**', route => {
    const url = route.request().url();
    if (/twilio|whatsapp|payment|outbound|dispatch/i.test(url)) {
      return route.abort('blockedbyclient');
    }
    return route.fulfill(json({ message: 'Unexpected request in Home Part 2 mock' }, 418));
  });

  await page.route('**/api/v1/auth/me', route => {
    state.counts.auth += 1;
    return route.fulfill(json(state.authStatus === 200
      ? state.authPayload
      : { message: 'unavailable' }, state.authStatus));
  });
  await page.route('**/api/v1/onboarding/status', async route => {
    state.counts.onboarding += 1;
    if (state.onboardingGate) await state.onboardingGate;
    return route.fulfill(json(state.onboardingStatus === 200
      ? state.onboarding : { message: 'unavailable' }, state.onboardingStatus));
  });
  await page.route('**/api/v1/operations/dashboard', async route => {
    state.counts.dashboard += 1;
    if (state.dashboardGate) await state.dashboardGate;
    if (state.dashboardNetworkError) return route.abort('failed');
    return route.fulfill(json(state.dashboardStatus === 200
      ? dashboard : { message: 'unavailable' }, state.dashboardStatus));
  });
  await page.route('**/api/v1/commercial/analytics**', route => {
    state.counts.analytics += 1;
    return route.fulfill(json(analytics));
  });
  await page.route('**/api/v1/operations/attention', route => {
    state.counts.attention += 1;
    return route.fulfill(json([]));
  });
  return state;
}

function assertReadOnly(state) {
  const mutating = state.methods.filter(item => !['GET', 'HEAD', 'OPTIONS'].includes(item.method));
  expect(mutating).toEqual([]);
  expect(state.methods.filter(item => /twilio|whatsapp|payment|outbound|dispatch/i.test(item.url))).toEqual([]);
}

test.describe('Inicio Parte 2: identidad, onboarding, carga y recuperación', () => {
  test('sin sesión va al acceso y no consulta ningún dato protegido', async ({ page }) => {
    const state = await boot(page, { token: null });
    await page.goto('/app');
    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    expect(state.counts.auth).toBe(0);
    expect(state.counts.onboarding).toBe(0);
    expect(state.counts.dashboard).toBe(0);
    assertReadOnly(state);
  });

  test('403 de identidad informa acceso restringido sin consultar datos privados', async ({ page }) => {
    const state = await boot(page, { authStatus: 403 });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText(/permiso|acceso/i);
    await expect(page.getByRole('button', { name: 'Reintentar acceso' })).toBeVisible();
    expect(state.counts.dashboard).toBe(0);
    expect(state.counts.onboarding).toBe(0);
    expect(state.counts.attention).toBe(0);
    assertReadOnly(state);
  });

  test('503 en identidad no se queda cargando y permite reintentar sin duplicar efectos', async ({ page }) => {
    const state = await boot(page, { authStatus: 503 });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos verificar tu acceso');
    await expect(page.getByRole('status')).toHaveCount(0);
    expect(state.counts.dashboard).toBe(0);
    state.authStatus = 200;
    await page.getByRole('button', { name: 'Reintentar acceso' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('3');
    assertReadOnly(state);
  });

  test('respuesta 200 sin roles verificables no habilita consultas ni métricas', async ({ page }) => {
    const state = await boot(page, { authPayload: { email: 'owner@example.test', roles: null } });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos verificar tu acceso');
    expect(state.counts.onboarding).toBe(0);
    expect(state.counts.dashboard).toBe(0);
    expect(state.counts.attention).toBe(0);
    assertReadOnly(state);
  });

  test('respuesta 200 nula en onboarding no muestra una pantalla vacía ni inventa actividad', async ({ page }) => {
    const state = await boot(page, { onboarding: null });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos comprobar la configuración');
    expect(state.counts.dashboard).toBe(0);
    state.onboarding = ready;
    await page.getByRole('button', { name: 'Reintentar configuración' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    assertReadOnly(state);
  });

  test('error 503 del onboarding no inventa un negocio listo y se recupera a pasos pendientes', async ({ page }) => {
    const state = await boot(page, { onboardingStatus: 503, onboarding: incomplete });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos comprobar la configuración');
    await expect(page.getByRole('status')).toHaveCount(0);
    expect(state.counts.dashboard).toBe(0);
    expect(state.counts.analytics).toBe(0);
    state.onboardingStatus = 200;
    await page.getByRole('button', { name: 'Reintentar configuración' }).click();
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25');
    await expect(card.getByRole('link', { name: /Continuar configuración/ }))
      .toHaveAttribute('href', '/app/settings?section=services');
    expect(state.counts.dashboard).toBe(0);
    assertReadOnly(state);
  });

  test('403 de onboarding bloquea carga operativa hasta poder comprobar configuración', async ({ page }) => {
    const state = await boot(page, { onboardingStatus: 403 });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos comprobar la configuración');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    expect(state.counts.dashboard).toBe(0);
    expect(state.counts.attention).toBe(0);
    assertReadOnly(state);
  });

  test('onboarding pendiente muestra carga, después pasos incompletos y ninguna métrica', async ({ page }) => {
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const state = await boot(page, { onboardingGate: gate, onboarding: incomplete });
    await page.goto('/app');
    await expect(page.getByRole('status')).toContainText('Cargando tu negocio');
    expect(state.counts.dashboard).toBe(0);
    release();
    await expect(page.getByRole('region', { name: 'Configura tu negocio' })).toBeVisible();
    expect(state.counts.dashboard).toBe(0);
    assertReadOnly(state);
  });

  test('configuración lista solo carga operaciones después de comprobar el permiso', async ({ page }) => {
    const state = await boot(page);
    await page.goto('/app');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('3');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('2');
    expect(state.counts.auth).toBeGreaterThan(0);
    expect(state.counts.onboarding).toBeGreaterThan(0);
    expect(state.counts.dashboard).toBeGreaterThan(0);
    assertReadOnly(state);
  });

  test('503 de dashboard es visible y se recupera sin recargar todo el navegador', async ({ page }) => {
    const state = await boot(page, { dashboardStatus: 503 });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos cargar el resumen operativo');
    await expect(page.getByRole('status')).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    state.dashboardStatus = 200;
    await page.getByRole('button', { name: 'Reintentar resumen' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('3');
    assertReadOnly(state);
  });

  test('corte de red del dashboard muestra fallo explícito y se recupera', async ({ page }) => {
    const state = await boot(page, { dashboardNetworkError: true });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos cargar el resumen operativo');
    expect(state.counts.dashboard).toBeGreaterThan(0);
    state.dashboardNetworkError = false;
    await page.getByRole('button', { name: 'Reintentar resumen' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('3');
    assertReadOnly(state);
  });

  test('403 del dashboard no muestra métricas falsas ni permite mutaciones', async ({ page }) => {
    const state = await boot(page, { dashboardStatus: 403 });
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos cargar el resumen operativo');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    assertReadOnly(state);
  });

  test('401 del onboarding borra la sesión y vuelve al login sin consultar dashboard', async ({ page }) => {
    const state = await boot(page, { onboardingStatus: 401 });
    await page.goto('/app');
    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    expect(state.counts.dashboard).toBe(0);
    expect(state.counts.attention).toBe(0);
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
    assertReadOnly(state);
  });

  test('401 del dashboard borra la sesión y no muestra datos antiguos', async ({ page }) => {
    const state = await boot(page, { dashboardStatus: 401 });
    await page.goto('/app');
    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
    assertReadOnly(state);
  });

  const steps = [
    ['CONFIGURE_BUSINESS', 'business', 0],
    ['ADD_SERVICE', 'services', 25],
    ['CONFIGURE_HOURS', 'hours', 50],
    ['CONNECT_PHONE_NUMBER', 'receptionist', 75],
    ['BUSINESS_PROFILE', 'business', 0],
    ['SERVICES', 'services', 25],
    ['SCHEDULE', 'hours', 50],
    ['PHONE', 'receptionist', 75]
  ];
  for (const [nextStep, section, percent] of steps) {
    test('onboarding enlaza ' + nextStep + ' a Settings sin métricas prematuras', async ({ page }) => {
      const flags = {
        businessProfileConfigured: percent >= 25,
        servicesConfigured: percent >= 50,
        scheduleConfigured: percent >= 75,
        phoneConfigured: false
      };
      const state = await boot(page, {
        onboarding: { ...incomplete, ...flags, nextStep }
      });
      await page.goto('/app');
      const card = page.getByRole('region', { name: 'Configura tu negocio' });
      await expect(card.getByRole('progressbar')).toHaveAttribute('aria-valuenow', String(percent));
      await expect(card.getByRole('link', { name: /Continuar configuración/ }))
        .toHaveAttribute('href', '/app/settings?section=' + section);
      expect(state.counts.dashboard).toBe(0);
      assertReadOnly(state);
    });
  }
});
