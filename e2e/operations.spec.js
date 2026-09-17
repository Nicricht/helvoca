const { test, expect } = require('@playwright/test');

test('operations console loads readiness, certification, commercial metrics, call trace and learning', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  const state = {
    requests: [{ id: 'r1', type: 'soporte', title: 'Revisar equipo', priority: 'HIGH', status: 'OPEN', createdAt: '2026-09-11T10:00:00Z' }],
    questions: [{ id: 'q1', question: '¿Tienen estacionamiento?', occurrences: 2, lastSeenAt: '2026-09-11T10:30:00Z' }]
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
      { code: 'ACTIVE_PHONE', label: 'Número activo', ready: true, required: true, detail: 'Hay al menos un número activo asociado al tenant.' },
      { code: 'TWILIO_MEDIA_STREAM', label: 'Media Streams', ready: true, required: true, detail: 'WebSocket WSS de audio configurado.' },
      { code: 'OPENAI_REALTIME', label: 'OpenAI Realtime', ready: true, required: true, detail: 'Realtime URL y modelo configurados.' }
    ],
    capabilities: { VOICE_ASSISTANT: true, INFORMATION: true, GENERIC_REQUESTS: true, BOOKINGS: true, HUMAN_TRANSFER: true },
    warnings: []
  };

  const certification = {
    available: true,
    state: 'PASSED',
    callId: 'cert-1',
    startedAt: '2026-09-11T09:00:00Z',
    aiProvider: 'gemini',
    telephonyProvider: 'twilio',
    callStatus: 'COMPLETED',
    passedChecks: 8,
    totalChecks: 8,
    checks: [
      { code: 'MEDIA_STARTED', label: 'Audio conectado', passed: true, detail: 'El stream de audio debe iniciar.' },
      { code: 'AI_READY', label: 'IA configurada', passed: true, detail: 'El proveedor de IA debe completar la configuración de sesión.' },
      { code: 'TRANSCRIPT', label: 'Transcripción', passed: true, detail: 'La llamada debe dejar transcripción persistida.' },
      { code: 'SERVICES', label: 'Servicios consultados', passed: true, detail: 'El agente debe consultar el catálogo real del negocio.' },
      { code: 'AVAILABILITY', label: 'Disponibilidad consultada', passed: true, detail: 'El agente debe comprobar disponibilidad mediante backend.' },
      { code: 'BOOKING_CREATED', label: 'Reserva creada', passed: true, detail: 'Debe existir una reserva creada por una tool autorizada.' },
      { code: 'BOOKING_CANCELLED', label: 'Reserva cancelada', passed: true, detail: 'La reserva de certificación debe cancelarse.' },
      { code: 'SUMMARY', label: 'Resumen final', passed: true, detail: 'La llamada debe finalizar con resumen persistido.' }
    ]
  };

  const callDetail = {
    call: { id: 'c1', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'REQUEST_CREATED', startedAt: '2026-09-11T10:40:00Z', durationSeconds: 95 },
    summary: 'El cliente solicitó una cotización. RecepVoz registró la solicitud para seguimiento.',
    actions: [
      { id: 'a1', actionType: 'KNOWLEDGE_SEARCH', success: true, createdAt: '2026-09-11T10:40:20Z' },
      { id: 'a2', actionType: 'REQUEST_CREATED', success: true, entityType: 'BUSINESS_REQUEST', entityId: 'r1', detail: 'Revisar equipo', createdAt: '2026-09-11T10:40:50Z' }
    ],
    transcript: [
      { id: 't1', speaker: 'USER', content: 'Necesito una cotización para revisar mi equipo.', sequenceNumber: 1, createdAt: '2026-09-11T10:40:10Z' },
      { id: 't2', speaker: 'ASSISTANT', content: 'Perfecto, dejé registrada la solicitud.', sequenceNumber: 2, createdAt: '2026-09-11T10:40:55Z' }
    ]
  };

  await page.route('**/api/v1/operations/dashboard', async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(dashboard()) });
  });

  await page.route('**/api/v1/operations/readiness', async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(readiness) });
  });

  await page.route('**/api/v1/operations/certification', async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    expect(route.request().method()).toBe('GET');
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(certification) });
  });

  await page.route('**/api/v1/calls/c1', async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(callDetail) });
  });

  await page.route('**/api/v1/requests', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    const body = route.request().postDataJSON();
    state.requests.unshift({ id: 'r2', type: body.requestType, title: body.title, priority: body.priority, status: 'OPEN', createdAt: '2026-09-11T11:00:00Z' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(state.requests[0]) });
  });

  await page.route('**/api/v1/learning/questions/q1/answer', async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    expect(route.request().postDataJSON().answer).toBe('Sí, tenemos estacionamiento gratuito.');
    state.questions = [];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'q1', status: 'ANSWERED' }) });
  });

  await page.goto('/operations.html');

  await expect(page.locator('#businessName')).toHaveText('Negocio E2E');
  await expect(page.locator('#readinessBadge')).toHaveText('LISTO');
  await expect(page.getByText('OpenAI Realtime')).toBeVisible();
  await expect(page.getByText('VOICE_ASSISTANT ✓')).toBeVisible();
  await expect(page.locator('#callsToday')).toHaveText('12');
  await expect(page.locator('#minutesToday')).toHaveText('12:34');
  await expect(page.locator('#bookingsToday')).toHaveText('4');
  await expect(page.locator('#openRequests')).toHaveText('1');
  await expect(page.locator('#unknownQuestions')).toHaveText('1');
  await expect(page.locator('#costToday')).toHaveText('1,2375');
  await expect(page.locator('#certificationBadge')).toHaveText('APROBADA');
  await expect(page.locator('#certificationMeta')).toContainText('8/8 controles');
  await expect(page.locator('#certificationChecks')).toContainText('Reserva cancelada');

  await page.locator('[data-call-id="c1"] [data-call-detail]').click();
  await expect(page.locator('#callDetailPanel')).toBeVisible();
  await expect(page.locator('#callSummary')).toContainText('cotización');
  await expect(page.locator('#callActions')).toContainText('REQUEST_CREATED');
  await expect(page.locator('#callActions')).toContainText('Revisar equipo');
  await expect(page.locator('#callTranscript')).toContainText('Necesito una cotización');
  await expect(page.locator('#callTranscript')).toContainText('Helvoca');

  await page.locator('#newRequestBtn').click();
  await page.locator('#requestForm [name=requestType]').fill('cotización');
  await page.locator('#requestForm [name=title]').fill('Cotizar instalación');
  await page.locator('#requestForm [name=description]').fill('Cliente solicita precio y plazo.');
  await page.locator('#requestForm [name=priority]').selectOption('HIGH');
  await page.getByRole('button', { name: 'Guardar solicitud' }).click();

  await expect(page.getByText('Cotizar instalación')).toBeVisible();
  await expect(page.locator('#openRequests')).toHaveText('2');
  await expect(page.locator('#message')).toHaveText('Solicitud creada.');

  await page.locator('[data-question-id="q1"] [data-answer]').fill('Sí, tenemos estacionamiento gratuito.');
  await page.locator('[data-question-id="q1"] [data-answer-btn]').click();

  await expect(page.locator('#unknownQuestions')).toHaveText('0');
  await expect(page.getByText('Helvoca no tiene preguntas pendientes. ✨')).toBeVisible();
  await expect(page.locator('#message')).toHaveText('Respuesta aprendida y guardada en conocimiento.');
});
