const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

const READY_ONBOARDING = {
  businessProfileConfigured: true,
  servicesConfigured: true,
  scheduleConfigured: true,
  knowledgeConfigured: true,
  humanTransferConfigured: false,
  phoneConfigured: true,
  readyForCalls: true,
  nextStep: 'DONE'
};

const INCOMPLETE_ONBOARDING = {
  businessProfileConfigured: true,
  servicesConfigured: false,
  scheduleConfigured: false,
  knowledgeConfigured: false,
  humanTransferConfigured: false,
  phoneConfigured: false,
  readyForCalls: false,
  nextStep: 'ADD_SERVICE'
};

const READY_OPERATIONS = {
  businessName: 'Clínica Norte',
  timezone: 'America/Santiago',
  localNow: '2026-10-02T13:30:00-03:00',
  callsToday: 6,
  callDurationSecondsToday: 920,
  bookingsToday: 4,
  newCustomersToday: 2,
  openRequests: 2,
  unansweredQuestions: 1,
  callFailuresToday: 1,
  estimatedCallCostTodayUsd: 1.82,
  recentCalls: [
    {
      id: 'call-1',
      callerNumber: '+56911112222',
      status: 'COMPLETED',
      resolution: 'BOOKING_CREATED',
      startedAt: '2026-10-02T15:20:00Z',
      durationSeconds: 180,
      estimatedTotalCostUsd: 0.42
    },
    {
      id: 'call-2',
      callerNumber: '+56933334444',
      status: 'FAILED',
      resolution: null,
      startedAt: '2026-10-02T14:55:00Z',
      durationSeconds: 21,
      estimatedTotalCostUsd: 0.08
    }
  ],
  recentRequests: [
    {
      id: 'req-1',
      type: 'CALLBACK',
      title: 'Cliente pidió devolución de llamada',
      priority: 'HIGH',
      status: 'OPEN',
      createdAt: '2026-10-02T15:10:00Z'
    },
    {
      id: 'req-2',
      type: 'FOLLOW_UP',
      title: 'Confirmar disponibilidad especial',
      priority: 'MEDIUM',
      status: 'IN_PROGRESS',
      createdAt: '2026-10-02T14:40:00Z'
    }
  ],
  unanswered: [
    {
      id: 'question-1',
      question: '¿Atienden urgencias los domingos?',
      occurrences: 3,
      lastSeenAt: '2026-10-02T15:05:00Z'
    }
  ]
};

const DEFAULT_ATTENTION = [
  {
    kind: 'REQUEST', id: 'req-1', operationId: 'operation-1',
    title: 'Cliente pidió devolución de llamada', priority: 'HIGH',
    status: 'OPEN', createdAt: '2026-10-02T15:10:00Z'
  },
  {
    kind: 'HANDOFF', id: 'handoff-1', operationId: 'operation-2',
    title: 'Reclamo de cliente pendiente', priority: 'URGENT',
    status: 'OPEN', createdAt: '2026-10-02T15:05:00Z'
  }
];

const EMPTY_OPERATIONS = {
  businessName: 'Clínica Norte',
  timezone: 'America/Santiago',
  localNow: '2026-10-02T13:30:00-03:00',
  callsToday: 0,
  callDurationSecondsToday: 0,
  bookingsToday: 0,
  newCustomersToday: 0,
  openRequests: 0,
  unansweredQuestions: 0,
  callFailuresToday: 0,
  estimatedCallCostTodayUsd: 0,
  recentCalls: [],
  recentRequests: [],
  unanswered: []
};

const READY_ANALYTICS = {
  days: 7,
  timezone: 'America/Santiago',
  primaryCurrency: 'CLP',
  totalRevenue: 189900,
  paidOrders: 5,
  unitsSold: 7,
  averageTicket: 37980,
  revenueChangePercent: 12.5,
  currencyTotals: [{ currency: 'CLP', amount: 189900 }],
  salesOverTime: [
    { date: '2026-09-26', paidOrders: 1, revenue: 29900 },
    { date: '2026-09-27', paidOrders: 0, revenue: 0 },
    { date: '2026-09-28', paidOrders: 1, revenue: 35000 },
    { date: '2026-09-29', paidOrders: 1, revenue: 42000 },
    { date: '2026-09-30', paidOrders: 0, revenue: 0 },
    { date: '2026-10-01', paidOrders: 1, revenue: 33000 },
    { date: '2026-10-02', paidOrders: 1, revenue: 50000 }
  ],
  topProducts: [],
  channels: [
    { channel: 'VOICE', orders: 2, sharePercent: 40 },
    { channel: 'WHATSAPP', orders: 2, sharePercent: 40 },
    { channel: 'ADMIN', orders: 1, sharePercent: 20 }
  ],
  peakWeekday: 'THURSDAY',
  peakHour: 13,
  recepVozOrders: 4,
  recepVozRevenue: 159900,
  bookingCurrency: 'CLP',
  paidBookings: 3,
  bookingRevenue: 75000,
  providerVerifiedBookingRevenue: 50000,
  manualRecordedBookingRevenue: 25000,
  recepVozPaidBookings: 2,
  recepVozBookingRevenue: 50000,
  bookingCurrencyTotals: [{ currency: 'CLP', amount: 75000 }],
  insights: []
};

