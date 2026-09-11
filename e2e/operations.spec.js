const { test, expect } = require('@playwright/test');

test('operations console loads metrics, creates a request and teaches an unanswered question', async ({ page }) => {
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
    bookingsToday: 4,
    newCustomersToday: 3,
    openRequests: state.requests.filter(r => r.status === 'OPEN' || r.status === 'IN_PROGRESS').length,
    unansweredQuestions: state.questions.length,
    callFailuresToday: 1,
    recentCalls: [{ id: 'c1', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'REQUEST_CREATED', startedAt: '2026-09-11T10:40:00Z', durationSeconds: 95 }],
    recentRequests: state.requests,
    unanswered: state.questions
  });

  await page.route('**/api/v1/operations/dashboard', async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(dashboard()) });
  });

  await page.route('**/api/v1/requests', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    const body = route.request().postDataJSON();
    state.requests.unshift({
      id: 'r2',
      type: body.requestType,
      title: body.title,
      priority: body.priority,
      status: 'OPEN',
      createdAt: '2026-09-11T11:00:00Z'
    });
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
  await expect(page.locator('#callsToday')).toHaveText('12');
  await expect(page.locator('#bookingsToday')).toHaveText('4');
  await expect(page.locator('#openRequests')).toHaveText('1');
  await expect(page.locator('#unknownQuestions')).toHaveText('1');
  await expect(page.getByText('Revisar equipo')).toBeVisible();
  await expect(page.getByText('¿Tienen estacionamiento?')).toBeVisible();

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
