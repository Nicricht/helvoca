const { test, expect } = require('@playwright/test');

test('receptionist simulator keeps actions isolated and shows trace', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  const sessionId = '11111111-1111-1111-1111-111111111111';
  let actionCreated = false;

  await page.route('**/api/v1/business', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es' })
  }));

  await page.route('**/api/v1/simulator/sessions', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        sessionId,
        greeting: 'Hola, soy la recepcionista virtual de Negocio E2E. ¿En qué puedo ayudarte?',
        active: true,
        ended: false
      })
    });
  });

  await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    expect(route.request().postDataJSON().message).toBe('Quiero reservar mañana');
    actionCreated = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        sessionId,
        reply: 'En una llamada real, la reserva quedaría confirmada para mañana a las 15:00.',
        resolution: 'BOOKING_CREATED',
        actionCount: 1,
        ended: false
      })
    });
  });

  await page.route(`**/api/v1/simulator/sessions/${sessionId}/finish`, async route => {
    expect(route.request().method()).toBe('POST');
    await new Promise(resolve => setTimeout(resolve, 150));
    await route.fulfill({ status: 204, body: '' });
  });

  await page.route(`**/api/v1/calls/${sessionId}`, async route => {
    expect(route.request().headers().authorization).toBe('Bearer e2e-token');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        call: {
          id: sessionId,
          telephonyProvider: 'simulator',
          status: 'IN_PROGRESS',
          resolution: actionCreated ? 'BOOKING_CREATED' : null
        },
        summary: null,
        transcript: [],
        actions: actionCreated ? [{
          id: 'a1',
          actionType: 'BOOKING_CREATED',
          success: true,
          entityType: 'BOOKING',
          entityId: '22222222-2222-2222-2222-222222222222',
          detail: 'Consulta · mañana 15:00',
          createdAt: '2026-09-11T16:30:00Z'
        }] : []
      })
    });
  });

  await page.goto('/simulator.html');
  await expect(page.getByText('Modo seguro')).toBeVisible();
  await expect(page.locator('.topbar > div strong')).toHaveText('NEGOCIO E2E');
  await expect(page.getByRole('link', { name: 'Configuración' })).toHaveAttribute('href', '/settings.html');
  await expect(page.getByText('No crea datos comerciales reales ni realiza llamadas telefónicas.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Finalizar' })).toHaveAttribute('title', 'Inicia una prueba para poder finalizarla.');
  await expect(page.getByRole('button', { name: 'Enviar' })).toHaveAttribute('title', 'Inicia una prueba para poder enviar mensajes.');

  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  await expect(page.getByText('Hola, soy la recepcionista virtual de Negocio E2E. ¿En qué puedo ayudarte?')).toBeVisible();
  await expect(page.locator('#chat .bubble.assistant small').first()).toHaveText('Negocio E2E');
  await expect(page.locator('#sessionBadge')).toHaveText('Prueba activa');

  await page.locator('#messageInput').fill('Quiero reservar mañana');
  await page.getByRole('button', { name: 'Enviar' }).click();

  await expect(page.getByText('En una llamada real, la reserva quedaría confirmada para mañana a las 15:00.')).toBeVisible();
  await expect(page.locator('#resolution')).toHaveText('BOOKING_CREATED');
  await expect(page.locator('#actionCount')).toHaveText('1');
  await expect(page.locator('#traceList')).toContainText('BOOKING_CREATED');
  await expect(page.locator('#traceList')).toContainText('Consulta · mañana 15:00');

  const finish = page.getByRole('button', { name: 'Finalizar' });
  await finish.click();
  await expect(finish).toBeDisabled();
  await expect(page.locator('#sessionBadge')).toHaveText('Prueba finalizada');
  await expect(page.locator('#messageInput')).toBeDisabled();
});

test('simulator exposes a clear voice fallback and start errors', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.addInitScript(() => {
    Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: undefined });
    Object.defineProperty(window, 'webkitSpeechRecognition', { configurable: true, value: undefined });
  });
  await page.route('**/api/v1/business', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ name: 'Negocio Seguro' })
  }));
  let starts = 0;
  await page.route('**/api/v1/simulator/sessions', async route => {
    starts += 1;
    await new Promise(resolve => setTimeout(resolve, 150));
    await route.fulfill({
      status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Simulador temporalmente no disponible' })
    });
  });

  await page.goto('/simulator.html');

  await expect(page.locator('#voiceHint')).toHaveText('Tu navegador no ofrece reconocimiento de voz. Puedes usar el chat igualmente.');
  await expect(page.locator('#micBtn')).toBeDisabled();
  await expect(page.locator('#micBtn')).toHaveAttribute('title', 'El reconocimiento de voz no está disponible en este navegador.');
  const start = page.getByRole('button', { name: 'Nueva prueba' });
  await start.click();
  await expect(start).toBeDisabled();
  await expect(page.locator('#message')).toContainText('Simulador temporalmente no disponible');
  await expect(start).toBeEnabled();
  await expect(page.locator('#sessionBadge')).toHaveText('Sin prueba activa');
  expect(starts).toBe(1);
});

