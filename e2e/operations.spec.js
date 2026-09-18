const { test, expect } = require('@playwright/test');

test('operations is advanced-only with calls and diagnostics', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  const dashboard = {
    businessName: 'Negocio E2E',
    timezone: 'America/Santiago',
    localNow: '2026-09-11T08:00:00-03:00',
    callsToday: 12,
    callDurationSecondsToday: 754,
    bookingsToday: 4,
    newCustomersToday: 3,
    openRequests: 1,
    unansweredQuestions: 1,
    callFailuresToday: 0,
    estimatedCallCostTodayUsd: 1.2375,
    recentCalls: [{ id: 'c1', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'BOOKING_CREATED', startedAt: '2026-09-11T10:40:00Z', durationSeconds: 95 }],
    recentRequests: [],
    unanswered: []
  };

  const readiness = {
    ready: true,
    requiredPassed: 7,
    requiredTotal: 7,
    checks: [
      { code: 'BUSINESS_PROFILE', label: 'Perfil del negocio', ready: true, required: true, detail: 'Nombre, idioma y zona horaria válidos.' },
      { code: 'OPENAI_REALTIME', label: 'OpenAI Realtime', ready: true, required: true, detail: 'Realtime configurado.' }
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
    checks: [{ code: 'SUMMARY', label: 'Resumen final', passed: true, detail: 'Resumen persistido.' }]
  };

  const detail = {
    call: { id: 'c1', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'BOOKING_CREATED', startedAt: '2026-09-11T10:40:00Z', durationSeconds: 95 },
    summary: 'El cliente reservó una hora.',
    actions: [{ id: 'a1', actionType: 'BOOKING_CREATED', success: true, detail: 'Peluquería', createdAt: '2026-09-11T10:40:50Z' }],
    transcript: [
      { id: 't1', speaker: 'USER', content: 'Necesito una hora.', createdAt: '2026-09-11T10:40:10Z' },
      { id: 't2', speaker: 'ASSISTANT', content: 'Reserva confirmada.', createdAt: '2026-09-11T10:40:55Z' }
    ]
  };

  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json(dashboard)));
  await page.route('**/api/v1/operations/readiness', route => route.fulfill(json(readiness)));
  await page.route('**/api/v1/operations/certification', route => route.fulfill(json(certification)));
  await page.route('**/api/v1/calls/c1', route => route.fulfill(json(detail)));

  await page.goto('/operations.html');

  await expect(page.getByRole('heading', { level: 1, name: 'Historial y diagnóstico' })).toBeVisible();
  await expect(page.locator('.brand-block strong')).toHaveText('NEGOCIO E2E');
  await expect(page.locator('#businessTabs')).toHaveCount(0);
  await expect(page.locator('#metrics')).toHaveCount(0);
  await expect(page.locator('#bookingsList')).toHaveCount(0);
  await expect(page.locator('#ordersList')).toHaveCount(0);
  await expect(page.locator('#requestsList')).toHaveCount(0);
  await expect(page.locator('#customersList')).toHaveCount(0);
  await expect(page.locator('#callsList')).toContainText('+56911111111');
  await expect(page.locator('.nav-config')).toHaveAttribute('href', '/settings.html');

  await expect(page.locator('#technicalDiagnostics')).not.toHaveAttribute('open', '');
  await page.locator('#technicalDiagnostics > summary').click();
  await expect(page.getByText('OpenAI Realtime')).toBeVisible();
  await expect(page.locator('#diagnosticsSummary')).toHaveText('Todo correcto');

  await page.locator('[data-call-id="c1"] [data-call-detail]').click();
  await expect(page.locator('#callDetailPanel')).toBeVisible();
  await expect(page.locator('#callActions')).toContainText('Reserva creada');
  await expect(page.locator('#callTranscript')).toContainText('Necesito una hora');
  await expect(page.locator('#callTranscript .transcript-line strong').nth(1)).toHaveText('Negocio E2E');
});
