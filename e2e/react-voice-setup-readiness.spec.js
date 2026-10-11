const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status, contentType: 'application/json', body: JSON.stringify(body)
});

function guide(configurationReady = false) {
  const steps = [
    ['BUSINESS', 'Datos del negocio', true, '/app/settings?section=business'],
    ['SERVICES', 'Catálogo de servicios', true, '/app/settings?section=services'],
    ['SCHEDULE', 'Horarios de atención', true, '/app/settings?section=hours'],
    ['AGENT', 'Recepcionista IA', true, '/app/settings?section=receptionist'],
    ['CARRIER', 'Conexión segura con Twilio', true, null],
    ['PHONE', 'Número Twilio certificado para este negocio', configurationReady, '/app/settings?section=channels'],
    ['LIVE_CALL', 'Prueba de llamada real autorizada', false, null]
  ].map(([code, label, complete, href]) => ({
    code, label, complete, href, guidance: 'Revisar requisitos antes de activar voz.'
  }));
  return {
    status: configurationReady ? 'AWAITING_LIVE_CERTIFICATION' : 'CONFIGURATION_INCOMPLETE',
    configurationReady,
    liveCertified: false,
    readyForCommercialCalls: false,
    activePhoneNumber: '+14355652512',
    steps
  };
}

async function boot(page, options = {}) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'part6-e2e'));
  const requests = [];
  let guideFetches = 0;
  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    requests.push({ path, method: request.method() });
    if (request.method() !== 'GET') {
      return route.fulfill(json({ message: 'Mutation blocked by Part 6 contract' }, 501));
    }
    if (path === '/api/v1/auth/me') return route.fulfill(json({
      roles: [options.role || 'BUSINESS_ADMIN'], permissions: ['BUSINESS_READ']
    }));
    if (path === '/api/v1/business') return route.fulfill(json({
      name: 'Negocio Piloto', timezone: 'America/Santiago', language: 'es'
    }));
    if (path === '/api/v1/business/profile') return route.fulfill(json({
      countryCode: 'CL', defaultCurrency: 'CLP', usesReservations: true
    }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({
      businessProfileConfigured: true, servicesConfigured: true,
      scheduleConfigured: true, phoneConfigured: true, readyForCalls: true
    }));
    if (path === '/api/v1/services') return route.fulfill(json([
      { id: 'service-1', name: 'Consulta', durationMinutes: 30, active: true }
    ]));
    if (path === '/api/v1/business/hours') return route.fulfill(json([
      { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
    ]));
    if (path === '/api/v1/knowledge') return route.fulfill(json([]));
    if (path === '/api/v1/ai-agent') return route.fulfill(json({
      name: 'Sofía', active: true, capabilities: []
    }));
    if (path === '/api/v1/ai-agent/voices') return route.fulfill(json([]));
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([{
      id: 'phone-1', phoneNumber: '+14355652512', provider: 'TWILIO', active: true
    }]));
    if (path === '/api/v1/onboarding/voice-readiness') {
      guideFetches++;
      if (options.error) return route.fulfill(json({ message: 'Readiness unavailable' }, 503));
      return route.fulfill(json(guide(options.ready)));
    }
    if (path === '/api/v1/phone-numbers/provisioning/status') return route.fulfill(json({
      enabled: false, configured: false, purchaseAvailable: false
    }));
    return route.fulfill(json({}));
  });
  await page.goto('/app/settings?section=channels');
  return { requests, fetchCount: () => guideFetches };
}

test('setup guides an admin without treating an active legacy number as certified', async ({ page }) => {
  const state = await boot(page);
  const panel = page.getByLabel('Guía de activación de voz');

  await expect(panel).toContainText('Configuración incompleta');
  await expect(panel).toContainText('Número Twilio certificado para este negocio');
  await expect(panel).toContainText('Pendiente');
  await expect(panel).toContainText('no certificado');
  await expect(panel.getByRole('link', { name: 'Ir a configurar' })).toHaveAttribute(
    'href', '/app/settings?section=channels'
  );
  expect(state.requests.filter(x => x.path.includes('provisioning') && x.method !== 'GET')).toEqual([]);
  expect(state.requests.filter(x => x.method !== 'GET')).toEqual([]);
});

test('all local checks passing still never claims live certification or places calls', async ({ page }) => {
  const state = await boot(page, { ready: true });
  const panel = page.getByLabel('Guía de activación de voz');
  await expect(panel).toContainText('Configuración completada. Falta certificar una llamada real autorizada.');
  await expect(panel).toContainText('Prueba de llamada real autorizada');
  await expect(panel).toContainText('Estado comercial:');
  await expect(panel).toContainText('no certificado');
  await panel.getByRole('button', { name: 'Actualizar diagnóstico' }).click();
  await expect.poll(state.fetchCount).toBe(2);
  expect(state.requests.filter(x => x.method !== 'GET')).toEqual([]);
});

test('readiness API outage fails closed, not ready', async ({ page }) => {
  await boot(page, { error: true });
  const panel = page.getByLabel('Guía de activación de voz');
  await expect(panel.getByRole('alert')).toContainText('No pudimos comprobar');
  await expect(panel.getByText('Configuración completada')).toHaveCount(0);
});

test('operator can read the guide without number purchase or activation controls', async ({ page }) => {
  const state = await boot(page, { role: 'OPERATOR' });
  const panel = page.getByLabel('Guía de activación de voz');
  await expect(panel).toContainText('Configuración incompleta');
  await expect(panel.getByRole('button', { name: 'Actualizar diagnóstico' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Activar', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Buscar números' })).toHaveCount(0);
  expect(state.requests.filter(x => x.method !== 'GET')).toEqual([]);
});
