const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

test('conversation inbox combines channels, opens the newest item and uses human labels', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    recentCalls: [{
      id: 'call-1',
      callerNumber: '+56911111111',
      status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z',
      durationSeconds: 95,
      resolution: 'BOOKING_CREATED'
    }]
  })));

  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([{
    id: 'wa-1',
    customerId: null,
    channel: 'whatsapp',
    sender: '+56922222222',
    recipient: '+56933333333',
    openedAt: '2026-09-17T18:05:00Z',
    lastMessageAt: '2026-09-17T18:06:00Z'
  }])));

  await page.route('**/api/v1/calls/call-1', route => route.fulfill(json({
    call: {
      id: 'call-1',
      callerNumber: '+56911111111',
      status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z',
      durationSeconds: 95,
      resolution: 'BOOKING_CREATED'
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

  await page.route('**/api/v1/messaging/conversations/wa-1', route => route.fulfill(json({
    conversation: {
      id: 'wa-1',
      sender: '+56922222222',
      openedAt: '2026-09-17T18:05:00Z',
      lastMessageAt: '2026-09-17T18:06:00Z'
    },
    messages: [
      { id: 'm-1', direction: 'INBOUND', role: 'USER', content: '¿Tienen hora mañana?', createdAt: '2026-09-17T18:05:10Z' },
      { id: 'm-2', direction: 'OUTBOUND', role: 'ASSISTANT', content: 'Sí, tengo disponibilidad.', createdAt: '2026-09-17T18:05:20Z' }
    ]
  })));

  await page.goto('/conversations.html');

  await expect(page.getByRole('heading', { level: 1, name: 'Conversaciones' })).toBeVisible();
  await expect(page.getByText('+56911111111')).toBeVisible();
  await expect(page.getByText('+56922222222')).toBeVisible();

  await expect(page.locator('#detailCustomer')).toHaveText('+56922222222');
  await expect(page.getByText('¿Tienen hora mañana?')).toBeVisible();

  await page.getByRole('button', { name: 'Llamadas' }).click();
  await page.getByText('+56911111111').click();
  await expect(page.getByText('El cliente reservó una hora.')).toBeVisible();
  await expect(page.locator('#detailActions')).toContainText('Reserva creada');
  await expect(page.locator('#detailActions')).not.toContainText('CREATE_BOOKING');
});

test('conversation inbox keeps calls usable when WhatsApp temporarily fails', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    recentCalls: [{
      id: 'call-1',
      callerNumber: '+56911111111',
      status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z',
      durationSeconds: 95,
      resolution: 'BOOKING_CREATED'
    }]
  })));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'WhatsApp no disponible' })
  }));
  await page.route('**/api/v1/calls/call-1', route => route.fulfill(json({
    call: {
      id: 'call-1',
      callerNumber: '+56911111111',
      status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z',
      durationSeconds: 95,
      resolution: 'BOOKING_CREATED'
    },
    summary: 'El cliente reservó una hora.',
    transcript: [],
    actions: []
  })));

  await page.goto('/conversations.html');

  await expect(page.getByText('+56911111111')).toBeVisible();
  await expect(page.locator('#detailCustomer')).toHaveText('+56911111111');
  await expect(page.getByText('El cliente reservó una hora.')).toBeVisible();
  await expect(page.locator('#message')).toContainText('WhatsApp no disponible');
});
