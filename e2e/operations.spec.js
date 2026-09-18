const { test, expect } = require('@playwright/test');

test('operations is advanced-only with calls and diagnostics', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
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
  const expectedCallTime = await page.evaluate(value => new Intl.DateTimeFormat('es-CL', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Santiago'
  }).format(new Date(value)), '2026-09-11T10:40:00Z');
  await expect(page.locator('#callsList')).toContainText(expectedCallTime);
  await expect(page.locator('.nav-config')).toHaveAttribute('href', '/settings.html');
  await expect(page.getByRole('link', { name: 'Probar' })).toHaveAttribute('href', '/simulator.html');

  await expect(page.locator('#technicalDiagnostics')).not.toHaveAttribute('open', '');
  await page.locator('#technicalDiagnostics > summary').click();
  await expect(page.getByText('OpenAI Realtime')).toBeVisible();
  await expect(page.locator('#diagnosticsSummary')).toHaveText('Todo correcto');

  await page.locator('[data-call-id="c1"] [data-call-detail]').click();
  await expect(page.locator('#callDetailPanel')).toBeVisible();
  await expect(page.locator('#callActions')).toContainText('Reserva creada');
  await expect(page.locator('#callTranscript')).toContainText('Necesito una hora');
  await expect(page.locator('#callTranscript .transcript-line strong').nth(1)).toHaveText('Negocio E2E');
  await page.getByRole('button', { name: 'Cerrar' }).click();
  await expect(page.locator('#callDetailPanel')).toBeHidden();
});

test('operations renders empty activity and failed technical states', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E', timezone: 'America/Caracas', localNow: '2026-09-18T11:00:00-04:00',
    callFailuresToday: 2, recentCalls: []
  })));
  await page.route('**/api/v1/operations/readiness', route => route.fulfill(json({
    ready: false, requiredPassed: 1, requiredTotal: 2,
    checks: [{ code: 'VOICE', label: 'Telefonía', ready: false, required: true, detail: 'Falta proveedor.' }],
    capabilities: { VOICE_ASSISTANT: false }, warnings: ['La voz no está lista.']
  })));
  await page.route('**/api/v1/operations/certification', route => route.fulfill(json({
    available: true, state: 'FAILED', passedChecks: 1, totalChecks: 2,
    checks: [{ code: 'SUMMARY', label: 'Resumen', passed: false, detail: 'No persistido.' }]
  })));

  await page.goto('/operations.html');

  await expect(page.locator('#callsList')).toContainText('Todavía no hay llamadas reales.');
  await expect(page.locator('#healthBadge')).toHaveText('2 llamadas necesitan revisión');
  await expect(page.locator('#diagnosticsSummary')).toHaveText('Revisar voz y certificación');
  await page.locator('#technicalDiagnostics > summary').click();
  await expect(page.locator('#readinessBadge')).toHaveText('1/2');
  await expect(page.locator('#readinessWarnings')).toContainText('La voz no está lista.');
  await expect(page.locator('#certificationBadge')).toHaveText('FALLÓ');
});

test('operations reports load errors and refreshes without duplicate clicks', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  let dashboardRequests = 0;
  await page.route('**/api/v1/operations/dashboard', async route => {
    dashboardRequests += 1;
    if (dashboardRequests === 1) {
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Diagnóstico no disponible' }) });
      return;
    }
    await new Promise(resolve => setTimeout(resolve, 250));
    await route.fulfill(json({
      businessName: 'Negocio Recuperado', timezone: 'America/Caracas', localNow: '2026-09-18T11:00:00-04:00',
      callFailuresToday: 0, recentCalls: []
    }));
  });
  await page.route('**/api/v1/operations/readiness', route => route.fulfill(json({
    ready: true, requiredPassed: 1, requiredTotal: 1, checks: [], capabilities: {}, warnings: []
  })));
  await page.route('**/api/v1/operations/certification', route => route.fulfill(json({ available: false })));

  await page.goto('/operations.html');
  await expect(page.locator('#message')).toContainText('Diagnóstico no disponible');

  const refresh = page.getByRole('button', { name: 'Actualizar' });
  await refresh.click();
  await expect(refresh).toBeDisabled();
  await expect(page.locator('.brand-block strong')).toHaveText('NEGOCIO RECUPERADO');
  await expect(refresh).toBeEnabled();
  expect(dashboardRequests).toBe(2);
});

test('operations keeps valid call history visible when a diagnostic endpoint fails', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio Resiliente', timezone: 'America/Caracas', localNow: '2026-09-18T11:00:00-04:00',
    callFailuresToday: 0,
    recentCalls: [{ id: 'c1', callerNumber: '+56922222222', status: 'COMPLETED', startedAt: '2026-09-18T15:00:00Z', durationSeconds: 30 }]
  })));
  await page.route('**/api/v1/operations/readiness', route => route.fulfill({
    status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Readiness no disponible' })
  }));
  await page.route('**/api/v1/operations/certification', route => route.fulfill(json({ available: false })));

  await page.goto('/operations.html');

  await expect(page.locator('.brand-block strong')).toHaveText('NEGOCIO RESILIENTE');
  await expect(page.locator('#callsList')).toContainText('+56922222222');
  await expect(page.locator('#diagnosticsSummary')).toHaveText('Revisar voz');
  await page.locator('#technicalDiagnostics > summary').click();
  await expect(page.locator('#readinessBadge')).toHaveText('ERROR');
  await expect(page.locator('#readinessWarnings')).toContainText('Readiness no disponible');
});

test('operations remains usable on tablet and mobile viewports', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 900 });
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E', timezone: 'America/Santiago', localNow: '2026-09-18T11:00:00-03:00',
    callFailuresToday: 0, recentCalls: [{ id: 'c1', callerNumber: '+56911111111', status: 'COMPLETED', startedAt: '2026-09-18T14:00:00Z', durationSeconds: 45 }]
  })));
  await page.route('**/api/v1/operations/readiness', route => route.fulfill(json({
    ready: true, requiredPassed: 1, requiredTotal: 1, checks: [], capabilities: {}, warnings: []
  })));
  await page.route('**/api/v1/operations/certification', route => route.fulfill(json({ available: false })));

  await page.goto('/operations.html');

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Abrir detalle' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Abrir detalle' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});