const EMPTY_ANALYTICS = {
  ...READY_ANALYTICS,
  totalRevenue: 0,
  paidOrders: 0,
  unitsSold: 0,
  averageTicket: 0,
  revenueChangePercent: null,
  currencyTotals: [],
  salesOverTime: [],
  channels: [],
  recepVozOrders: 0,
  recepVozRevenue: 0,
  paidBookings: 0,
  bookingRevenue: 0,
  providerVerifiedBookingRevenue: 0,
  manualRecordedBookingRevenue: 0,
  recepVozPaidBookings: 0,
  recepVozBookingRevenue: 0,
  bookingCurrencyTotals: []
};

async function bootHome(page, options = {}) {
  const roles = options.roles || ['BUSINESS_ADMIN'];
  const permissions = options.permissions;
  const requests = [];
  let onboardingRequests = 0;
  let attentionReads = 0;
  let attentionMutations = 0;
  let attentionItems = [...(options.attention || (options.operations === EMPTY_OPERATIONS ? [] : DEFAULT_ATTENTION))];

  await page.addInitScript(
    token => {
      const seedKey = '__react_home_e2e_session_seeded';
      if (sessionStorage.getItem(seedKey) !== '1') {
        sessionStorage.setItem(seedKey, '1');
        sessionStorage.setItem('helvoca_access_token', token);
      }
    },
    options.token || 'react-home-e2e'
  );

  page.on('request', request => {
    requests.push({
      method: request.method(),
      url: request.url(),
      postData: request.postData()
    });
  });

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) {
      return route.fulfill(json({ message: 'expired' }, 401));
    }
    return route.fulfill(json({
      email: roles.includes('OPERATOR') ? 'operator@negocio.cl' : 'owner@negocio.cl',
      roles,
      ...(permissions ? { permissions } : {})
    }));
  });

  await page.route('**/api/v1/onboarding/status', route => {
    onboardingRequests += 1;
    return route.fulfill(json(options.onboarding || READY_ONBOARDING));
  });

  await page.route('**/api/v1/operations/attention', async route => {
    attentionReads += 1;
    if (options.attentionError) {
      return route.fulfill(json({ message: 'attention unavailable' }, options.attentionError));
    }
    return route.fulfill(json(attentionItems));
  });

  await page.route('**/api/v1/requests/req-1/status', route => {
    attentionMutations += 1;
    const status = route.request().postDataJSON()?.status;
    attentionItems = attentionItems
      .map(item => item.id === 'req-1' ? { ...item, status } : item)
      .filter(item => item.status !== 'RESOLVED' && item.status !== 'CANCELLED');
    return route.fulfill(json({ id: 'req-1', status }));
  });
  await page.route('**/api/v1/handoffs/handoff-1/acknowledge', route => {
    attentionMutations += 1;
    attentionItems = attentionItems.map(item => item.id === 'handoff-1'
      ? { ...item, status: 'ACKNOWLEDGED' } : item);
    return route.fulfill(json({ id: 'handoff-1', status: 'ACKNOWLEDGED' }));
  });
  await page.route('**/api/v1/handoffs/handoff-1/resolve', route => {
    attentionMutations += 1;
    attentionItems = attentionItems.filter(item => item.id !== 'handoff-1');
    return route.fulfill(json({ id: 'handoff-1', status: 'RESOLVED' }));
  });
  await page.route('**/api/v1/requests', route => route.fulfill(json([
    { id: 'req-1', requestType: 'CALLBACK', title: 'Cliente pidió devolución de llamada',
      status: 'OPEN', priority: 'HIGH', createdAt: '2026-10-02T15:10:00Z' }
  ])));

  await page.route('**/api/v1/operations/dashboard', async route => {
    if (options.operationsGate) await options.operationsGate;
    if (options.operationsError) {
      return route.fulfill(json({ message: 'operations unavailable' }, options.operationsError));
    }
    return route.fulfill(json(options.operations || READY_OPERATIONS));
  });

  await page.route('**/api/v1/commercial/analytics**', route => {
    if (options.analyticsError) {
      return route.fulfill(json({ message: 'analytics unavailable' }, options.analyticsError));
    }
    return route.fulfill(json(options.analytics || READY_ANALYTICS));
  });

  // Any accidental legacy Home dependency should not silently make the new cockpit pass.
  await page.route('**/api/v1/operations/pilot-readiness', route =>
    route.fulfill(json({ message: 'pilot readiness does not belong to Home' }, 418))
  );
  await page.route('**/api/v1/operations/pilot-metrics', route =>
    route.fulfill(json({ message: 'pilot metrics do not belong to Home' }, 418))
  );
  await page.route('**/api/v1/operations/readiness', route =>
    route.fulfill(json({ message: 'readiness does not belong to Home' }, 418))
  );

  return {
    requests,
    onboardingRequests: () => onboardingRequests,
    attentionReads: () => attentionReads,
    attentionMutations: () => attentionMutations
  };
}

