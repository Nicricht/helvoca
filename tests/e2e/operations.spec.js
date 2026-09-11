const { test, expect } = require('@playwright/test');

test('owner sees operations, controls capabilities and teaches Helvoca', async ({ page }) => {
  let questionOpen = true;
  let savedCapabilities = null;

  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    const json = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

    if (path === '/api/v1/auth/me') return json({ email: 'owner@helvoca.test' });
    if (path === '/api/v1/business') return json({ name: 'Negocio Demo', timezone: 'America/Santiago', language: 'es', humanTransferPhone: null });
    if (path === '/api/v1/onboarding/status') return json({
      businessProfileConfigured: true,
      servicesConfigured: true,
      scheduleConfigured: true,
      phoneConfigured: true,
      readyForCalls: true,
      nextStep: 'READY'
    });
    if (path === '/api/v1/services') return json([{ id: '11111111-1111-1111-1111-111111111111', name: 'Consulta', durationMinutes: 30, active: true }]);
    if (path === '/api/v1/business/hours') return json([{ dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }]);
    if (path === '/api/v1/knowledge') return json([]);
    if (path === '/api/v1/phone-numbers') return json([{ id: '22222222-2222-2222-2222-222222222222', phoneNumber: '+17372508034', active: true }]);

    if (path === '/api/v1/dashboard/overview') return json({
      callsToday: 12,
      completedCallsToday: 10,
      bookingsCreatedToday: 4,
      requestsCreatedToday: 2,
      openRequests: 1,
      customers: 23,
      unansweredQuestions: questionOpen ? 1 : 0,
      toolFailuresToday: 0,
      recentCalls: [{ id: '33333333-3333-3333-3333-333333333333', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'REQUEST_CREATED', startedAt: '2026-09-11T10:00:00Z', durationSeconds: 125 }],
      upcomingBookings: [{ id: '44444444-4444-4444-4444-444444444444', customerName: 'Camila', serviceName: 'Consulta', startAt: '2026-09-12T15:00:00Z', status: 'CONFIRMED' }],
      recentRequests: [{ id: '55555555-5555-5555-5555-555555555555', category: 'Cotización', subject: 'Necesita presupuesto', details: 'Solicita una cotización para un servicio.', priority: 'NORMAL', status: 'OPEN', source: 'AI_CALL', createdAt: '2026-09-11T10:01:00Z' }],
      pendingQuestions: questionOpen ? [{ id: '66666666-6666-6666-6666-666666666666', question: '¿Tienen estacionamiento?', status: 'OPEN', occurrences: 2, lastAskedAt: '2026-09-11T10:02:00Z' }] : []
    });

    if (path === '/api/v1/capabilities' && method === 'GET') return json({ capabilities: { INFORMATION: true, SERVICES: true, BOOKINGS: true, REQUESTS: true, HUMAN_TRANSFER: true } });
    if (path === '/api/v1/capabilities' && method === 'PUT') {
      savedCapabilities = request.postDataJSON().enabled;
      return json({ capabilities: Object.fromEntries(['INFORMATION', 'SERVICES', 'BOOKINGS', 'REQUESTS', 'HUMAN_TRANSFER'].map(code => [code, savedCapabilities.includes(code)])) });
    }
    if (path === '/api/v1/voice/readiness') return json({ realtimeReady: true, missing: [] });

    if (path.endsWith('/resolve') && path.startsWith('/api/v1/unanswered-questions/')) {
      questionOpen = false;
      return json({ id: '66666666-6666-6666-6666-666666666666', question: '¿Tienen estacionamiento?', answer: request.postDataJSON().answer, status: 'RESOLVED', occurrences: 2 });
    }
    if (path.endsWith('/ignore') && path.startsWith('/api/v1/unanswered-questions/')) {
      questionOpen = false;
      return json({ id: '66666666-6666-6666-6666-666666666666', status: 'IGNORED' });
    }
    if (path.includes('/api/v1/requests/') && path.endsWith('/status')) return json({});

    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"message":"E2E route not mocked"}' });
  });

  await page.goto('/');
  await expect(page.locator('#dashboardView')).toBeVisible();
  await expect(page.locator('#operationsPanel')).toBeVisible();
  await expect(page.locator('#metricCalls')).toHaveText('12');
  await expect(page.locator('#metricBookings')).toHaveText('4');
  await expect(page.locator('#metricCustomers')).toHaveText('23');
  await expect(page.locator('#voiceReadyBadge')).toHaveText('Realtime listo');
  await expect(page.getByText('Necesita presupuesto')).toBeVisible();
  await expect(page.getByText('¿Tienen estacionamiento?')).toBeVisible();

  await page.locator('#capabilitiesForm input[name="BOOKINGS"]').uncheck();
  await page.getByRole('button', { name: 'Guardar capacidades' }).click();
  await expect.poll(() => savedCapabilities).not.toBeNull();
  expect(savedCapabilities).not.toContain('BOOKINGS');
  expect(savedCapabilities).toContain('REQUESTS');

  page.once('dialog', dialog => dialog.accept('Sí, contamos con estacionamiento gratuito.'));
  await page.getByRole('button', { name: 'Responder y enseñar' }).click();
  await expect(page.getByText('No hay preguntas pendientes. Buen signo.')).toBeVisible();
});