test('simulator restores the message when sending fails', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const sessionId = '33333333-3333-3333-3333-333333333333';
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Negocio Seguro' })));
  await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
    sessionId, greeting: 'Hola, ¿en qué puedo ayudarte?', active: true, ended: false
  })));
  await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
    call: { id: sessionId, status: 'IN_PROGRESS' }, actions: []
  })));
  await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, route => route.fulfill({
    status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'No pude procesar el mensaje' })
  }));

  await page.goto('/simulator.html');
  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  await page.locator('#messageInput').fill('Necesito una reserva');
  await page.getByRole('button', { name: 'Enviar' }).click();

  await expect(page.locator('#message')).toContainText('No pude procesar el mensaje');
  await expect(page.locator('#messageInput')).toHaveValue('Necesito una reserva');
  await expect(page.locator('#messageInput')).toBeEnabled();
});

test('simulator sends recognized speech when the browser supports it', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'e2e-token');
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
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Negocio por Voz' })));
  await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
    sessionId, greeting: 'Hola, te escucho.', active: true, ended: false
  })));
  await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
    call: { id: sessionId, status: 'IN_PROGRESS' }, actions: []
  })));
  await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, async route => {
    expect(route.request().postDataJSON().message).toBe('Necesito información');
    await route.fulfill(json({ reply: 'Claro, dime qué necesitas.', ended: false }));
  });

  await page.goto('/simulator.html');
  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  await expect(page.locator('#micBtn')).toBeEnabled();
  await page.locator('#micBtn').click();

  await expect(page.locator('#chat')).toContainText('Necesito información');
  await expect(page.locator('#chat')).toContainText('Claro, dime qué necesitas.');
});

test('simulator locks session changes while voice recognition listens', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'e2e-token');
    class ListeningRecognition {
      start() { setTimeout(() => this.onstart?.(), 500); }
      stop() { this.onend?.(); }
    }
    Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: ListeningRecognition });
    Object.defineProperty(window, 'webkitSpeechRecognition', { configurable: true, value: undefined });
  });
  const sessionId = '66666666-6666-6666-6666-666666666666';
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Negocio por Voz' })));
  await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
    sessionId, greeting: 'Hola, te escucho.', active: true, ended: false
  })));
  await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
    call: { id: sessionId, status: 'IN_PROGRESS' }, actions: []
  })));

  await page.goto('/simulator.html');
  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  const mic = page.locator('#micBtn');
  await mic.click();

  expect(await page.getByRole('button', { name: 'Nueva prueba' }).isDisabled()).toBe(true);
  await expect(page.getByRole('button', { name: 'Finalizar' })).toBeDisabled();
  await expect(mic).toBeEnabled();
  await expect(page.locator('#voiceHint')).toContainText('Escuchando');
  await mic.click();
  await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Finalizar' })).toBeEnabled();
});

test('simulator locks session controls while a message is in flight', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  const sessionId = '55555555-5555-5555-5555-555555555555';
  const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Negocio Concurrente' })));
  await page.route('**/api/v1/simulator/sessions', route => route.fulfill(json({
    sessionId, greeting: 'Hola.', active: true, ended: false
  })));
  await page.route(`**/api/v1/calls/${sessionId}`, route => route.fulfill(json({
    call: { id: sessionId, status: 'IN_PROGRESS' }, actions: []
  })));
  await page.route(`**/api/v1/simulator/sessions/${sessionId}/messages`, async route => {
    await new Promise(resolve => setTimeout(resolve, 250));
    await route.fulfill(json({ reply: 'Respuesta segura.', ended: false }));
  });

  await page.goto('/simulator.html');
  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  await page.locator('#messageInput').fill('Mensaje pendiente');
  await page.getByRole('button', { name: 'Enviar' }).click();

  await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Finalizar' })).toBeDisabled();
  await expect(page.locator('#chat')).toContainText('Respuesta segura.');
  await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Finalizar' })).toBeEnabled();
});

test('simulator waits for business identity before starting', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/business', async route => {
    await new Promise(resolve => setTimeout(resolve, 250));
    await route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify({ name: 'Negocio Identificado' })
    });
  });

  await page.goto('/simulator.html');

  const start = page.getByRole('button', { name: 'Nueva prueba' });
  await expect(start).toBeDisabled();
  await expect(page.locator('.topbar > div strong')).toHaveText('NEGOCIO IDENTIFICADO');
  await expect(start).toBeEnabled();
});

test('simulator stays usable on tablet and mobile viewports', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 900 });
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/business', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ name: 'Negocio Móvil' })
  }));

  await page.goto('/simulator.html');

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});
