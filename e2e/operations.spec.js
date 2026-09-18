const { test, expect } = require('@playwright/test');

test('operations prioritizes business work and keeps technical diagnostics collapsed', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  const state = {
    requests: [{ id: 'r1', type: 'soporte', title: 'Revisar equipo', priority: 'HIGH', status: 'OPEN', createdAt: '2026-09-11T10:00:00Z' }],
    questions: [{ id: 'q1', question: '¿Tienen estacionamiento?', occurrences: 2, lastSeenAt: '2026-09-11T10:30:00Z' }],
    bookings: [{ id: 'b1', customerId: 'cust1', serviceId: 'svc1', startAt: '2026-09-12T15:00:00Z', endAt: '2026-09-12T15:30:00Z', status: 'CONFIRMED', source: 'VOICE' }],
    customers: [{ id: 'cust1', name: 'Ana Reserva', phone: '+56922222222', email: 'ana@example.cl', createdAt: '2026-09-10T09:00:00Z' }],
    services: [{ id: 'svc1', name: 'Peluquería', durationMinutes: 30, price: 25000, active: true }],
    orders: [{ id: 'o1', status: 'CONFIRMED', fulfillmentType: 'PICKUP', contactName: 'Juan Pedido', contactPhone: '+56933333333', total: 18990, currency: 'CLP', source: 'WHATSAPP', createdAt: '2026-09-11T10:50:00Z', lines: [{ name: 'Hamburguesa', quantity: 2, unitPrice: 7000, lineTotal: 14000 }, { name: 'Bebida', quantity: 1, unitPrice: 4990, lineTotal: 4990 }] }]
  };

  const dashboard = () => ({
    businessName: 'Negocio E2E',
    timezone: 'America/Santiago',
    localNow: '2026-09-11T08:00:00-03:00',
    callsToday: 12,
    callDurationSecondsToday: 754,
    bookingsToday: 4,
    newCustomersToday: 3,
    openRequests: state.requests.filter(r => r.status === 'OPEN' || r.status === 'IN_PROGRESS').length,
    unansweredQuestions: state.questions.length,
    callFailuresToday: 1,
    estimatedCallCostTodayUsd: 1.2375,
    recentCalls: [{ id: 'c1', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'REQUEST_CREATED', startedAt: '2026-09-11T10:40:00Z', durationSeconds: 95, estimatedTotalCostUsd: 0.1025 }],
    recentRequests: state.requests,
    unanswered: state.questions
  });

  const readiness = {
    ready: true,
    requiredPassed: 7,
    requiredTotal: 7,
    checks: [
      { code: 'BUSINESS_PROFILE', label: 'Perfil del negocio', ready: true, required: true, detail: 'Nombre, idioma y zona horaria válidos.' },
      { code: 'OPENAI_REALTIME', label: 'OpenAI Realtime', ready: true, required: true, detail: 'Realtime URL y modelo configurados.' }
    ],
    capabilities: { VOICE_ASSISTANT: true, BOOKINGS: true },
    warnings: []
  };

  const certification = {
    available: true,
    state: 'PASSED',
    startedAt: '2026-09-11T09:00:00Z',
    aiProvider: 'gemini',
    telephonyProvider: 'twilio',
    callStatus: 'COMPLETED',
    passedChecks: 8,
    totalChecks: 8,
    checks: [{ code: 'SUMMARY', label: 'Resumen final', passed: true, detail: 'La llamada debe finalizar con resumen persistido.' }]
  };

  const callDetail = {
    call: { id: 'c1', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'REQUEST_CREATED', startedAt: '2026-09-11T10:40:00Z', durationSeconds: 95 },
    summary: 'El cliente solicitó una cotización.',
    actions: [{ id: 'a1', actionType: 'REQUEST_CREATED', success: true, detail: 'Revisar equipo', createdAt: '2026-09-11T10:40:50Z' }],
    transcript: [
      { id: 't1', speaker: 'USER', content: 'Necesito una cotización.', createdAt: '2026-09-11T10:40:10Z' },
      { id: 't2', speaker: 'ASSISTANT', content: 'Perfecto, dejé registrada la solicitud.', createdAt: '2026-09-11T10:40:55Z' }
    ]
  };

  await page.route('**/api/v1/operations/dashboard', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(dashboard()) }));
  await page.route('**/api/v1/operations/readiness', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(readiness) }));
  await page.route('**/api/v1/operations/certification', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(certification) }));
  await page.route('**/api/v1/calls/c1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(callDetail) }));
  await page.route('**/api/v1/bookings', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(state.bookings) }));
  await page.route('**/api/v1/customers', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(state.customers) }));
  await page.route('**/api/v1/services', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(state.services) }));
  await page.route('**/api/v1/commercial/orders', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(state.orders) }));
  await page.route('**/api/v1/commercial/orders/o1/status', async route => {
    expect(route.request().method()).toBe('PATCH');
    const body = route.request().postDataJSON();
    expect(body.status).toBe('PREPARING');
    state.orders[0] = { ...state.orders[0], status: 'PREPARING' };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(state.orders[0]) });
  });

  await page.route('**/api/v1/requests', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    const body = route.request().postDataJSON();
    state.requests.unshift({ id: 'r2', type: body.requestType, title: body.title, priority: body.priority, status: 'OPEN', createdAt: '2026-09-11T11:00:00Z' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(state.requests[0]) });
  });

  await page.route('**/api/v1/learning/questions/q1/answer', async route => {
    state.questions = [];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'q1', status: 'ANSWERED' }) });
  });

  await page.goto('/operations.html');

  await expect(page.locator('link[href^="/operations-business.css?v="]')).toHaveCount(1);

  await expect(page.getByRole('heading', { level: 1, name: 'Operaciones avanzadas' })).toBeVisible();
  await expect(page.locator('#businessName')).toHaveText('Negocio E2E');
  await expect(page.locator('#callsToday')).toHaveText('12');
  await expect(page.locator('#minutesToday')).toContainText('12:34');
  await expect(page.locator('#bookingsToday')).toHaveCount(0);

  await expect(page.locator('#businessTabs')).toHaveCount(0);
  await expect(page.locator('#callsList')).toBeVisible();

  await expect(page.locator('#technicalDiagnostics')).not.toHaveAttribute('open', '');
  await expect(page.getByText('OpenAI Realtime')).not.toBeVisible();
  await page.locator('#technicalDiagnostics > summary').click();
  await expect(page.getByText('OpenAI Realtime')).toBeVisible();
  await expect(page.locator('#diagnosticsSummary')).toHaveText('Todo correcto');

  await page.locator('[data-call-id="c1"] [data-call-detail]').click();
  await expect(page.locator('#callDetailPanel')).toBeVisible();
  await expect(page.locator('#callActions')).toContainText('Solicitud creada');
  await expect(page.locator('#callActions')).not.toContainText('REQUEST_CREATED');
  await expect(page.locator('#callTranscript')).toContainText('Necesito una cotización');

  await page.locator('#newRequestBtn').click();
  await page.locator('#requestForm [name=requestType]').fill('cotización');
  await page.locator('#requestForm [name=title]').fill('Cotizar instalación');
  await page.locator('#requestForm [name=priority]').selectOption('HIGH');
  await page.getByRole('button', { name: 'Guardar solicitud' }).click();
  await expect(page.getByText('Cotizar instalación')).toBeVisible();

  await page.locator('[data-question-id="q1"] [data-answer]').fill('Sí, tenemos estacionamiento gratuito.');
  await page.locator('[data-question-id="q1"] [data-answer-btn]').click();
  await expect(page.getByText('Helvoca no tiene preguntas pendientes. ✨')).toBeVisible();
});
