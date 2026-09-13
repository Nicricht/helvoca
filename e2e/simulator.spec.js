const { test, expect } = require('@playwright/test');

test('receptionist simulator keeps actions isolated and shows trace', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  const sessionId = '11111111-1111-1111-1111-111111111111';
  let actionCreated = false;

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
  await expect(page.getByText('No crea datos comerciales reales ni realiza llamadas telefónicas.')).toBeVisible();

  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  await expect(page.getByText('Hola, soy la recepcionista virtual de Negocio E2E. ¿En qué puedo ayudarte?')).toBeVisible();
  await expect(page.locator('#sessionBadge')).toHaveText('Prueba activa');

  await page.locator('#messageInput').fill('Quiero reservar mañana');
  await page.getByRole('button', { name: 'Enviar' }).click();

  await expect(page.getByText('En una llamada real, la reserva quedaría confirmada para mañana a las 15:00.')).toBeVisible();
  await expect(page.locator('#resolution')).toHaveText('BOOKING_CREATED');
  await expect(page.locator('#actionCount')).toHaveText('1');
  await expect(page.locator('#traceList')).toContainText('BOOKING_CREATED');
  await expect(page.locator('#traceList')).toContainText('Consulta · mañana 15:00');
});
