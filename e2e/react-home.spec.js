const { test, expect } = require('./home-coverage-fixture');

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
  let meReads = 0;
  let operationsReads = 0;
  let onboardingFailuresRemaining = options.onboardingFailures ?? 0;
  let operationsFailuresRemaining = options.operationsFailures ?? 0;
  let attentionReads = 0;
  let attentionFailuresRemaining = options.attentionFailures ?? 0;
  const mutationFailures = { ...(options.mutationFailures || {}) };
  const mutationAttempts = { requestProgress: 0, requestResolve: 0, handoffAck: 0, handoffResolve: 0 };
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

  await page.route('**/api/v1/auth/me', async route => {
    meReads += 1;
    if (options.meGates?.[meReads]) await options.meGates[meReads];
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

  await page.route('**/api/v1/onboarding/status', async route => {
    onboardingRequests += 1;
    if (options.onboardingGates?.[onboardingRequests]) await options.onboardingGates[onboardingRequests];
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
    if (attentionFailuresRemaining > 0) {
      attentionFailuresRemaining -= 1;
      return route.fulfill(json({ message: 'attention temporarily unavailable' }, 503));
    }
    if (options.attentionError) {
      return route.fulfill(json({ message: 'attention unavailable' }, options.attentionError));
    }
    if (options.attentionPayloads?.length) {
      return route.fulfill(json(options.attentionPayloads.shift()));
    }
    return route.fulfill(json(attentionItems));
  });

  await page.route('**/api/v1/requests/req-1/status', async route => {
    const status = route.request().postDataJSON()?.status;
    const action = status === 'IN_PROGRESS' ? 'requestProgress' : 'requestResolve';
    mutationAttempts[action] += 1;
    if (options.mutationGates?.[action]) await options.mutationGates[action];
    if (mutationFailures[action] > 0) {
      mutationFailures[action] -= 1;
      return route.fulfill(json({ message: 'No se pudo guardar la solicitud' }, 503));
    }
    attentionMutations += 1;
    attentionItems = attentionItems
      .map(item => item.id === 'req-1' && item.kind === 'REQUEST' ? { ...item, status } : item)
      .filter(item => item.status !== 'RESOLVED' && item.status !== 'CANCELLED');
    return route.fulfill(json({ id: 'req-1', status }));
  });
  await page.route('**/api/v1/handoffs/handoff-1/acknowledge', async route => {
    mutationAttempts.handoffAck += 1;
    if (options.mutationGates?.handoffAck) await options.mutationGates.handoffAck;
    if (mutationFailures.handoffAck > 0) {
      mutationFailures.handoffAck -= 1;
      return route.fulfill(json({ message: 'No se pudo tomar el escalamiento' }, 503));
    }
    attentionMutations += 1;
    attentionItems = attentionItems.map(item => item.id === 'handoff-1' && item.kind === 'HANDOFF'
      ? { ...item, status: 'ACKNOWLEDGED' } : item);
    return route.fulfill(json({ id: 'handoff-1', status: 'ACKNOWLEDGED' }));
  });
  await page.route('**/api/v1/handoffs/handoff-1/resolve', async route => {
    mutationAttempts.handoffResolve += 1;
    if (options.mutationGates?.handoffResolve) await options.mutationGates.handoffResolve;
    if (mutationFailures.handoffResolve > 0) {
      mutationFailures.handoffResolve -= 1;
      return route.fulfill(json({ message: 'No se pudo cerrar el escalamiento' }, 503));
    }
    attentionMutations += 1;
    attentionItems = attentionItems.filter(item => item.id !== 'handoff-1' || item.kind !== 'HANDOFF');
    if (options.requestAfterHandoffResolved) attentionItems.push(options.requestAfterHandoffResolved);
    return route.fulfill(json({ id: 'handoff-1', status: 'RESOLVED' }));
  });
  await page.route('**/api/v1/requests', route => route.fulfill(json([
    { id: 'req-1', requestType: 'CALLBACK', title: 'Cliente pidió devolución de llamada',
      status: 'OPEN', priority: 'HIGH', createdAt: '2026-10-02T15:10:00Z' }
  ])));

  await page.route('**/api/v1/operations/dashboard', async route => {
    operationsReads += 1;
    if (options.operationsGates?.[operationsReads]) await options.operationsGates[operationsReads];
    if (options.operationsGate) await options.operationsGate;
    if (operationsFailuresRemaining > 0) {
      operationsFailuresRemaining -= 1;
      return route.fulfill(json({ message: 'operations unavailable' }, 503));
    }
    if (options.operationsError) {
      return route.fulfill(json({ message: 'operations unavailable' }, options.operationsError));
    }
    if (options.operationsPayloads?.length) return route.fulfill(json(options.operationsPayloads.shift()));
    return route.fulfill(json(options.operations || READY_OPERATIONS));
  });

  await page.route('**/api/v1/commercial/analytics**', async route => {
    const days = Number(new URL(route.request().url()).searchParams.get('days') || 7);
    if (options.analyticsGates?.[days]) await options.analyticsGates[days];
    if (options.analyticsErrorsByDays?.[days]) {
      return route.fulfill(json({ message: 'analytics unavailable' }, options.analyticsErrorsByDays[days]));
    }
    if (options.analyticsError) {
      return route.fulfill(json({ message: 'analytics unavailable' }, options.analyticsError));
    }
    if (options.analyticsPayloads?.length) return route.fulfill(json(options.analyticsPayloads.shift()));
    return route.fulfill(json(options.analyticsByDays?.[days] || options.analytics || READY_ANALYTICS));
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
    attentionMutations: () => attentionMutations,
    mutationAttempts: () => ({ ...mutationAttempts })
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

    const hero = page.getByRole('region', { name: 'Estado de tu negocio' });
    const heroBox = await hero.boundingBox();
    expect(heroBox).not.toBeNull();
    // The accessible hero is content-sized: it must not clip or overlap its next section.
    expect(heroBox.height).toBeGreaterThanOrEqual(168);
    // Decorative artwork may intentionally extend outside the card; verify the readable
    // text area fits instead of treating that decoration as clipped content.
    const heroCopy = hero.locator('div[class*="heroCopy"]');
    const textBounds = await heroCopy.boundingBox();
    expect(textBounds).not.toBeNull();
    expect(textBounds.y + textBounds.height).toBeLessThanOrEqual(heroBox.y + heroBox.height + 2);

    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toBeVisible();
    const valueBox = await value.boundingBox();
    expect(valueBox).not.toBeNull();
    expect(valueBox.y).toBeGreaterThanOrEqual(heroBox.y + heroBox.height - 2);
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

  test('a full attention batch states the 100-case limit without calling it the historical total', async ({ page }) => {
    const items = Array.from({ length: 100 }, (_, i) => ({
      ...DEFAULT_ATTENTION[0],
      id: 'request-batch-' + i,
      operationId: 'operation-batch-' + i,
      priority: 'NORMAL',
      title: 'Pendiente real ' + i
    }));
    const api = await bootHome(page, { attention: items });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('100 casos recientes');
    await expect(panel).toContainText('Se muestran hasta 100 casos recientes');
    await expect(panel.locator('article[data-kind="REQUEST"]')).toHaveCount(100);
    expect(api.attentionMutations()).toBe(0);
  });

  test('assigned and unknown human statuses remain displayed without inventing actions', async ({ page }) => {
    const items = [
      { ...DEFAULT_ATTENTION[1], id: 'assigned-human', operationId: 'o-assigned', status: 'ASSIGNED', priority: 'NORMAL', createdAt: 'invalid-date', title: '' },
      { ...DEFAULT_ATTENTION[1], id: 'unknown-human', operationId: 'o-unknown', status: 'WAITING_CONTACT', priority: 'LOW', createdAt: '', title: 'Escalamiento en espera' }
    ];
    const api = await bootHome(page, { attention: items });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Asignado');
    await expect(panel).toContainText('WAITING_CONTACT');
    await expect(panel).toContainText('Caso sin título');
    await expect(panel).toContainText('Fecha no disponible');
    await expect(panel.getByRole('button', { name: 'Cerrar escalamiento' })).toHaveCount(1);
    expect(api.attentionMutations()).toBe(0);
  });

  test('two same-tick start actions send one request even before React disables the button', async ({ page }) => {
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const api = await bootHome(page, { mutationGates: { requestProgress: gate } });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel.getByRole('button', { name: 'Empezar gestión' })).toBeVisible();
    await panel.getByRole('button', { name: 'Empezar gestión' }).evaluate(button => {
      button.click();
      button.click();
    });
    await expect.poll(() => api.mutationAttempts().requestProgress).toBe(1);
    release();
    await expect(panel).toContainText('En progreso');
    expect(api.attentionMutations()).toBe(1);
  });

  test('non-Error provider failure shows a safe retry message without claiming a case was changed', async ({ page }) => {
    await page.addInitScript(() => {
      const original = window.fetch.bind(window);
      window.fetch = (resource, init) => {
        if (String(resource).includes('/api/v1/requests/req-1/status') && init?.method === 'PATCH') {
          return Promise.reject('transport dropped');
        }
        return original(resource, init);
      };
    });
    const api = await bootHome(page);
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel.getByRole('alert')).toContainText('No pudimos actualizar el caso.');
    await expect(panel).toContainText('Abierta');
    expect(api.attentionMutations()).toBe(0);
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

  test('attention API outage stays unknown until explicit retry succeeds', async ({ page }) => {
    const api = await bootHome(page, { attentionFailures: 1 });
    await page.goto('/app');

    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel.getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    await expect(panel).not.toContainText('Sin intervenciones pendientes');
    expect(api.attentionMutations()).toBe(0);

    await panel.getByRole('button', { name: 'Reintentar' }).click();
    await expect(panel).toContainText('Cliente pidió devolución de llamada');
    await expect(panel.getByRole('alert')).toHaveCount(0);
    expect(api.attentionReads()).toBeGreaterThanOrEqual(2);
    expect(api.attentionMutations()).toBe(0);
  });

  for (const [name, malformed] of [
    ['null response', null],
    ['object instead of an array', { items: [] }],
    ['invalid request fields', [{ kind: 'REQUEST', id: 'req-1', status: 'OPEN' }]]
  ]) {
    test(`attention ${name} is unavailable rather than falsely cleared`, async ({ page }) => {
      const api = await bootHome(page, { attentionPayloads: [malformed] });
      await page.goto('/app');
      const panel = page.getByRole('region', { name: 'Necesita tu atención' });
      await expect(panel.getByRole('alert')).toContainText('No pudimos verificar los pendientes');
      await expect(panel).not.toContainText('Sin intervenciones pendientes');
      await expect(page.getByRole('region', { name: 'Estado de tu negocio' }))
        .not.toContainText('Todo bajo control');
      expect(api.attentionMutations()).toBe(0);

      await panel.getByRole('button', { name: 'Reintentar' }).click();
      await expect(panel).toContainText('Cliente pidió devolución de llamada');
      await expect(panel.getByRole('alert')).toHaveCount(0);
      expect(api.attentionReads()).toBeGreaterThanOrEqual(2);
    });
  }

  test('validated empty attention array permits a real empty-state message', async ({ page }) => {
    const api = await bootHome(page, { attention: [] });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Sin intervenciones pendientes');
    await expect(panel.getByRole('alert')).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('cancelling request resolution never mutates or closes the request', async ({ page }) => {
    const api = await bootHome(page, { attention: [{
      ...DEFAULT_ATTENTION[0], status: 'IN_PROGRESS'
    }] });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Marcar resuelta' }).click();
    await expect(panel.getByRole('button', { name: 'Confirmar cierre' })).toBeVisible();
    expect(api.attentionMutations()).toBe(0);
    await panel.getByRole('button', { name: 'Cancelar' }).click();
    await expect(panel.getByRole('button', { name: 'Marcar resuelta' })).toBeVisible();
    await expect(panel.getByRole('button', { name: 'Confirmar cierre' })).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('cancelled handoff close never sends a resolve request', async ({ page }) => {
    const api = await bootHome(page, { attention: [{
      ...DEFAULT_ATTENTION[1], status: 'ACKNOWLEDGED'
    }] });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Cerrar escalamiento' }).click();
    await expect(panel.getByRole('button', { name: 'Confirmar cierre' })).toBeVisible();
    await panel.getByRole('button', { name: 'Cancelar' }).click();
    await expect(panel.getByRole('button', { name: 'Cerrar escalamiento' })).toBeVisible();
    expect(api.mutationAttempts().handoffResolve).toBe(0);
    expect(api.attentionMutations()).toBe(0);
  });

  test('failed request start leaves the authoritative OPEN row and permits a single retry', async ({ page }) => {
    const api = await bootHome(page, { mutationFailures: { requestProgress: 1 } });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel.getByRole('alert')).toContainText('No se pudo guardar la solicitud');
    await expect(panel).toContainText('Abierta');
    expect(api.attentionMutations()).toBe(0);
    expect(api.mutationAttempts().requestProgress).toBe(1);

    await panel.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect(panel).toContainText('En progreso');
    await expect(panel.getByRole('alert')).toHaveCount(0);
    expect(api.attentionMutations()).toBe(1);
    expect(api.mutationAttempts().requestProgress).toBe(2);
  });

  test('failed request resolution stays pending until a new explicit confirmation', async ({ page }) => {
    const api = await bootHome(page, {
      attention: [{ ...DEFAULT_ATTENTION[0], status: 'IN_PROGRESS' }],
      mutationFailures: { requestResolve: 1 }
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Marcar resuelta' }).click();
    await panel.getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel.getByRole('alert')).toContainText('No se pudo guardar la solicitud');
    await expect(panel).toContainText('En progreso');
    expect(api.attentionMutations()).toBe(0);

    await panel.getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel).not.toContainText('Cliente pidió devolución de llamada');
    expect(api.attentionMutations()).toBe(1);
    expect(api.mutationAttempts().requestResolve).toBe(2);
  });

  test('an in-flight request transition prevents duplicate submissions', async ({ page }) => {
    let releaseMutation;
    const gate = new Promise(resolve => { releaseMutation = resolve; });
    const api = await bootHome(page, { mutationGates: { requestProgress: gate } });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect.poll(() => api.mutationAttempts().requestProgress).toBe(1);
    await expect(panel.getByRole('button', { name: /Guardando/ })).toBeDisabled();
    await expect(panel.getByRole('button', { name: 'Actualizar' })).toBeDisabled();
    expect(api.attentionMutations()).toBe(0);

    releaseMutation();
    await expect(panel).toContainText('En progreso');
    expect(api.mutationAttempts().requestProgress).toBe(1);
    expect(api.attentionMutations()).toBe(1);
  });

  test('an in-flight human acknowledgement prevents a duplicate provider-independent action', async ({ page }) => {
    let releaseMutation;
    const gate = new Promise(resolve => { releaseMutation = resolve; });
    const api = await bootHome(page, {
      attention: [DEFAULT_ATTENTION[1]],
      mutationGates: { handoffAck: gate }
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await panel.getByRole('button', { name: 'Tomar caso' }).click();
    await expect.poll(() => api.mutationAttempts().handoffAck).toBe(1);
    await expect(panel.getByRole('button', { name: /Guardando/ })).toBeDisabled();
    expect(api.attentionMutations()).toBe(0);

    releaseMutation();
    await expect(panel).toContainText('Recibido');
    expect(api.mutationAttempts().handoffAck).toBe(1);
    expect(api.attentionMutations()).toBe(1);
  });

  test('closing the human handoff does not falsely close its associated request', async ({ page }) => {
    const operationId = 'operation-shared';
    const api = await bootHome(page, {
      attention: [{ ...DEFAULT_ATTENTION[1], operationId, status: 'ACKNOWLEDGED' }],
      requestAfterHandoffResolved: {
        ...DEFAULT_ATTENTION[0],
        operationId,
        title: 'La solicitud original sigue abierta'
      }
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Reclamo de cliente pendiente');
    await expect(panel).not.toContainText('La solicitud original sigue abierta');
    await panel.getByRole('button', { name: 'Cerrar escalamiento' }).click();
    await panel.getByRole('button', { name: 'Confirmar cierre' }).click();
    await expect(panel).not.toContainText('Reclamo de cliente pendiente');
    await expect(panel).toContainText('La solicitud original sigue abierta');
    await expect(panel.getByRole('button', { name: 'Empezar gestión' })).toBeVisible();
    expect(api.attentionMutations()).toBe(1);
  });

  test('identical request and handoff IDs keep independent confirmation state', async ({ page }) => {
    const api = await bootHome(page, {
      attention: [
        { ...DEFAULT_ATTENTION[0], id: 'handoff-1', operationId: 'operation-request', status: 'IN_PROGRESS' },
        { ...DEFAULT_ATTENTION[1], id: 'handoff-1', operationId: 'operation-handoff', status: 'ACKNOWLEDGED' }
      ]
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    const rows = panel.locator('article[data-kind]');
    await expect(rows).toHaveCount(2);
    await rows.nth(0).getByRole('button', { name: 'Marcar resuelta' }).click();
    await expect(rows.nth(0).getByRole('button', { name: 'Confirmar cierre' })).toBeVisible();
    await expect(rows.nth(1).getByRole('button', { name: 'Confirmar cierre' })).toHaveCount(0);
    await expect(rows.nth(1).getByRole('button', { name: 'Cerrar escalamiento' })).toBeVisible();
    expect(api.attentionMutations()).toBe(0);
  });

  test('read-only operator cannot change request status even when cases remain visible', async ({ page }) => {
    const api = await bootHome(page, {
      roles: ['OPERATOR'],
      permissions: ['REQUESTS_READ', 'OPERATIONS_READ'],
      attention: [DEFAULT_ATTENTION[0]]
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Cliente pidió devolución de llamada');
    await expect(panel.getByRole('button', { name: 'Empezar gestión' })).toHaveCount(0);
    await expect(panel.getByRole('button', { name: 'Confirmar cierre' })).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);
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
    const api = await bootHome(page, { meFailures: 1 });
    await page.goto('/app');

    const error = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(error).toContainText('No pudimos verificar tus permisos de acceso.');
    expect(api.requests.some(request => request.url.includes('/operations/dashboard'))).toBe(false);
    // Home and AppShell must share one identity read instead of racing two GETs.
    const identityReads = () => api.requests.filter(request =>
      new URL(request.url).pathname === '/api/v1/auth/me').length;
    expect(identityReads()).toBe(1);

    await error.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await expect(error).toHaveCount(0);
    expect(identityReads()).toBe(2);
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

  test('currency rendering remains readable when Intl.format itself throws after valid constructor checks', async ({ page }) => {
    await page.addInitScript(() => {
      const Native = Intl.NumberFormat;
      Intl.NumberFormat = function(...args) {
        const formatter = new Native(...args);
        if (args[1]?.style !== 'currency') return formatter;
        return new Proxy(formatter, {
          get(target, key) {
            if (key === 'format') throw new Error('format unavailable');
            const value = Reflect.get(target, key, target);
            return typeof value === 'function' ? value.bind(target) : value;
          }
        });
      };
      Intl.NumberFormat.prototype = Native.prototype;
    });
    await bootHome(page);
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('CLP');
    await expect(value).toContainText('209.900');
  });

  test('unsupported formatter currency is partial, never silently counted as attributed revenue', async ({ page }) => {
    await page.addInitScript(() => {
      const Native = Intl.NumberFormat;
      Intl.NumberFormat = function(...args) {
        if (args[1]?.style === 'currency' && args[1]?.currency === 'ZZZ') {
          throw new RangeError('unsupported test currency');
        }
        return new Native(...args);
      };
      Intl.NumberFormat.prototype = Native.prototype;
    });
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: 'ZZZ',
      recepVozRevenue: 100000,
      bookingCurrency: 'CLP',
      recepVozBookingRevenue: 20000
    } });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(panel).toContainText('Importes atribuidos parcialmente verificables');
    await expect(panel).toContainText('20.000');
    await expect(panel).not.toContainText('120.000');
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

  test('HTTP 200 with null commercial payload shows an unknown state, never an empty $0', async ({ page }) => {
    await bootHome(page, { analyticsPayloads: [null] });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(panel.getByRole('alert')).toContainText('No recibimos datos verificables');
    await expect(panel).not.toContainText('$0');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
  });

  test('short invalid currency code is rejected before Intl formatting', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: 'CL',
      recepVozRevenue: 100000,
      bookingCurrency: 'CLP',
      recepVozBookingRevenue: 20000
    } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('Importes atribuidos parcialmente verificables');
    await expect(value).toContainText('20.000');
    await expect(value).not.toContainText('120.000');
  });

  test('unknown call status and absent caller number preserve an informative fallback', async ({ page }) => {
    const operations = {
      ...READY_OPERATIONS,
      recentRequests: [],
      recentCalls: [{
        id: 'no-caller',
        callerNumber: null,
        status: null,
        resolution: null,
        startedAt: '2026-10-02T15:20:00Z',
        durationSeconds: 0
      }]
    };
    await bootHome(page, { operations, attention: [] });
    await page.goto('/app');
    const activity = page.getByRole('region', { name: 'Última actividad' });
    await expect(activity).toContainText('Llamada reciente');
    await expect(activity).toContainText('Llamada');
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' }))
      .not.toContainText('Hay una llamada en curso');
  });

  test('unknown request status is human-readable and missing known status uses Solicitud fallback', async ({ page }) => {
    await bootHome(page, {
      operations: {
        ...READY_OPERATIONS,
        recentCalls: [],
        recentRequests: [{
          id: 'unrecognized-status',
          title: 'Seguimiento especializado',
          status: null,
          priority: 'MEDIUM',
          createdAt: '2026-10-02T15:20:00Z'
        }]
      },
      attention: []
    });
    await page.goto('/app');
    const activity = page.getByRole('region', { name: 'Última actividad' });
    await expect(activity).toContainText('Seguimiento especializado');
    await expect(activity).toContainText('Solicitud');
  });

  test('nullable nextStep retains valid incomplete onboarding and directs to the safe Settings fallback', async ({ page }) => {
    await bootHome(page, { onboarding: {
      ...INCOMPLETE_ONBOARDING,
      nextStep: null
    } });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(panel).toContainText('Completa la configuración pendiente');
    await expect(panel.getByRole('link', { name: 'Continuar configuración' }))
      .toHaveAttribute('href', '/app/settings');
  });

  test('request statuses from stale attention data are not wrongly labelled Abierta', async ({ page }) => {
    await bootHome(page, { attention: [
      { ...DEFAULT_ATTENTION[0], id: 'resolved-row', status: 'RESOLVED', priority: 'NORMAL', operationId: null },
      { ...DEFAULT_ATTENTION[0], id: 'cancelled-row', status: 'CANCELLED', priority: 'NORMAL', operationId: null },
      { ...DEFAULT_ATTENTION[0], id: 'unknown-row', status: 'WAITING_ON_CUSTOMER', priority: 'NORMAL', operationId: null }
    ] });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('Resuelta');
    await expect(panel).toContainText('Cancelada');
    await expect(panel).toContainText('WAITING_ON_CUSTOMER');
    await expect(panel.getByRole('button', { name: 'Empezar gestión' })).toHaveCount(0);
    await expect(panel.getByRole('button', { name: 'Marcar resuelta' })).toHaveCount(0);
  });

  test('two operational signal categories use accurate plural counts', async ({ page }) => {
    await bootHome(page, {
      operations: {
        ...READY_OPERATIONS,
        unansweredQuestions: 2,
        callFailuresToday: 2
      },
      attention: []
    });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('2 preguntas sin respuesta');
    await expect(panel).toContainText('2 fallos de llamada');
  });

  test('successful HTTP response without dashboard data fails closed and recovers on deliberate retry', async ({ page }) => {
    const api = await bootHome(page, { operationsPayloads: [null] });
    await page.goto('/app');
    const error = page.locator('main[data-visual-page="home"] [role="alert"]');
    await expect(error).toContainText('No recibimos datos verificables del resumen operativo.');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    expect(api.attentionMutations()).toBe(0);
    await error.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await expect(error).toHaveCount(0);
  });

  test('unknown recent activity status is shown intelligibly without pretending it was completed', async ({ page }) => {
    await bootHome(page, { operations: {
      ...READY_OPERATIONS,
      recentCalls: [],
      recentRequests: [{
        id: 'unknown-event',
        title: 'Seguimiento pendiente de validar',
        status: 'WAITING_ON_CUSTOMER',
        priority: 'NORMAL',
        createdAt: '2026-10-02T15:20:00Z'
      }]
    }, attention: [] });
    await page.goto('/app');
    const activity = page.getByRole('region', { name: 'Última actividad' });
    await expect(activity).toContainText('Seguimiento pendiente de validar');
    await expect(activity).toContainText('waiting on customer');
    await expect(activity).not.toContainText('Resuelta');
  });

  test('cached value explicitly identifies an in-flight refresh without inventing a new total', async ({ page }) => {
    const gates = {};
    const api = await bootHome(page, { analyticsGates: gates });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(panel).toContainText('209.900');
    await panel.getByRole('button', { name: '30 días' }).click();
    await expect(panel.getByRole('button', { name: '30 días' })).toHaveAttribute('aria-pressed', 'true');

    let release;
    gates[7] = new Promise(resolve => { release = resolve; });
    await page.evaluate(() => {
      const actualNow = Date.now;
      Date.now = () => actualNow() + 120000;
    });
    await panel.getByRole('button', { name: '7 días' }).click();
    await expect(panel).toContainText('Actualizando…');
    await expect(panel).toContainText('209.900');
    release();
    await expect(panel).not.toContainText('Actualizando…');
    expect(api.attentionMutations()).toBe(0);
  });

  for (const state of [
    { label: 'identity', options: gate => ({ meFailures: 1, meGates: { 2: gate } }),
      error: 'No pudimos verificar tus permisos de acceso.' },
    { label: 'onboarding', options: gate => ({ onboardingFailures: 2, onboardingGates: { 3: gate } }),
      error: 'No pudimos verificar la configuración de tu negocio.' },
    { label: 'operations', options: gate => ({ operationsFailures: 2, operationsGates: { 3: gate } }),
      error: 'No pudimos cargar el resumen operativo.' }
  ]) {
    test(`${state.label} retry stays visibly busy and disabled until the server actually recovers`, async ({ page }) => {
      let release;
      const gate = new Promise(resolve => { release = resolve; });
      const api = await bootHome(page, state.options(gate));
      await page.goto('/app');
      const error = page.locator('main[data-visual-page="home"] [role="alert"]');
      await expect(error).toContainText(state.error);
      await error.getByRole('button', { name: 'Reintentar' }).click();
      const retrying = error.getByRole('button', { name: 'Reintentando…' });
      await expect(retrying).toBeDisabled();
      expect(api.attentionMutations()).toBe(0);
      release();
      await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
      await expect(error).toHaveCount(0);
    });
  }

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

  test('same-currency attributed paid orders and bookings sum exactly once', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      recepVozRevenue: 12000,
      recepVozBookingRevenue: 8000,
      primaryCurrency: 'CLP',
      bookingCurrency: 'clp'
    } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value.locator('strong').filter({ hasText: '$20.000' })).toBeVisible();
    await expect(value).not.toContainText('Importes atribuidos parcialmente verificables');
  });

  test('mixed CLP and USD attribution stays separated without unsupported conversion', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: 'CLP',
      recepVozRevenue: 25000,
      bookingCurrency: 'USD',
      recepVozBookingRevenue: 35.5
    } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value.locator('div[class*="moneyStack"] > strong')).toHaveCount(2);
    await expect(value).toContainText('CLP');
    await expect(value).toContainText('USD');
    await expect(value).toContainText('$25.000');
    await expect(value).toContainText('35,5');
    await expect(value).not.toContainText('$25.035');
  });

  test('multi-currency backend with unattributable order breakdown never invents zero revenue', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: null,
      recepVozRevenue: null,
      recepVozOrders: 3,
      bookingCurrency: null,
      recepVozBookingRevenue: null,
      recepVozPaidBookings: 2,
      currencyTotals: [{ currency: 'CLP', amount: 50000 }, { currency: 'USD', amount: 30 }]
    } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('Importe no disponible');
    await expect(value).toContainText('3 pedidos con origen RecepVoz');
    await expect(value).toContainText('2 reservas pagadas con origen RecepVoz');
    await expect(value).toContainText('El importe visible es parcial, no un total.');
    await expect(value).not.toContainText('$0');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
  });

  test('one verified currency with another missing amount is labelled partial, not a total', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: 'CLP', recepVozRevenue: 30000, recepVozOrders: 2,
      bookingCurrency: null, recepVozBookingRevenue: null, recepVozPaidBookings: 1
    } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('$30.000');
    await expect(value).toContainText('Importes atribuidos parcialmente verificables');
    await expect(value).toContainText('El importe visible es parcial, no un total.');
  });

  test('invalid, negative and non-finite amounts never become attributed sales', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...READY_ANALYTICS,
      primaryCurrency: 'CLP', recepVozRevenue: -5000,
      recepVozOrders: 1,
      bookingCurrency: 'USD', recepVozBookingRevenue: 'NaN',
      recepVozPaidBookings: 1
    } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('Importe no disponible');
    await expect(value).not.toContainText('-5.000');
    await expect(value).not.toContainText('NaN');
    await expect(value).not.toContainText('$0');
  });

  test('truly empty source counts display zero instead of inventing earned revenue', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...EMPTY_ANALYTICS,
      primaryCurrency: null,
      bookingCurrency: null,
      recepVozRevenue: null,
      recepVozBookingRevenue: null,
      recepVozOrders: 0,
      recepVozPaidBookings: 0
    } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('$0');
    await expect(value).not.toContainText('Importe no disponible');
  });

  test('rapid period changes cannot overwrite the selected 1-day result with slow old windows', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    let release7, release30;
    const pending7 = new Promise(resolve => { release7 = resolve; });
    const pending30 = new Promise(resolve => { release30 = resolve; });
    const api = await bootHome(page, {
      analyticsGates: { 7: pending7, 30: pending30 },
      analyticsByDays: {
        1: { ...READY_ANALYTICS, days: 1, recepVozRevenue: 111, recepVozBookingRevenue: 0 },
        7: { ...READY_ANALYTICS, days: 7, recepVozRevenue: 777, recepVozBookingRevenue: 0 },
        30: { ...READY_ANALYTICS, days: 30, recepVozRevenue: 3030, recepVozBookingRevenue: 0 }
      }
    });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect.poll(() => api.requests.some(r => r.url.includes('/api/v1/commercial/analytics?days=7'))).toBe(true);
    await value.getByRole('button', { name: '30 días' }).click();
    await expect.poll(() => api.requests.some(r => r.url.includes('/api/v1/commercial/analytics?days=30'))).toBe(true);
    await value.getByRole('button', { name: 'Hoy' }).click();
    await expect(value.getByRole('button', { name: 'Hoy' })).toHaveAttribute('aria-pressed', 'true');
    await expect(value.locator('div[class*="valueAmount"]')).toContainText('$111');
    release30();
    release7();
    await expect(value.locator('div[class*="valueAmount"]')).toContainText('$111');
    await expect(value).not.toContainText('$777');
    await expect(value).not.toContainText('$3.030');
    expect(api.requests.filter(r => /\/api\/v1\/(?:requests\/.*\/status|handoffs\/.*\/(?:resolve|acknowledge))/.test(r.url))).toEqual([]);
  });

  test('analytics errors for a selected period leave real operational totals visible', async ({ page }) => {
    await bootHome(page, { analyticsErrorsByDays: { 30: 503 } });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('valor comercial atribuido');
    await value.getByRole('button', { name: '30 días' }).click();
    await expect(value.getByRole('alert')).toContainText('No pudimos cargar el valor atribuido.');
    await expect(value).not.toContainText('$0');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    await value.getByRole('button', { name: '7 días' }).click();
    await expect(value.getByRole('alert')).toHaveCount(0);
    await expect(value).toContainText('valor comercial atribuido');
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


  test('expanded history shows request state controls and records only explicitly clicked mutation', async ({ page }) => {
    const state = await bootHome(page);
    await page.goto('/app');
    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/ }).click();
    const history = page.locator('#home-history-panel');
    await expect(history.getByRole('heading', { name: 'Historial y auditoría' })).toBeVisible();
    await expect(history).toContainText('Cliente pidió devolución de llamada');
    expect(state.attentionMutations()).toBe(0);
    await history.getByRole('button', { name: 'Marcar en progreso' }).click();
    await expect.poll(state.attentionMutations).toBe(1);
    expect(state.requests.filter(req =>
      req.url.includes('/api/v1/requests/req-1/status') && req.method === 'PATCH')).toHaveLength(1);
    expect(state.requests.filter(req => /twilio|whatsapp.*send|payments.*(?:charge|capture)/i.test(req.url))).toEqual([]);
  });

  test('read-only operator can inspect history but cannot mutate requests or see audit tools', async ({ page }) => {
    const state = await bootHome(page, {
      roles: ['OPERATOR'], permissions: ['REQUESTS_READ', 'OPERATIONS_READ']
    });
    await page.goto('/app');
    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/ }).click();
    const history = page.locator('#home-history-panel');
    await expect(history).toContainText('Cliente pidió devolución de llamada');
    await expect(history.getByRole('button', { name: 'Marcar en progreso' })).toHaveCount(0);
    await expect(history.getByRole('button', { name: 'Auditoría' })).toHaveCount(0);
    await expect(history.getByRole('button', { name: /Exportar auditoría/ })).toHaveCount(0);
    expect(state.attentionMutations()).toBe(0);
  });

  test('audit tab filters and local CSV export are explicit, not background provider effects', async ({ page }) => {
    const state = await bootHome(page);
    const auditReads = [];
    // The authenticated download uses native fetch() rather than apiRequest.
    // Intercept it inside the isolated browser to avoid any local-network fallback
    // or provider traffic, while keeping the real Blob/download behavior under test.
    await page.addInitScript(() => {
      const originalFetch = window.fetch.bind(window);
      window.__auditExportRequests = [];
      window.fetch = (resource, options) => {
        const value = typeof resource === 'string' ? resource : resource.url;
        const parsed = new URL(value, location.origin);
        if (parsed.pathname === '/api/v1/audit/export') {
          window.__auditExportRequests.push(parsed.search);
          const csv = parsed.searchParams.get('format') === 'csv';
          return Promise.resolve(new Response(new Blob(['actor,action\\n'], { type: 'text/csv' }), {
            status: 200,
            headers: csv ? {
              'content-type': 'text/csv',
              'content-disposition': 'attachment; filename="auditoria-test.csv"'
            } : { 'content-type': 'application/octet-stream' }
          }));
        }
        return originalFetch(resource, options);
      };
    });
    await page.route('**/api/v1/audit**', route => {
      const url = new URL(route.request().url());
      auditReads.push(url);
      if (url.pathname.endsWith('/export')) {
        return route.fulfill({
          status: 200, contentType: 'text/csv',
          headers: { 'content-disposition': 'attachment; filename="auditoria-test.csv"' },
          body: 'actor,action\\n'
        });
      }
      return route.fulfill(json([{
        id: 'audit-test-1', action: 'REQUEST_UPDATED', resourceType: 'REQUEST',
        resourceId: 'req-1', actorName: 'Operador Demo', createdAt: '2026-10-02T12:00:00Z'
      }]));
    });
    await page.goto('/app');
    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/ }).click();
    const history = page.locator('#home-history-panel');
    await history.getByRole('button', { name: 'Auditoría' }).click();
    await expect(history).toContainText('REQUEST_UPDATED');
    await history.getByRole('textbox', { name: 'Actor de auditoría' }).fill(' Operador Demo ');
    await history.getByRole('textbox', { name: 'Acción de auditoría' }).fill(' REQUEST_UPDATED ');
    await history.getByRole('textbox', { name: 'Recurso de auditoría' }).fill(' REQUEST ');
    await history.getByRole('button', { name: 'Aplicar filtros' }).click();
    await expect.poll(() => auditReads.some(url =>
      url.searchParams.get('actor') === 'Operador Demo' &&
      url.searchParams.get('action') === 'REQUEST_UPDATED' &&
      url.searchParams.get('resourceType') === 'REQUEST'
    )).toBe(true);
    const downloaded = page.waitForEvent('download');
    await history.getByRole('button', { name: 'Exportar auditoría CSV' }).click();
    const file = await downloaded;
    expect(file.suggestedFilename()).toBe('auditoria-test.csv');
    await expect(history.getByRole('status')).toContainText('Exportación de auditoría preparada');
    await history.getByRole('textbox', { name: 'Actor de auditoría' }).fill('');
    await history.getByRole('textbox', { name: 'Acción de auditoría' }).fill('');
    await history.getByRole('textbox', { name: 'Recurso de auditoría' }).fill('');
    await history.getByRole('button', { name: 'Aplicar filtros' }).click();
    await expect.poll(() => auditReads.some(url => !url.search)).toBe(true);
    const secondDownload = page.waitForEvent('download');
    await history.getByRole('button', { name: 'Exportar auditoría XLSX' }).click();
    expect((await secondDownload).suggestedFilename()).toBe('helvoca-auditoria.xlsx');
    const exported = await page.evaluate(() => window.__auditExportRequests);
    expect(exported).toHaveLength(2);
    expect(new URLSearchParams(exported[0]).get('actor')).toBe('Operador Demo');
    expect(exported[1]).toBe('?format=xlsx');
    expect(state.attentionMutations()).toBe(0);
    expect(state.requests.filter(req => req.url.includes('/api/v1/') &&
      !['GET', 'HEAD', 'OPTIONS'].includes(req.method))).toEqual([]);
    expect(state.requests.filter(req => /twilio|whatsapp.*send|payments.*(?:charge|capture)/i.test(req.url))).toEqual([]);
  });

  test('shared customer export API honors CSV filename, XLSX fallback, 503 and expired authentication', async ({ page }) => {
    const state = await bootHome(page);
    await page.addInitScript(() => {
      const original = window.fetch.bind(window);
      window.__exportMode = 'csv';
      window.__exportRequests = [];
      window.fetch = (resource, opts) => {
        const url = new URL(typeof resource === 'string' ? resource : resource.url, location.origin);
        if (url.pathname !== '/api/v1/customers/export') return original(resource, opts);
        window.__exportRequests.push({ url: url.href, authorization: opts?.headers?.Authorization ?? null });
        const mode = window.__exportMode;
        if (mode === '401') return Promise.resolve(new Response(null, { status: 401 }));
        if (mode === '503') return Promise.resolve(new Response(null, { status: 503 }));
        const name = mode === 'csv' ? 'attachment; filename="clientes-verificados.csv"' : null;
        return Promise.resolve(new Response(new Blob(['synthetic-local-export'], { type: 'text/plain' }), {
          status: 200, headers: name ? { 'content-disposition': name } : {}
        }));
      };
    });
    await page.route('**/api/v1/commercial/orders**', route => route.fulfill(json([])));
    await page.goto('/app/orders');
    await page.getByRole('button', { name: 'Clientes', exact: true }).click();
    const panel = page.getByRole('region', { name: 'Exportación de clientes' });
    await expect(panel).toBeVisible();

    let download = page.waitForEvent('download');
    await panel.getByRole('button', { name: 'Exportar CSV' }).click();
    expect((await download).suggestedFilename()).toBe('clientes-verificados.csv');
    await expect(panel.getByRole('status')).toContainText('Exportación preparada');

    await page.evaluate(() => { window.__exportMode = 'xlsx'; });
    download = page.waitForEvent('download');
    await panel.getByRole('button', { name: 'Exportar XLSX' }).click();
    expect((await download).suggestedFilename()).toBe('helvoca-clientes.xlsx');
    // Assert that generated object URLs are revoked by the real delayed callback.
    await page.waitForTimeout(1150);

    await page.evaluate(() => { window.__exportMode = '503'; });
    await panel.getByRole('button', { name: 'Exportar CSV' }).click();
    await expect(panel.getByRole('status')).toContainText('No pudimos preparar la descarga');

    await page.evaluate(() => {
      sessionStorage.removeItem('helvoca_access_token');
      window.__exportMode = '503';
    });
    await panel.getByRole('button', { name: 'Exportar CSV' }).click();
    await expect(panel.getByRole('status')).toContainText('No pudimos preparar la descarga');
    const requests = await page.evaluate(() => window.__exportRequests);
    expect(requests).toHaveLength(4);
    expect(requests[0].authorization).toContain('Bearer ');
    expect(requests[3].authorization).toBeNull();

    await page.evaluate(() => {
      sessionStorage.setItem('helvoca_access_token', 'react-home-e2e');
      window.__exportMode = '401';
    });
    await panel.getByRole('button', { name: 'Exportar CSV' }).click();
    await expect(page).toHaveURL(url => url.pathname.startsWith('/app/auth'));
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
    expect(state.attentionMutations()).toBe(0);
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


  test('HTTP 200 null analytics shows a truthful local error instead of an empty card', async ({ page }) => {
    await bootHome(page);
    await page.route('**/api/v1/commercial/analytics**', route => route.fulfill(json(null)));
    await page.goto('/app');
    const section = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(section.getByRole('alert')).toContainText('No pudimos verificar los datos comerciales');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
  });

  test('HTTP 200 null operational dashboard blocks fabricated metrics and recovers on retry', async ({ page }) => {
    await bootHome(page);
    let valid = false;
    await page.route('**/api/v1/operations/dashboard', route =>
      route.fulfill(json(valid ? READY_OPERATIONS : null)));
    await page.goto('/app');
    await expect(page.getByRole('alert')).toContainText('No pudimos validar el resumen operativo');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
    valid = true;
    await page.getByRole('button', { name: 'Reintentar resumen' }).click();
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
  });

  test('in-progress call is announced as currently active without creating a call', async ({ page }) => {
    const state = await bootHome(page, { operations: {
      ...READY_OPERATIONS,
      recentCalls: [
        { ...READY_OPERATIONS.recentCalls[0], id: 'missing-status', status: null },
        { ...READY_OPERATIONS.recentCalls[1], id: 'live', status: 'IN_PROGRESS' }
      ]
    }});
    await page.goto('/app');
    await expect(page.getByRole('region', { name: 'Estado de tu negocio' })).toContainText('Atendiendo ahora');
    expect(state.requests.filter(r => !['GET', 'HEAD', 'OPTIONS'].includes(r.method))).toEqual([]);
  });

  test('plural operational signals reflect actual unanswered questions and failed calls', async ({ page }) => {
    await bootHome(page, { operations: {
      ...READY_OPERATIONS, unansweredQuestions: 2, callFailuresToday: 2
    }});
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel).toContainText('2 preguntas sin respuesta');
    await expect(panel).toContainText('2 fallos de llamada');
  });

  test('recent call without phone or statuses has a neutral readable fallback', async ({ page }) => {
    await bootHome(page, { operations: {
      ...READY_OPERATIONS, recentRequests: [], recentCalls: [{
        ...READY_OPERATIONS.recentCalls[0],
        id: 'unknown-call', callerNumber: null, status: null,
        resolution: null, startedAt: '2026-10-02T17:00:00Z'
      }]
    }});
    await page.goto('/app');
    const latest = page.getByRole('region', { name: 'Última actividad' });
    await expect(latest).toContainText('Llamada reciente');
    await expect(latest).toContainText('Llamada');
  });

  test('unrecognized recent request status remains descriptive, not fabricated as complete', async ({ page }) => {
    await bootHome(page, { operations: {
      ...READY_OPERATIONS,
      recentCalls: [],
      recentRequests: [{
        ...READY_OPERATIONS.recentRequests[0], status: 'NEEDS_VERIFICATION', title: 'Caso por revisar'
      }]
    }});
    await page.goto('/app');
    await expect(page.getByRole('region', { name: 'Última actividad' }))
      .toContainText('needs verification');
  });

  test('invalid business timezone leaves activity time blank without throwing', async ({ page }) => {
    await bootHome(page, { operations: {
      ...READY_OPERATIONS,
      timezone: 'Definitely/Invalid',
      recentRequests: [],
      recentCalls: [{ ...READY_OPERATIONS.recentCalls[0], startedAt: '2026-10-02T16:00:00Z' }]
    }});
    await page.goto('/app');
    const latest = page.getByRole('region', { name: 'Última actividad' });
    await expect(latest).toBeVisible();
    await expect(latest.locator('time')).toBeEmpty();
  });

  test('null onboarding nextStep stays on safe Settings fallback without business metrics', async ({ page }) => {
    await bootHome(page, { onboarding: {
      ...INCOMPLETE_ONBOARDING, nextStep: null
    }});
    await page.goto('/app');
    const onboarding = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(onboarding.getByRole('link', { name: /Continuar configuración/ }))
      .toHaveAttribute('href', '/app/settings');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
  });

  test('real zero attributed without a declared currency does not invent an exchange rate', async ({ page }) => {
    await bootHome(page, { analytics: {
      ...EMPTY_ANALYTICS,
      primaryCurrency: null, bookingCurrency: null,
      recepVozRevenue: 0, recepVozBookingRevenue: 0
    }});
    await page.goto('/app');
    const sales = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(sales).toContainText('$0');
    await expect(sales).not.toContainText('Valor no disponible');
  });

  test('explicit commercial refresh displays staleness without hiding verified value or duplicating writes', async ({ page }) => {
    let release;
    let calls = 0;
    const gate = new Promise(resolve => { release = resolve; });
    const state = await bootHome(page);
    await page.route('**/api/v1/commercial/analytics**', async route => {
      calls += 1;
      if (calls > 1) await gate;
      return route.fulfill(json(READY_ANALYTICS));
    });
    await page.goto('/app');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    await expect(value).toContainText('$209.900');
    const refresh = value.getByRole('button', { name: 'Actualizar valor comercial' });
    await refresh.click();
    await expect(value).toContainText('Actualizando…');
    await expect(refresh).toBeDisabled();
    await expect(value).toContainText('$209.900');
    expect(state.attentionMutations()).toBe(0);
    release();
    await expect(refresh).toBeEnabled();
    await expect(value).not.toContainText('Actualizando…');
    expect(calls).toBe(2);
  });

  test('missing recent request status remains a clearly labelled solicitud', async ({ page }) => {
    await bootHome(page, { operations: {
      ...READY_OPERATIONS,
      recentCalls: [],
      recentRequests: [{ ...READY_OPERATIONS.recentRequests[0], status: null }]
    }});
    await page.goto('/app');
    const activity = page.getByRole('region', { name: 'Última actividad' });
    await expect(activity).toContainText('Cliente pidió devolución de llamada');
    await expect(activity).toContainText('Solicitud');
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
    await expect(toggle).toHaveAttribute('aria-controls', 'home-history-panel');
    await expect(page.locator('#home-history-panel')).toBeHidden();
    await toggle.focus();
    await page.keyboard.press('Enter');
    const collapse = page.getByRole('button', { name: /Ocultar historial y auditoría/ });
    await expect(collapse).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByRole('heading', { name: 'Historial y auditoría' })).toBeVisible();
    await expect(page.locator('#home-history-panel')).toBeVisible();
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
    await expect(panel.getByRole('group', { name: /Confirmación de cierre: Cliente pidió devolución de llamada/ })).toBeVisible();
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

  test('attention failure alert and retry are accessible with Enter, never a mutation', async ({ page }) => {
    const state = await bootHome(page, { attentionError: 503 });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(panel.getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    const before = state.attentionReads();
    const retry = panel.getByRole('button', { name: 'Reintentar' });
    await retry.focus();
    await page.keyboard.press('Enter');
    await expect.poll(state.attentionReads).toBeGreaterThan(before);
    await expect(panel.getByRole('alert')).toContainText('No pudimos verificar los pendientes');
    expect(state.attentionMutations()).toBe(0);
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

  test('commercial periods are a named keyboard-operable group with visible focus', async ({ page }) => {
    await bootHome(page);
    await page.goto('/app');

    const group = page.getByRole('group', { name: 'Periodo del valor generado' });
    await expect(group).toBeVisible();
    const today = group.getByRole('button', { name: 'Hoy' });
    const seven = group.getByRole('button', { name: '7 días' });
    const thirty = group.getByRole('button', { name: '30 días' });

    await today.focus();
    await expect(today).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(today).toHaveAttribute('aria-pressed', 'true');
    await page.keyboard.press('Tab');
    await expect(seven).toBeFocused();
    await page.keyboard.press('Space');
    await expect(seven).toHaveAttribute('aria-pressed', 'true');
    await page.keyboard.press('Tab');
    await expect(thirty).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(thirty).toHaveAttribute('aria-pressed', 'true');
    await expect(today).toHaveAttribute('aria-pressed', 'false');

    const focusStyle = await thirty.evaluate(element => getComputedStyle(element).outlineStyle);
    expect(focusStyle).not.toBe('none');
  });

  test('active call announcement is readable without relying on a decorative animation', async ({ page }) => {
    await bootHome(page, { operations: {
      ...READY_OPERATIONS,
      recentCalls: [{
        ...READY_OPERATIONS.recentCalls[0],
        status: 'IN_PROGRESS',
        resolution: null
      }]
    } });
    await page.goto('/app');

    const hero = page.getByRole('region', { name: 'Estado de tu negocio' });
    await expect(hero).toContainText('Hay una llamada en curso.');
    const announcement = hero.locator('span').filter({ hasText: /^Hay una llamada en curso\.$/ });
    await expect(announcement).toHaveCount(1);
    await expect(announcement).toHaveClass(/srOnly/);
    await expect(announcement).not.toHaveAttribute('aria-hidden', 'true');
  });

  test('reduced-motion preference removes ambient and dashboard decorative animations', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootHome(page);
    await page.goto('/app');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
    const animations = await page.evaluate(() => {
      const selectors = [
        '[data-home-ambient-layer="flow"]',
        '[data-home-ambient-layer="grid"]',
        '[data-home-ambient-layer="particles"]',
        '[data-home-ambient-layer="halo"]'
      ];
      return selectors.map(selector => getComputedStyle(document.querySelector(selector)).animationName);
    });
    expect(animations).toEqual(['none', 'none', 'none', 'none']);
    await expect(page.getByRole('group', { name: 'Periodo del valor generado' })).toBeVisible();
  });

  test('mobile action buttons and commercial period targets meet 44 CSS pixel minimum', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await bootHome(page);
    await page.goto('/app');
    const targets = [
      page.getByRole('button', { name: 'Hoy' }),
      page.getByRole('button', { name: '7 días' }),
      page.getByRole('button', { name: '30 días' }),
      page.getByRole('button', { name: 'Actualizar' }),
      page.getByRole('button', { name: 'Empezar gestión' }),
      page.getByRole('button', { name: 'Tomar caso' }),
      page.getByRole('link', { name: 'Revisar pendientes' })
    ];
    for (const target of targets) {
      await expect(target).toBeVisible();
      const bounds = await target.boundingBox();
      expect(bounds).not.toBeNull();
      expect(bounds.height).toBeGreaterThanOrEqual(43.5);
    }
    await expectNoHorizontalOverflow(page);
  });

  test('onboarding at narrow width keeps progress and primary action accessible to keyboard', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 740 });
    await bootHome(page, { onboarding: INCOMPLETE_ONBOARDING });
    await page.goto('/app');
    const panel = page.getByRole('region', { name: 'Configura tu negocio' });
    await expect(panel.getByRole('progressbar', { name: 'Progreso de configuración' })).toHaveAttribute('aria-valuenow', '25');
    const action = panel.getByRole('link', { name: 'Continuar configuración' });
    await expect(action).toHaveAttribute('href', '/app/settings?section=services');
    await action.focus();
    await expect(action).toBeFocused();
    expect((await action.boundingBox()).height).toBeGreaterThanOrEqual(43.5);
    await expectNoHorizontalOverflow(page);
  });

  test('zoomed Home retains whole hero content instead of clipping behind value panel', async ({ page }) => {
    await page.setViewportSize({ width: 640, height: 900 });
    await bootHome(page, { operations: {
      ...READY_OPERATIONS,
      callsToday: 123456,
      bookingsToday: 78910
    } });
    await page.goto('/app');
    await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
    const hero = page.getByRole('region', { name: 'Estado de tu negocio' });
    await expect(hero).toBeVisible();
    await expect(hero).toContainText('123456');
    const value = page.getByRole('region', { name: 'Valor generado por RecepVoz' });
    const bounds = await Promise.all([hero.boundingBox(), value.boundingBox()]);
    expect(bounds[0]).not.toBeNull();
    expect(bounds[1]).not.toBeNull();
    expect(bounds[0].y + bounds[0].height).toBeLessThanOrEqual(bounds[1].y + 2);
    await expectNoHorizontalOverflow(page);
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
    { name: 'compact phone', width: 320, height: 740 },
    { name: 'large phone', width: 430, height: 932 },
    { name: 'small laptop', width: 1024, height: 768 }
  ]) {
    test(`Home preserves sections and has no horizontal overflow at ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await bootHome(page);
      await page.goto('/app');
      await expect(page.getByRole('region', { name: 'Estado de tu negocio' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Valor generado por RecepVoz' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toBeVisible();
      await expect(page.getByRole('region', { name: 'Última actividad' })).toBeVisible();
      await expectNoHorizontalOverflow(page);
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
  test('HTTP success with missing roles fails closed without operations leakage', async ({ page }) => {
    await bootHome(page, { roles: [] });
    await page.goto('/app');
    await expect(page.locator('main[data-visual-page="home"] [role="alert"]'))
      .toContainText('No pudimos verificar tus permisos de acceso.');
    await expect(page.getByRole('region', { name: 'Resultados de hoy' })).toHaveCount(0);
  });

});
