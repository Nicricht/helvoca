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
    onboardingRequests: () => onboardingRequests
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
    await expect(attention).toContainText('4');
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
    await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toContainText(/todo bajo control|sin pendientes/i);
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

    await expect(page).toHaveURL('http://127.0.0.1:4173/');
    await expect(page.locator('[data-auth-entry]')).toBeVisible();
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