async function expectNoHorizontalOverflow(page) {
  const overflow = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    body: document.body.scrollWidth - document.body.clientWidth
  }));
  expect(overflow.document).toBeLessThanOrEqual(1);
  expect(overflow.body).toBeLessThanOrEqual(1);
}

test.describe('React Home migration', () => {
  test('owner-ready state focuses on value, attention and latest activity without duplicated navigation', async ({ page }) => {
    const { requests } = await bootHome(page);

    await page.goto('/app');

    await expect(page).toHaveURL(/\/app\/?$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' })).toContainText(/todo bajo control|necesita tu atención/i);

    const heroBox = await page.getByRole('region', { name: 'Estado de tu negocio' }).boundingBox();
    expect(heroBox).not.toBeNull();
    expect(heroBox.height).toBeLessThanOrEqual(190);

    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toBeVisible();
    await expect(value).toContainText(/valor.*atribuido|ventas.*atribuidas/i);
    await expect(value).toContainText('$209.900');
    await expect(value).toContainText(/4 pedidos|2 reservas/i);
    await expect(value).not.toContainText(/ganancia|beneficio estimado|ROI|retorno de inversi/i);

    const today = page.getByRole('region', { name: 'Resultados de hoy' });
    await expect(today).toBeVisible();
    await expect(today).toContainText('6');
    await expect(today).toContainText(/llamadas atendidas/i);
    await expect(today).toContainText('4');
    await expect(today).toContainText(/reservas generadas/i);
    await expect(today).toContainText('15');
    await expect(today).toContainText(/minutos.*IA|min.*gestionados/i);

    const attention = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(attention).toBeVisible();
    await expect(attention).toContainText('2');
    await expect(attention).toContainText(/solicitudes|pendientes/i);
    await expect(attention).toContainText(/pregunta|sin respuesta/i);
    await expect(attention).toContainText(/fallo/i);

    const recent = page.getByRole('region', { name: 'Última actividad' });
    await expect(recent).toBeVisible();
    await expect(recent).toContainText('+56911112222');

    await expect(page.getByRole('navigation', { name: 'Accesos rápidos' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Resumen de ventas' })).toHaveCount(0);

    const apiRequests = requests.filter(request => request.url.includes('/api/v1/'));
    for (const request of apiRequests) {
      const url = new URL(request.url);
      expect(url.searchParams.has('businessId')).toBe(false);
      expect(request.postData || '').not.toMatch(/businessId/i);
    }

    expect(requests.some(request => request.url.includes('/operations/pilot-readiness'))).toBe(false);
    expect(requests.some(request => request.url.includes('/operations/pilot-metrics'))).toBe(false);
    expect(requests.some(request => request.url.includes('/operations/readiness'))).toBe(false);
  });

  test('loads a deduplicated actionable attention inbox and keeps the human history secondary', async ({ page }) => {
    const api = await bootHome(page);
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Cliente pidió devolución de llamada');
    await expect(panel).toContainText('Reclamo de cliente pendiente');
    await expect(panel).toContainText('Señales adicionales para revisar');
    await expect(panel).not.toContainText('4 solicitudes');
    expect(api.attentionReads()).toBe(1);
    await expect(page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Historial y auditoría' })).toHaveCount(0);
    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/i }).click();
    await expect(page.getByRole('heading', { name: 'Historial y auditoría' })).toBeVisible();
  });

  test('updates requests and takes handoffs only after explicit user actions', async ({ page }) => {
    const api = await bootHome(page);
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Cliente pidió devolución de llamada');
    expect(api.attentionMutations()).toBe(0);
    await panel.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel).toContainText('En progreso');
    await expect.poll(api.attentionMutations).toBe(1);
    await panel.getByRole('button', { name: 'Marcar resuelta' }).click();
    await expect(panel.getByRole('button', { name: 'Confirmar cierre' })).toBeVisible();
    expect(api.attentionMutations()).toBe(1);
    await panel.getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel).not.toContainText('Cliente pidió devolución de llamada');
    await panel.getByRole('button', { name: 'Tomar caso' }).click();
    await expect(panel).toContainText('Recibido');
    await panel.getByRole('button', { name: 'Cerrar escalamiento' }).click();
    await expect(panel.getByRole('button', { name: 'Confirmar cierre' })).toBeVisible();
    await panel.getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel).toContainText('Sin intervenciones pendientes');
    expect(api.attentionMutations()).toBe(4);
  });

  test('does not fabricate a clear queue when the attention API fails', async ({ page }) => {
    await bootHome(page, { attentionError: 503 });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel.getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    await expect(panel).not.toContainText('Sin intervenciones pendientes');
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' }))
      .not.toContainText('Todo bajo control');
  });

  test('never fetches the protected human inbox for a role without operational access', async ({ page }) => {
    const api = await bootHome(page, {
      roles: ['STAFF'],
      permissions: ['BOOKINGS_READ']
    });
    await page.goto('/app');
    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toContainText('Tu rol no tiene acceso');
    expect(api.attentionReads()).toBe(0);
  });

  test('read-only request permission does not expose status-changing controls', async ({ page }) => {
    const api = await bootHome(page, {
      roles: ['OPERATOR'],
      permissions: ['REQUESTS_READ', 'OPERATIONS_READ']
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Cliente pidió devolución de llamada');
    await expect(panel.getByRole('button', { name: 'Empezar gestión' })).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('renders the approved sans typography and layered petroleum atmosphere', async ({ page }) => {
    await bootHome(page);
    await page.goto('/app');

    const fontFamily = await page.evaluate(() => getComputedStyle(document.body).fontFamily);
    expect(fontFamily).toMatch(/Inter|Segoe UI|system-ui|sans-serif/i);
    expect(fontFamily).not.toMatch(/Times New Roman|Georgia|ui-serif/i);

    const layers = page.locator('[data-home-ambient-layer]');
    await expect(layers).toHaveCount(4);

    for (const layer of ['flow', 'grid', 'particles', 'halo']) {
      await expect(page.locator(`[data-home-ambient-layer="${layer}"]`)).toBeAttached();
    }

    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
  });

  test('value period controls query real analytics windows instead of faking client-side numbers', async ({ page }) => {
    const { requests } = await bootHome(page);
    await page.goto('/app');

    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value.getByRole('button', { name: '7 días' })).toHaveAttribute('aria-pressed', 'true');

    await value.getByRole('button', { name: 'Hoy' }).click();
    await expect.poll(() => requests.filter(request => request.url.includes('/api/v1/commercial/analytics?days=1')).length).toBeGreaterThan(0);

    await value.getByRole('button', { name: '30 días' }).click();
    await expect.poll(() => requests.filter(request => request.url.includes('/api/v1/commercial/analytics?days=30')).length).toBeGreaterThan(0);
  });

  test('value panel survives nullable analytics fields omitted by backend JSON serialization', async ({ page }) => {
    const analytics = { ...READY_ANALYTICS };
    delete analytics.revenueChangePercent;

    await bootHome(page, { analytics });

    await page.goto('/app');

    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Valor generado por RecepVoz' })).toBeVisible();
    await expect(page.locator('#root')).not.toBeEmpty();
  });

  test('incomplete owner sees onboarding guidance instead of duplicated operational workspaces', async ({ page }) => {
    await bootHome(page, { onboarding: INCOMPLETE_ONBOARDING });

    await page.goto('/app');

    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    const onboarding = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(onboarding).toBeVisible();
    await expect(onboarding.getByRole('progressbar')).toBeVisible();
    await expect(onboarding).toContainText(/servicios/i);
    await expect(onboarding.getByRole('link', { name: /continuar/i })).toHaveAttribute(
      'href',
      /\/app\/settings\?section=services/
    );

    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Resumen de ventas' })).toHaveCount(0);
  });

  test('unknown onboarding steps still stay inside canonical React Settings', async ({ page }) => {
    await bootHome(page, {
      onboarding: {
        ...INCOMPLETE_ONBOARDING,
        nextStep: 'FUTURE_ONBOARDING_STEP'
      }
    });

    await page.goto('/app');

    const onboarding = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(onboarding).toBeVisible();
    await expect(onboarding.getByRole('link', { name: /continuar/i })).toHaveAttribute(
      'href',
      '/app/settings'
    );
    await expect(page.locator('a[href^="/settings.html"]')).toHaveCount(0);
  });

  test('phone onboarding parity points the owner to the receptionist setup', async ({ page }) => {
    await bootHome(page, {
      onboarding: {
        ...READY_ONBOARDING,
        phoneConfigured: false,
        readyForCalls: false,
        nextStep: 'CONNECT_PHONE_NUMBER'
      }
    });

    await page.goto('/app');

    const onboarding = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(onboarding).toContainText('Recepcionista');
    await expect(onboarding.getByRole('link', { name: /continuar/i })).toHaveAttribute(
      'href',
      /\/app\/settings\?section=receptionist/
    );
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
  });

  test('shows an explicit loading state while the daily summary is pending', async ({ page }) => {
    let releaseOperations;
    const operationsGate = new Promise(resolve => { releaseOperations = resolve; });
    await bootHome(page, { operationsGate });

    await page.goto('/app');

    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    await expect(page.getByRole('status')).toContainText(/cargando/i);

    releaseOperations();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
  });

  test('empty state preserves the Home hierarchy without inventing activity', async ({ page }) => {
    await bootHome(page, {
      operations: EMPTY_OPERATIONS,
      analytics: EMPTY_ANALYTICS
    });

    await page.goto('/app');

    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText(/sin actividad|todavía no/i);
    await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toContainText(/sin intervenciones pendientes/i);
    await expect(page.getByRole('region', { name: 'Última actividad' })).toContainText(/todavía no|sin actividad/i);
    await expect(page.getByRole('navigation', { name: 'Accesos rápidos' })).toHaveCount(0);
  });

  test('sales failure is partial and never takes down daily operations', async ({ page }) => {
    await bootHome(page, { analyticsError: 503 });

    await page.goto('/app');

    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Última actividad' })).toBeVisible();

    const sales = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(sales).toBeVisible();
    await expect(sales.getByRole('alert')).toContainText(/no pudimos cargar|no disponible/i);
  });

  test('value panel keeps currencies separate and describes source attribution without causal ROI claims', async ({ page }) => {
    await bootHome(page, {
      analytics: {
        ...READY_ANALYTICS,
        primaryCurrency: 'USD',
        totalRevenue: null,
        averageTicket: null,
        currencyTotals: [
          { currency: 'CLP', amount: 189900 },
          { currency: 'USD', amount: 30 }
        ],
        recepVozRevenue: 30,
        bookingCurrency: 'CLP',
        recepVozBookingRevenue: 50000,
        bookingCurrencyTotals: [{ currency: 'CLP', amount: 75000 }]
      }
    });

    await page.goto('/app');

    const sales = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(sales).toContainText('CLP');
    await expect(sales).toContainText('USD');
    await expect(sales).toContainText(/origen|atribuci/i);
    await expect(sales).not.toContainText(/ROI|retorno de inversi/i);
  });

  test('401 clears the browser session and returns to the public entry', async ({ page }) => {
    await bootHome(page, { expired: true });
    await page.route('http://127.0.0.1:4173/', route =>
      route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<!doctype html><html><body><main data-auth-entry>Acceso</main></body></html>'
      })
    );

    await page.goto('/app');

    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    await expect(page.locator('[data-auth-page="recepvoz"]')).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
  });

  test('operator gets the operational cockpit without calling the owner-only onboarding endpoint', async ({ page }) => {
    const state = await bootHome(page, {
      roles: ['OPERATOR'],
      permissions: [
        'BUSINESS_READ',
        'CUSTOMERS_READ',
        'CONVERSATIONS_READ',
        'BOOKINGS_READ',
        'ORDERS_READ',
        'INVENTORY_READ',
        'ANALYTICS_READ',
        'OPERATIONS_READ',
        'REQUESTS_READ'
      ]
    });

    await page.goto('/app');

    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Última actividad' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Configura tu negocio' })).toHaveCount(0);
    expect(state.onboardingRequests()).toBe(0);
  });

  test('Home performs only read/navigation work and does not trigger external side effects', async ({ page }) => {
    const { requests } = await bootHome(page);

    await page.goto('/app');
    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();

    const apiRequests = requests.filter(request => request.url.includes('/api/v1/'));
    const mutations = apiRequests.filter(request => !['GET', 'HEAD', 'OPTIONS'].includes(request.method));
    expect(mutations).toEqual([]);

    const unsafe = requests.filter(request =>
      /twilio|whatsapp.*send|outbound-messages|dispatch|payments.*(charge|capture|create)/i.test(request.url)
    );
    expect(unsafe).toEqual([]);
  });

  test('unknown attributed revenue never becomes a fictional $0', async ({ page }) => {
    const analytics = {
      ...READY_ANALYTICS,
      recepVozRevenue: null,
      recepVozBookingRevenue: null
    };
    await bootHome(page, { analytics });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('Valor no disponible');
    await expect(value).not.toContainText('$0');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
  });

  test('verified zero revenue remains zero rather than unknown', async ({ page }) => {
    await bootHome(page, { analytics: EMPTY_ANALYTICS });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('$0');
    await expect(value).not.toContainText('Valor no disponible');
  });

  test('negative or malformed attributed amounts never produce revenue or a positive ROI', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      recepVozRevenue: -9000,
      recepVozBookingRevenue: -35
    }});
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('Valor no disponible');
    await expect(value).not.toContainText('$-');
    await expect(value).not.toContainText(/ROI positivo|retorno de inversión confirmado/i);
  });

  test('partial verified revenue is presented as partial, not the total business value', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      recepVozRevenue: null,
      bookingCurrency: 'CLP', recepVozBookingRevenue: 25000
    }});
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('$25.000');
    await expect(value).toContainText('Datos parciales');
  });

  test('malformed string amounts are not parsed into trusted money', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS, recepVozRevenue: '999999', recepVozBookingRevenue: null
    }});
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('Valor no disponible');
    await expect(value).not.toContainText('$999.999');
  });

  test('unsupported currency formatting is never hidden behind a fictitious currency conversion', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS, primaryCurrency: 'INVALID', recepVozRevenue: 123,
      bookingCurrency: null, recepVozBookingRevenue: null
    }});
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('Valor no disponible');
    await expect(value).not.toContainText('INVALID 123');
  });

  test('different currencies remain separate instead of blending attributed revenue', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: 'USD', recepVozRevenue: 30,
      bookingCurrency: 'CLP', recepVozBookingRevenue: 50000
    }});
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('USD');
    await expect(value).toContainText('CLP');
    await expect(value).not.toContainText('50.030');
  });

  test('same currency groups attributed sales and bookings without double counting other totals', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: 'CLP', recepVozRevenue: 10000,
      bookingCurrency: 'CLP', recepVozBookingRevenue: 25000,
      totalRevenue: 999999
    }});
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('$35.000');
    await expect(value).not.toContainText('999.999');
  });

  test('changing Hoy / 7 días / 30 días uses its own API period, never the previous period total', async ({ page }) => {
    const calls = [];
    await bootHome(page);
    await page.route('**/api/v1/commercial/analytics**', route => {
      const days = Number(new URL(route.request().url()).searchParams.get('days'));
      calls.push(days);
      return route.fulfill(json({
        ...READY_ANALYTICS, days,
        recepVozRevenue: ({ 1: 1100, 7: 7700, 30: 33000 })[days],
        recepVozBookingRevenue: 0
      }));
    });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('$7.700');
    await value.getByRole('button', { name: 'Hoy' }).click();
    await expect(value).toContainText('$1.100');
    await expect(value).not.toContainText('$7.700');
    await value.getByRole('button', { name: '30 días' }).click();
    await expect(value).toContainText('$33.000');
    await expect(value).not.toContainText('$1.100');
    await value.getByRole('button', { name: '7 días' }).click();
    await expect(value).toContainText('$7.700');
    expect(calls).toContain(1);
    expect(calls).toContain(7);
    expect(calls).toContain(30);
  });


  test('keyboard Tab, Space and aria-pressed operate the sales period with a visible focus ring', async ({ page }) => {
    const state = await bootHome(page);
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    const seven = value.getByRole('button', { name: '7 días' });
    const thirty = value.getByRole('button', { name: '30 días' });
    await expect(seven).toHaveAttribute('aria-pressed', 'true');
    await seven.focus();
    await page.keyboard.press('Tab');
    await expect(thirty).toBeFocused();
    const styles = await thirty.evaluate(el => ({
      focusedByKeyboard: el.matches(':focus-visible'),
      outline: getComputedStyle(el).outlineStyle,
      width: parseFloat(getComputedStyle(el).outlineWidth)
    }));
    expect(styles.focusedByKeyboard).toBe(true);
    expect(styles.outline).not.toBe('none');
    expect(styles.width).toBeGreaterThanOrEqual(2);
    await page.keyboard.press('Space');
    await expect(thirty).toHaveAttribute('aria-pressed', 'true');
    await expect(seven).toHaveAttribute('aria-pressed', 'false');
    await expect.poll(() => state.requests.some(request =>
      request.url.includes('/api/v1/commercial/analytics?days=30'))).toBe(true);
    expect(state.attentionMutations()).toBe(0);
  });

  test('history expands with Enter and collapses with Space, reflecting aria-expanded', async ({ page }) => {
    const state = await bootHome(page);
    await page.goto('/app');
    const toggle = page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/ });
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await toggle.focus();
    await page.keyboard.press('Enter');
    const collapse = page.getByRole('button', { name: /Ocultar historial y auditoría/ });
    await expect(collapse).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByRole('heading', { name: 'Historial y auditoría' })).toBeVisible();
    await collapse.focus();
    await page.keyboard.press('Space');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByRole('heading', { name: 'Historial y auditoría' })).toHaveCount(0);
    expect(state.attentionMutations()).toBe(0);
  });

  test('keyboard cancellation of a destructive action never sends a close request', async ({ page }) => {
    const state = await bootHome(page);
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel).toContainText('En progreso');
    const resolve = panel.getByRole('button', { name: 'Marcar resuelta' });
    await resolve.focus();
    await page.keyboard.press('Enter');
    const cancel = panel.getByRole('button', { name: 'Cancelar' });
    await expect(cancel).toBeVisible();
    await cancel.focus();
    await page.keyboard.press('Space');
    await expect(panel.getByRole('button', { name: 'Confirmar cierre' })).toHaveCount(0);
    await expect(panel).toContainText('Cliente pidió devolución de llamada');
    expect(state.attentionMutations()).toBe(1);
  });

  test('keyboard confirmation performs only the explicitly accepted request close', async ({ page }) => {
    const state = await bootHome(page);
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel).toContainText('En progreso');
    await panel.getByRole('button', { name: 'Marcar resuelta' }).click();
    expect(state.attentionMutations()).toBe(1);
    const confirm = panel.getByRole('button', { name: 'Confirmar cierre' });
    await confirm.focus();
    await page.keyboard.press('Enter');
    await expect(panel).not.toContainText('Cliente pidió devolución de llamada');
    expect(state.attentionMutations()).toBe(2);
    const writes = state.requests.filter(req =>
      !['GET', 'OPTIONS', 'HEAD'].includes(req.method) && req.url.includes('/api/v1/'));
    expect(writes).toHaveLength(2);
    expect(writes.every(req => req.url.includes('/api/v1/requests/req-1/status'))).toBe(true);
  });

  test('onboarding progress exposes an accurate numeric accessible value and keyboard link', async ({ page }) => {
    const state = await bootHome(page, { onboarding: INCOMPLETE_ONBOARDING });
    await page.goto('/app');
    const section = page.getByRole('region', { name: 'Configura tu negocio' });
    const progress = section.getByRole('progressbar', { name: 'Progreso de configuración' });
    await expect(progress).toHaveAttribute('aria-valuemin', '0');
    await expect(progress).toHaveAttribute('aria-valuemax', '100');
    await expect(progress).toHaveAttribute('aria-valuenow', '25');
    const link = section.getByRole('link', { name: /Continuar configuración/ });
    await expect(link).toHaveAttribute('href', '/app/settings?section=services');
    await link.focus();
    await expect(link).toBeFocused();
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');
    await expect(link).toBeFocused();
    const visual = await link.evaluate(el => ({
      focusVisible: el.matches(':focus-visible'),
      style: getComputedStyle(el).outlineStyle,
      size: parseFloat(getComputedStyle(el).outlineWidth)
    }));
    expect(visual.focusVisible).toBe(true);
    expect(visual.style).not.toBe('none');
    expect(visual.size).toBeGreaterThanOrEqual(2);
    expect(state.attentionMutations()).toBe(0);
    expect(state.requests.some(req => req.url.includes('/api/v1/operations/dashboard'))).toBe(false);
  });

  test('Home uses one page-level heading, labelled regions and decorative non-semantic layers', async ({ page }) => {
    await bootHome(page);
    await page.goto('/app');
    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toHaveCount(1);
    for (const title of [
      'Estado de tu negocio', 'Valor generado por RecepVoz',
      'Resultados de hoy', 'Necesita tu atención', 'Última actividad'
    ]) {
      await expect(page.getByRole('region', { name: title })).toBeVisible();
    }
    await expect(page.locator('[data-home-ambient-layer][aria-hidden="true"]')).toHaveCount(0);
    expect(await page.locator('[data-home-ambient-layer]').evaluateAll(items =>
      items.every(item => item.closest('[aria-hidden="true"]') !== null))).toBe(true);
  });

  test('reduced motion removes animation and transition on the Home scene', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootHome(page);
    await page.goto('/app');
    await expect(page.getByRole('region', { name: 'Valor generado por RecepVoz' })).toBeVisible();
    expect(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches)).toBe(true);
    const state = await page.evaluate(() => {
      const atmosphere = document.querySelector('[data-home-ambient-layer="flow"]');
      const section = document.querySelector('[aria-labelledby="homeValueTitle"]');
      if (!atmosphere || !section) return null;
      return {
        ambience: getComputedStyle(atmosphere).animationName,
        valueAnimationMs: getComputedStyle(section, '::before').animationDuration,
        periodTransitionMs: getComputedStyle(
          document.querySelector('[aria-label="Periodo del valor generado"] button')
        ).transitionDuration
      };
    });
    expect(state).not.toBeNull();
    expect(state.ambience).toBe('none');
    expect(state.valueAnimationMs).toMatch(/0\.0?1ms|0s|1e-05s|1e-5s/);
    expect(state.periodTransitionMs).toMatch(/0\.0?1ms|0s|1e-05s|1e-5s/);
  });

  test('commercial pending state is announced and marked aria-busy without hiding operations', async ({ page }) => {
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    await bootHome(page);
    await page.route('**/api/v1/commercial/analytics**', async route => {
      await gate;
      return route.fulfill(json(READY_ANALYTICS));
    });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toHaveAttribute('aria-busy', 'true');
    await expect(value.getByRole('status')).toContainText('Calculando valor atribuido');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    release();
    await expect(value).toHaveAttribute('aria-busy', 'false');
    await expect(value).toContainText('$209.900');
  });

  test('no provider/payment side effects arise from navigation-only tabs, refresh and history', async ({ page }) => {
    const state = await bootHome(page);
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await value.getByRole('button', { name: 'Hoy' }).click();
    await value.getByRole('button', { name: '30 días' }).click();
    const attention = page.getByRole('region', { name: 'Necesita tu atención' });
    await attention.getByRole('button', { name: 'Actualizar' }).click();
    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/ }).click();
    await expect(page.getByRole('heading', { name: 'Historial y auditoría' })).toBeVisible();
    expect(state.attentionMutations()).toBe(0);
    const writes = state.requests.filter(req =>
      req.url.includes('/api/v1/') && !['GET', 'HEAD', 'OPTIONS'].includes(req.method));
    expect(writes).toEqual([]);
    expect(state.requests.filter(req => /twilio|outbound|whatsapp.*send|payments.*(?:charge|capture|create)/i.test(req.url))).toEqual([]);
  });

  for (const viewport of [
    { name: 'small phone', width: 320, height: 640 },
    { name: 'compact phone', width: 360, height: 740 },
    { name: 'small laptop', width: 1024, height: 768 },
    { name: 'wide desktop', width: 1920, height: 1080 }
  ]) {
    test('reflows important Home controls and avoids page overflow at ' + viewport.name, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await bootHome(page);
      await page.goto('/app');
      const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
      const panel = page.getByRole('region', { name: 'Necesita tu atención' });
      await expect(value).toBeVisible();
      await expect(panel).toBeVisible();
      for (const label of ['Hoy', '7 días', '30 días']) {
        const button = value.getByRole('button', { name: label });
        await expect(button).toBeVisible();
        const bounds = await button.boundingBox();
        expect(bounds).not.toBeNull();
        expect(bounds.width).toBeGreaterThanOrEqual(24);
        expect(bounds.height).toBeGreaterThanOrEqual(24);
      }
      const geometry = await page.evaluate(() => ({
        document: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        body: document.body.scrollWidth - document.body.clientWidth
      }));
      expect(geometry.document).toBeLessThanOrEqual(1);
      expect(geometry.body).toBeLessThanOrEqual(1);
    });
  }

  for (const viewport of [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'tablet', width: 768, height: 1024 },
    { name: 'mobile', width: 390, height: 844 }
  ]) {
    test(`stays contained at ${viewport.name} width with no page-level horizontal overflow`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await bootHome(page);

      await page.goto('/app');

      await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Última actividad' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Accesos rápidos' })).toHaveCount(0);
      await expectNoHorizontalOverflow(page);
    });
  }
});
