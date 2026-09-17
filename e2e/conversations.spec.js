const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

test('conversation inbox shows real calls and keeps WhatsApp empty until real data exists', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    recentCalls: [{
      id: 'call-1',
      callerNumber: '+56911111111',
      status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z',
      durationSeconds: 95,
      resolution: 'Reserva creada'
    }]
  })));

  await page.route('**/api/v1/calls/call-1', route => route.fulfill(json({
    call: {
      id: 'call-1',
      callerNumber: '+56911111111',
      status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z',
      durationSeconds: 95,
      resolution: 'Reserva creada'
    },
    summary: 'El cliente reservó una hora.',
    transcript: [
      { speaker: 'USER', content: 'Quiero reservar.', createdAt: '2026-09-17T18:00:10Z' },
      { speaker: 'ASSISTANT', content: 'Claro.', createdAt: '2026-09-17T18:00:12Z' }
    ],
    actions: [
      { actionType: 'CREATE_BOOKING', success: true, detail: 'Reserva creada', createdAt: '2026-09-17T18:00:20Z' }
    ]
  })));

  await page.goto('/conversations.html');

  await expect(page.getByRole('heading', { level: 1, name: 'Conversaciones' })).toBeVisible();
  await expect(page.getByText('+56911111111')).toBeVisible();

  await page.getByText('+56911111111').click();
  await expect(page.getByText('El cliente reservó una hora.')).toBeVisible();
  await expect(page.getByText('Quiero reservar.')).toBeVisible();
  await expect(page.getByText('CREATE_BOOKING')).toBeVisible();

  await page.getByRole('button', { name: 'WhatsApp' }).click();
  await expect(page.getByText('WhatsApp todavía no tiene conversaciones reales')).toBeVisible();
});
