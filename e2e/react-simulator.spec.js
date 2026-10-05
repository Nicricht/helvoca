const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootSimulator(page, options = {}) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'react-simulator-e2e'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@negocio.cl',
    roles: ['BUSINESS_ADMIN']
  })));

  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    plan: 'PRO',
    publicPlanCode: 'PRO',
    planName: 'Profesional',
    status: 'ACTIVE',
    includedMinutes: 500,
    usedMinutes: 35
  })));

  await page.route('**/api/v1/business', async route => {
    if (options.businessDelay) await new Promise(resolve => setTimeout(resolve, options.businessDelay));
    await route.fulfill(json({ name: 'Negocio React' }));
  });
}

test.describe('React Simulator migration', () => {
  test('renders the safe simulator inside the canonical React shell', async ({ page }) => {
    await bootSimulator(page);
    await page.goto('/app/simulator');

    await expect(page.getByRole('heading', { level: 1, name: 'Simulador' })).toBeVisible();
    await expect(page.getByText('Modo seguro')).toBeVisible();
    await expect(page.getByText(/no crea datos comerciales reales ni realiza llamadas telefónicas/i)).toBeVisible();
    await expect(page.getByText(/WhatsApp real/i)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Finalizar' })).toBeDisabled();
    await expect(page.getByRole('link', { name: 'Agenda' })).toHaveAttribute('href', '/app/agenda');
  });

  test('starts, chats, exposes verified trace and finishes without browser-side commercial mutations', async ({ page }) => {
    await bootSimulator(page);
    const sessionId = '11111111-1111-1111-1111-111111111111';
    const requests = [];
    let actionCreated = false;

    page.on('request', request => {
      const url = new URL(request.url());
      if (url.pathname.startsWith('/api/v1/')) requests.push({ method: request.method(), path: url.pathname });
    });

    await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
      sessionId,
      greeting: 'Hola, soy tu recepcionista de prueba.',
      active: true,
      ended: false
    })));

    await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, async route => {
      expect(route.request().postDataJSON()).toEqual({ message: 'Quiero reservar mañana' });
      actionCreated = true;
      await route.fulfill(json({
        sessionId,
        reply: 'En una llamada real revisaría disponibilidad.',
        resolution: 'BOOKING_CREATED',
        actionCount: 1,
        ended: false
      }));
    });

    await page.route(`**/api/v1/simulator/sessions/${sessionId}/finish`, route => route.fulfill({ status: 204, body: '' }));

    await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
      call: { id: sessionId, resolution: actionCreated ? 'BOOKING_CREATED' : 'NO_ACTION' },
      actions: actionCreated ? [{
        id: 'action-1',
        actionType: 'BOOKING_CREATED',
        success: true,
        detail: 'Consulta · mañana 15:00'
      }] : []
    })));

    await page.goto('/app/simulator');
    await page.getByRole('button', { name: 'Nueva prueba' }).click();
    await expect(page.getByText('Hola, soy tu recepcionista de prueba.')).toBeVisible();

    await page.getByLabel('Mensaje de prueba').fill('Quiero reservar mañana');
    await page.getByRole('button', { name: 'Enviar' }).click();

    await expect(page.getByText('En una llamada real revisaría disponibilidad.')).toBeVisible();
    await expect(page.getByTestId('simulator-resolution')).toHaveText('Reserva creada');
    await expect(page.getByTestId('simulator-action-count')).toHaveText('1');
    await expect(page.getByTestId('simulator-trace')).toContainText('Consulta · mañana 15:00');

    await page.getByRole('button', { name: 'Finalizar' }).click();
    await expect(page.getByTestId('simulator-session-state')).toHaveText('Prueba finalizada');

    expect(requests.filter(item => !['GET', 'HEAD', 'OPTIONS'].includes(item.method)).map(item => item.path))
      .toEqual([
        '/api/v1/simulator/sessions',
        `/api/v1/simulator/sessions/${sessionId}/messages`,
        `/api/v1/simulator/sessions/${sessionId}/finish`
      ]);
  });

  test('restores a failed message and remains usable', async ({ page }) => {
    await bootSimulator(page);
    const sessionId = '33333333-3333-3333-3333-333333333333';

    await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
      sessionId,
      greeting: 'Hola.',
      active: true,
      ended: false
    })));
    await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
      call: { id: sessionId, resolution: 'NO_ACTION' },
      actions: []
    })));
    await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, route => route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'No pude procesar el mensaje' })
    }));

    await page.goto('/app/simulator');
    await page.getByRole('button', { name: 'Nueva prueba' }).click();
    await page.getByLabel('Mensaje de prueba').fill('Necesito una reserva');
    await page.getByRole('button', { name: 'Enviar' }).click();

    await expect(page.getByRole('alert')).toContainText('No pude procesar el mensaje');
    await expect(page.getByLabel('Mensaje de prueba')).toHaveValue('Necesito una reserva');
    await expect(page.getByLabel('Mensaje de prueba')).toBeEnabled();
  });

  test('legacy simulator URL becomes compatibility-only and does not load retired assets', async ({ page }) => {
    await bootSimulator(page);
    const requested = [];
    page.on('request', request => requested.push(new URL(request.url()).pathname));

    await page.goto('/simulator.html');

    await expect(page).toHaveURL(/\/app\/simulator\/?$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Simulador' })).toBeVisible();
    expect(requested).not.toContain('/simulator.js');
    expect(requested).not.toContain('/simulator.css');
  });
});
