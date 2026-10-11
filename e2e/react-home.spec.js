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
  let meFailuresRemaining = options.meFailures ?? 0;
  let onboardingFailuresRemaining = options.onboardingFailures ?? 0;
  let operationsFailuresRemaining = options.operationsFailures ?? 0;
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
    if (meFailuresRemaining > 0) {
      meFailuresRemaining -= 1;
      return route.fulfill(json({ message: 'identity unavailable' }, 503));
    }
    if (options.meError) {
      return route.fulfill(json({ message: 'forbidden' }, options.meError));
    }
    return route.fulfill(json({
      email: roles.includes('OPERATOR') ? 'operator@negocio.cl' : 'owner@negocio.cl',
      roles,
      ...(permissions ? { permissions } : {})
    }));
  });

  await page.route('**/api/v1/onboarding/status', route => {
    onboardingRequests += 1;
    if (onboardingFailuresRemaining > 0) {
      onboardingFailuresRemaining -= 1;
      return route.fulfill(json({ message: 'onboarding unavailable' }, 503));
    }
    if (options.onboardingError) {
      return route.fulfill(json({ message: 'onboarding unavailable' }, options.onboardingError));
    }
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
    if (operationsFailuresRemaining > 0) {
      operationsFailuresRemaining -= 1;
      return route.fulfill(json({ message: 'operations unavailable' }, 503));
    }
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
    await expect(today).toContainText(/llamadas registradas/i);
    await expect(today).toContainText('4');
    await expect(today).toContainText(/reservas registradas/i);
    await expect(today).toContainText('15');
    await expect(today).toContainText(/minutos de llamadas registradas/i);

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


  test('daily totals describe all logged calls and bookings without claiming each was answered or AI-created', async ({ page }) => {
    const api = await bootHome(page, {
      operations: {
        ...READY_OPERATIONS,
        callsToday: 2,
        bookingsToday: 1,
        callFailuresToday: 2,
        recentCalls: [{
          ...READY_OPERATIONS.recentCalls[1],
          status: 'FAILED',
          resolution: 'FAILED'
        }]
      }
    });
    await page.goto('/app');

    const hero = page.getByRole('region', { name: 'Estado de tu negocio' });
    const today = page.getByRole('region', { name: 'Resultados de hoy' });
    await expect(hero).toContainText('Hoy se registraron 2 llamadas y 1 reserva.');
    await expect(hero).not.toContainText(/atendió|creó/i);
    await expect(today).toContainText('Llamadas registradas');
    await expect(today).toContainText('Reservas registradas');
    await expect(today).not.toContainText(/llamadas atendidas|reservas generadas/i);
    await expect(page.getByRole('region', { name: 'Necesita tu atención' }))
      .toContainText('2 fallos de llamada');
    expect(api.attentionMutations()).toBe(0);
  });

  test('single call and zero bookings render grammatically accurate daily summary', async ({ page }) => {
    await bootHome(page, {
      operations: {
        ...EMPTY_OPERATIONS,
        callsToday: 1,
        recentCalls: []
      },
      attention: []
    });
    await page.goto('/app');
    const hero = page.getByRole('region', { name: 'Estado de tu negocio' });
    await expect(hero).toContainText('1 llamada y 0 reservas.');
    await expect(hero).not.toContainText('1 llamadas');
  });

  test('in-progress provider call displays live status without manufacturing a completed call', async ({ page }) => {
    const active = {
      ...READY_OPERATIONS.recentCalls[0],
      status: 'IN_PROGRESS',
      resolution: null
    };
    const api = await bootHome(page, {
      operations: {
        ...READY_OPERATIONS,
        callsToday: 1,
        bookingsToday: 0,
        recentCalls: [active],
        recentRequests: []
      }
    });
    await page.goto('/app');

    await expect(page.locator('[data-visual-page="home"]')).toContainText('Atendiendo ahora');
    await expect(page.getByRole('region', { name: 'Última actividad' })).toContainText('En curso');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('Llamadas registradas');
    expect(api.requests.filter(request => request.url.includes('/api/v1/') && request.method !== 'GET')).toEqual([]);
  });

  test('latest activity picks a newer human request instead of an older call', async ({ page }) => {
    await bootHome(page, {
      operations: {
        ...READY_OPERATIONS,
        recentCalls: [{ ...READY_OPERATIONS.recentCalls[0], startedAt: '2026-10-02T14:00:00Z' }],
        recentRequests: [{
          ...READY_OPERATIONS.recentRequests[0],
          createdAt: '2026-10-02T15:00:00Z',
          title: 'Solicitud más reciente'
        }]
      }
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Última actividad' });
    await expect(panel).toContainText('Solicitud más reciente');
    await expect(panel).toContainText('Abierta');
    await expect(panel).not.toContainText('+56911112222');
  });

  test('latest activity chooses the newer call and formats time in the business timezone', async ({ page }) => {
    const instant = '2026-10-02T15:00:00Z';
    const expected = new Intl.DateTimeFormat('es-CL', {
      hour: '2-digit', minute: '2-digit', timeZone: 'America/Santiago'
    }).format(new Date(instant));
    const api = await bootHome(page, {
      operations: {
        ...READY_OPERATIONS,
        timezone: 'America/Santiago',
        recentCalls: [{
          ...READY_OPERATIONS.recentCalls[0],
          startedAt: instant,
          resolution: 'BOOKING_CREATED'
        }],
        recentRequests: [{
          ...READY_OPERATIONS.recentRequests[0],
          createdAt: '2026-10-02T14:59:59Z'
        }]
      }
    });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/app');
    const latest = page.getByRole('region', { name: 'Última actividad' });
    await expect(latest).toContainText('+56911112222');
    await expect(latest).toContainText('Reserva creada');
    await expect(latest.locator('time')).toHaveAttribute('dateTime', instant);
    await expect(latest.locator('time')).toHaveText(expected);
    expect(api.attentionMutations()).toBe(0);
  });

  test('invalid timezone never crashes the activity summary', async ({ page }) => {
    await bootHome(page, {
      operations: { ...READY_OPERATIONS, timezone: 'Invalid/Business_Zone' }
    });
    await page.goto('/app');
    const latest = page.getByRole('region', { name: 'Última actividad' });
    await expect(latest).toBeVisible();
    await expect(latest.locator('time')).toBeEmpty();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
  });

  test('business with only reservations does not appear empty despite zero calls', async ({ page }) => {
    await bootHome(page, {
      operations: {
        ...EMPTY_OPERATIONS,
        bookingsToday: 3
      },
      attention: []
    });
    await page.goto('/app');
    const today = page.getByRole('region', { name: 'Resultados de hoy' });
    await expect(today).toContainText('Reservas registradas');
    await expect(today).not.toContainText('Todavía no hay actividad registrada hoy.');
  });

  for (const [seconds, minutes] of [
    [0, 0], [1, 1], [29, 1], [59, 1],
    [60, 1], [89, 1], [90, 2], [3600, 60]
  ]) {
    test(`daily call duration ${seconds}s maps to ${minutes} rounded minutes`, async ({ page }) => {
      await bootHome(page, {
        operations: {
          ...READY_OPERATIONS,
          callDurationSecondsToday: seconds
        }
      });
      await page.goto('/app');
      const section = page.getByRole('region', { name: 'Resultados de hoy' });
      const minuteMetric = section.locator('article').filter({ hasText: 'Minutos de llamadas registradas' });
      await expect(minuteMetric.locator('strong')).toHaveText(String(minutes));
    });
  }

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

  test('403 identity check fails closed before any protected operational API call', async ({ page }) => {
    const api = await bootHome(page, { meError: 403 });
    await page.goto('/app');

    const error = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(error).toContainText('No pudimos verificar tus permisos de acceso.');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' })).toHaveCount(0);
    expect(api.requests.some(request => /\/api\/v1\/(operations\/|onboarding\/|commercial\/analytics)/.test(request.url))).toBe(false);
    expect(api.attentionReads()).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('transient identity outage recovers only after revalidating the user', async ({ page }) => {
    const api = await bootHome(page, { meFailures: 2 });
    await page.goto('/app');

    const error = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(error).toContainText('No pudimos verificar tus permisos de acceso.');
    expect(api.requests.some(request => request.url.includes('/operations/dashboard'))).toBe(false);

    await error.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await expect(error).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('staff without operational role sees a restricted home without protected backend reads', async ({ page }) => {
    const api = await bootHome(page, {
      roles: ['STAFF'],
      permissions: ['BOOKINGS_READ']
    });
    await page.goto('/app');
    await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();
    await expect(page.getByRole('status')).toContainText('Tu rol no tiene acceso al resumen operativo.');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toHaveCount(0);

    const endpoints = api.requests.map(request => new URL(request.url).pathname);
    expect(endpoints).not.toContain('/api/v1/operations/dashboard');
    expect(endpoints).not.toContain('/api/v1/operations/attention');
    expect(endpoints).not.toContain('/api/v1/onboarding/status');
    expect(endpoints).not.toContain('/api/v1/commercial/analytics');
    expect(api.attentionReads()).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('permissions listed by the client cannot override restricted operational roles', async ({ page }) => {
    const api = await bootHome(page, {
      roles: ['STAFF'],
      permissions: ['OPERATIONS_READ', 'ANALYTICS_READ', 'REQUESTS_READ']
    });
    await page.goto('/app');
    await expect(page.getByRole('status')).toContainText('Tu rol no tiene acceso al resumen operativo.');
    expect(api.requests.filter(request => /\/api\/v1\/(operations\/|commercial\/analytics)/.test(request.url))).toEqual([]);
    expect(api.attentionReads()).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('Home ignores businessId supplied in the URL and never forwards it to tenant APIs', async ({ page }) => {
    const api = await bootHome(page);
    await page.goto('/app?businessId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();

    const observed = api.requests.filter(request => request.url.includes('/api/v1/'));
    expect(observed.length).toBeGreaterThan(0);
    for (const request of observed) {
      const url = new URL(request.url);
      expect(url.searchParams.has('businessId')).toBe(false);
      expect(request.postData || '').not.toMatch(/businessId/i);
      expect(request.method).toBe('GET');
    }
  });

  test('unauthenticated visitors are redirected before any protected Home API request', async ({ page }) => {
    const protectedReads = [];
    page.on('request', request => {
      if (/\/api\/v1\/(?:operations|commercial|onboarding)\//.test(request.url())) {
        protectedReads.push(request.url());
      }
    });

    await page.goto('/app');
    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    expect(protectedReads).toEqual([]);
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

  // Onboarding must reflect the authoritative backend state, never invent progress.

  test('onboarding business profile step directs to canonical Settings with exact 0% progress', async ({ page }) => {
    const api = await bootHome(page, { onboarding: {
      ...READY_ONBOARDING,
      businessProfileConfigured: false,
        servicesConfigured: false,
        scheduleConfigured: false,
        phoneConfigured: false,
      readyForCalls: false,
      nextStep: 'CONFIGURE_BUSINESS'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card).toBeVisible();
    await expect(card.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
    await expect(card).toContainText('0 de 4 pasos completos');
    await expect(card.locator('[data-complete="true"]')).toHaveCount(0);
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=business');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    expect(api.requests.filter(request => /\/api\/v1\/(operations\/|commercial\/analytics)/.test(request.url))).toEqual([]);
    expect(api.attentionReads()).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('onboarding services step directs to canonical Settings with exact 25% progress', async ({ page }) => {
    const api = await bootHome(page, { onboarding: {
      ...READY_ONBOARDING,
      businessProfileConfigured: true,
        servicesConfigured: false,
        scheduleConfigured: false,
        phoneConfigured: false,
      readyForCalls: false,
      nextStep: 'ADD_SERVICE'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card).toBeVisible();
    await expect(card.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25');
    await expect(card).toContainText('1 de 4 pasos completos');
    await expect(card.locator('[data-complete="true"]')).toHaveCount(1);
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=services');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    expect(api.requests.filter(request => /\/api\/v1\/(operations\/|commercial\/analytics)/.test(request.url))).toEqual([]);
    expect(api.attentionReads()).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('onboarding hours step directs to canonical Settings with exact 50% progress', async ({ page }) => {
    const api = await bootHome(page, { onboarding: {
      ...READY_ONBOARDING,
      businessProfileConfigured: true,
        servicesConfigured: true,
        scheduleConfigured: false,
        phoneConfigured: false,
      readyForCalls: false,
      nextStep: 'CONFIGURE_HOURS'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card).toBeVisible();
    await expect(card.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50');
    await expect(card).toContainText('2 de 4 pasos completos');
    await expect(card.locator('[data-complete="true"]')).toHaveCount(2);
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=hours');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    expect(api.requests.filter(request => /\/api\/v1\/(operations\/|commercial\/analytics)/.test(request.url))).toEqual([]);
    expect(api.attentionReads()).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('onboarding phone step directs to canonical Settings with exact 75% progress', async ({ page }) => {
    const api = await bootHome(page, { onboarding: {
      ...READY_ONBOARDING,
      businessProfileConfigured: true,
        servicesConfigured: true,
        scheduleConfigured: true,
        phoneConfigured: false,
      readyForCalls: false,
      nextStep: 'CONNECT_PHONE_NUMBER'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card).toBeVisible();
    await expect(card.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '75');
    await expect(card).toContainText('3 de 4 pasos completos');
    await expect(card.locator('[data-complete="true"]')).toHaveCount(3);
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=receptionist');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    expect(api.requests.filter(request => /\/api\/v1\/(operations\/|commercial\/analytics)/.test(request.url))).toEqual([]);
    expect(api.attentionReads()).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('legacy BUSINESS_PROFILE onboarding alias preserves the correct Settings destination', async ({ page }) => {
    await bootHome(page, { onboarding: {
      ...READY_ONBOARDING, businessProfileConfigured: false, readyForCalls: false, nextStep: 'BUSINESS_PROFILE'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=business');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
  });

  test('legacy SERVICES onboarding alias preserves the correct Settings destination', async ({ page }) => {
    await bootHome(page, { onboarding: {
      ...READY_ONBOARDING, servicesConfigured: false, readyForCalls: false, nextStep: 'SERVICES'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=services');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
  });

  test('legacy SCHEDULE onboarding alias preserves the correct Settings destination', async ({ page }) => {
    await bootHome(page, { onboarding: {
      ...READY_ONBOARDING, scheduleConfigured: false, readyForCalls: false, nextStep: 'SCHEDULE'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=hours');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
  });

  test('legacy PHONE onboarding alias preserves the correct Settings destination', async ({ page }) => {
    await bootHome(page, { onboarding: {
      ...READY_ONBOARDING, phoneConfigured: false, readyForCalls: false, nextStep: 'PHONE'
    } });
    await page.goto('/app');
    const card = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(card.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings?section=receptionist');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
  });

  test('service-free business already ready for calls is never blocked by optional services or hours', async ({ page }) => {
    const api = await bootHome(page, { onboarding: {
      ...READY_ONBOARDING,
      servicesConfigured: false,
      scheduleConfigured: false,
      knowledgeConfigured: false,
      humanTransferConfigured: false,
      readyForCalls: true,
      nextStep: 'OPTIONAL_HUMAN_TRANSFER'
    } });
    await page.goto('/app');

    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Configura tu negocio' })).toHaveCount(0);
    expect(api.requests.some(request => request.url.includes('/operations/dashboard'))).toBe(true);
    expect(api.attentionMutations()).toBe(0);
  });

  test('missing readiness flag fails closed and never renders an empty Home', async ({ page }) => {
    const api = await bootHome(page, { onboarding: {
      ...INCOMPLETE_ONBOARDING, readyForCalls: undefined
    } });
    await page.goto('/app');
    const alert = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(alert).toContainText('No pudimos verificar la configuración de tu negocio.');
    await expect(alert.getByRole('button', { name: 'Reintentar' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Configura tu negocio' })).toHaveCount(0);
    expect(api.requests.some(request => request.url.includes('/operations/dashboard'))).toBe(false);
  });

  test('malformed onboarding step flags fail closed instead of declaring 100% progress', async ({ page }) => {
    const api = await bootHome(page, { onboarding: {
      ...INCOMPLETE_ONBOARDING, phoneConfigured: 'yes'
    } });
    await page.goto('/app');
    await expect(page.locator('main[data-visual-page="home"] [role="alert"]'))
      .toContainText('No pudimos verificar la configuración de tu negocio.');
    await expect(page.getByRole('region', { name: 'Configura tu negocio' })).toHaveCount(0);
    expect(api.requests.some(request => request.url.includes('/operations/dashboard'))).toBe(false);
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

  test('owner onboarding outage fails closed without loading unverified business metrics', async ({ page }) => {
    const api = await bootHome(page, { onboardingError: 503 });

    await page.goto('/app');

    const error = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(error).toContainText('No pudimos verificar la configuración de tu negocio.');
    await expect(error.getByRole('button', { name: 'Reintentar' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);

    const apiRequests = api.requests.filter(request => request.url.includes('/api/v1/'));
    expect(apiRequests.some(request => request.url.includes('/operations/dashboard'))).toBe(false);
    expect(apiRequests.some(request => request.url.includes('/commercial/analytics'))).toBe(false);
    expect(apiRequests.some(request => request.url.includes('/operations/attention'))).toBe(false);
    expect(apiRequests.every(request => request.method === 'GET')).toBe(true);
  });

  test('owner explicitly retries a transient onboarding outage and sees authoritative results', async ({ page }) => {
    const api = await bootHome(page, { onboardingFailures: 2 });

    await page.goto('/app');
    const error = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(error).toContainText('No pudimos verificar la configuración de tu negocio.');
    expect(api.requests.some(request => request.url.includes('/operations/dashboard'))).toBe(false);

    await error.getByRole('button', { name: 'Reintentar' }).click();

    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('6');
    await expect(error).toHaveCount(0);
    expect(api.onboardingRequests()).toBeGreaterThanOrEqual(3);
  });

  test('operational dashboard outage does not invent results and can recover on retry', async ({ page }) => {
    const api = await bootHome(page, { operationsFailures: 2 });

    await page.goto('/app');
    const error = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(error).toContainText('No pudimos cargar el resumen operativo.');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' })).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);

    await error.getByRole('button', { name: 'Reintentar' }).click();

    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toContainText('6');
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' })).toBeVisible();
    await expect(error).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);
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
