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

  test('exposes a clear voice fallback and recovers when session start fails', async ({ page }) => {
    await bootSimulator(page);
    await page.addInitScript(() => {
      Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: undefined });
      Object.defineProperty(window, 'webkitSpeechRecognition', { configurable: true, value: undefined });
    });
    await page.route('**/api/v1/simulator/sessions', async route => {
      await new Promise(resolve => setTimeout(resolve, 120));
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Simulador temporalmente no disponible' })
      });
    });

    await page.goto('/app/simulator');

    await expect(page.getByTestId('simulator-voice-hint'))
      .toContainText('Tu navegador no ofrece reconocimiento de voz');
    await expect(page.getByRole('button', { name: 'Usar micrófono' })).toBeDisabled();

    const start = page.getByRole('button', { name: 'Nueva prueba' });
    await start.click();
    await expect(start).toBeDisabled();
    await expect(page.getByRole('alert')).toContainText('Simulador temporalmente no disponible');
    await expect(start).toBeEnabled();
    await expect(page.getByTestId('simulator-session-state')).toHaveText('Sin prueba activa');
  });

  test('sends recognized speech when the browser supports it', async ({ page }) => {
    await bootSimulator(page);
    await page.addInitScript(() => {
      class FakeRecognition {
        start() {
          this.onstart?.();
          setTimeout(() => {
            this.onresult?.({ results: [[{ transcript: 'Necesito información' }]] });
            this.onend?.();
          }, 0);
        }
        stop() { this.onend?.(); }
      }
      Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: FakeRecognition });
      Object.defineProperty(window, 'webkitSpeechRecognition', { configurable: true, value: undefined });
    });

    const sessionId = '44444444-4444-4444-4444-444444444444';
    await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
      sessionId,
      greeting: 'Hola, te escucho.',
      active: true,
      ended: false
    })));
    await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
      call: { id: sessionId, resolution: 'NO_ACTION' },
      actions: []
    })));
    await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, async route => {
      expect(route.request().postDataJSON()).toEqual({ message: 'Necesito información' });
      await route.fulfill(json({ reply: 'Claro, dime qué necesitas.', ended: false }));
    });

    await page.goto('/app/simulator');
    await page.getByRole('button', { name: 'Nueva prueba' }).click();
    const mic = page.getByRole('button', { name: 'Usar micrófono' });
    await expect(mic).toBeEnabled();
    await mic.click();

    await expect(page.getByText('Necesito información', { exact: true })).toBeVisible();
    await expect(page.getByText('Claro, dime qué necesitas.', { exact: true })).toBeVisible();
  });

  test('locks session changes while voice recognition listens but keeps the mic stoppable', async ({ page }) => {
    await bootSimulator(page);
    await page.addInitScript(() => {
      class ListeningRecognition {
        start() { setTimeout(() => this.onstart?.(), 300); }
        stop() { this.onend?.(); }
      }
      Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: ListeningRecognition });
      Object.defineProperty(window, 'webkitSpeechRecognition', { configurable: true, value: undefined });
    });

    const sessionId = '66666666-6666-6666-6666-666666666666';
    await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
      sessionId,
      greeting: 'Hola, te escucho.',
      active: true,
      ended: false
    })));
    await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
      call: { id: sessionId, resolution: 'NO_ACTION' },
      actions: []
    })));

    await page.goto('/app/simulator');
    await page.getByRole('button', { name: 'Nueva prueba' }).click();

    const mic = page.getByRole('button', { name: 'Usar micrófono' });
    await mic.click();

    await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Finalizar' })).toBeDisabled();
    await expect(mic).toBeEnabled();
    await expect(page.getByTestId('simulator-voice-hint')).toContainText(/Activando|Escuchando/);

    await mic.click();
    await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Finalizar' })).toBeEnabled();
  });

  test('locks session controls while a message is in flight', async ({ page }) => {
    await bootSimulator(page);
    const sessionId = '55555555-5555-5555-5555-555555555555';

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
    await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, async route => {
      await new Promise(resolve => setTimeout(resolve, 250));
      await route.fulfill(json({ reply: 'Respuesta segura.', ended: false }));
    });

    await page.goto('/app/simulator');
    await page.getByRole('button', { name: 'Nueva prueba' }).click();
    await page.getByLabel('Mensaje de prueba').fill('Mensaje pendiente');
    await page.getByRole('button', { name: 'Enviar' }).click();

    await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Finalizar' })).toBeDisabled();
    await expect(page.getByText('Respuesta segura.', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Finalizar' })).toBeEnabled();
  });

  test('waits for business identity before enabling a new simulation', async ({ page }) => {
    await bootSimulator(page, { businessDelay: 250 });
    await page.goto('/app/simulator');

    const start = page.getByRole('button', { name: 'Nueva prueba' });
    await expect(start).toBeDisabled();
    await expect(page.getByText('Negocio React', { exact: true })).toBeVisible();
    await expect(start).toBeEnabled();
  });

  test('stays contained on tablet and mobile viewports', async ({ page }) => {
    await bootSimulator(page);

    for (const viewport of [
      { width: 768, height: 900 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/simulator');
      await expect(page.getByRole('heading', { level: 1, name: 'Simulador' })).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1
      )).toBe(true);
    }
  });
});
