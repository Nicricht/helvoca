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
  await page.route('**/api/v1/customers', route => route.fulfill(json([])));

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
  await expect(page.locator('.brand-block strong')).toHaveText('NEGOCIO E2E');
  await expect(page.locator('#conversationList').getByText('+56911111111').first()).toBeVisible();
  await expect(page.locator('#conversationList').getByText('+56922222222').first()).toBeVisible();

  await expect(page.locator('#detailCustomer')).toHaveText('+56922222222');
  await expect(page.getByText('¿Tienen hora mañana?')).toBeVisible();
  await expect(page.locator('#detailTranscript .transcript-line strong').nth(1)).toHaveText('Negocio E2E');

  await page.goto('/conversations.html?channel=calls&conversation=call-1');
  await expect(page.getByRole('button', { name: 'Llamadas' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('#detailCustomer')).toHaveText('+56911111111');
  await expect(page.getByText('El cliente reservó una hora.')).toBeVisible();
  await expect(page.locator('#detailActions')).toContainText('Reserva creada');
  await expect(page.locator('#detailActions')).not.toContainText('CREATE_BOOKING');
  await expect(page.locator('#detailTranscript .transcript-line strong').nth(1)).toHaveText('Negocio E2E');
  await expect(page.locator('.nav-home')).toHaveAttribute('href', '/');
  await expect(page.locator('.nav-operations')).toHaveAttribute('href', '/operations.html');
  await expect(page.locator('.nav-config')).toHaveAttribute('href', '/settings.html');
  await expect(page.getByRole('link', { name: 'Probar' })).toHaveAttribute('href', '/simulator.html');
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
  await page.route('**/api/v1/customers', route => route.fulfill(json([])));
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

  await expect(page.locator('#conversationList').getByText('+56911111111')).toBeVisible();
  await expect(page.locator('#detailCustomer')).toHaveText('+56911111111');
  await expect(page.getByText('El cliente reservó una hora.')).toBeVisible();
  await expect(page.locator('#message')).toContainText('WhatsApp no disponible');
});

test('conversation inbox uses real customer names when customer ids are available', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    recentCalls: [{
      id: 'call-1', customerId: 'customer-call', callerNumber: '+56911111111',
      status: 'COMPLETED', startedAt: '2026-09-17T18:00:00Z', durationSeconds: 95,
      resolution: 'BOOKING_CREATED'
    }]
  })));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([{
    id: 'wa-1', customerId: 'customer-wa', channel: 'whatsapp',
    sender: '+56922222222', recipient: '+56933333333',
    openedAt: '2026-09-17T18:05:00Z', lastMessageAt: '2026-09-17T18:06:00Z'
  }])));
  await page.route('**/api/v1/customers', route => route.fulfill(json([
    { id: 'customer-call', name: 'Ana Voz', phone: '+56911111111' },
    { id: 'customer-wa', name: 'Bruno WhatsApp', phone: '+56922222222' }
  ])));
  await page.route('**/api/v1/calls/call-1', route => route.fulfill(json({
    call: {
      id: 'call-1', customerId: 'customer-call', callerNumber: '+56911111111',
      status: 'COMPLETED', startedAt: '2026-09-17T18:00:00Z', durationSeconds: 95,
      resolution: 'BOOKING_CREATED'
    },
    summary: 'Conversación de voz.', transcript: [], actions: []
  })));
  await page.route('**/api/v1/messaging/conversations/wa-1', route => route.fulfill(json({
    conversation: {
      id: 'wa-1', customerId: 'customer-wa', sender: '+56922222222',
      openedAt: '2026-09-17T18:05:00Z', lastMessageAt: '2026-09-17T18:06:00Z'
    },
    messages: []
  })));

  await page.goto('/conversations.html?channel=whatsapp&conversation=wa-1');
  await expect(page.locator('#conversationList')).toContainText('Bruno WhatsApp');
  await expect(page.locator('#detailCustomer')).toHaveText('Bruno WhatsApp');

  await page.goto('/conversations.html?channel=calls&conversation=call-1');
  await expect(page.locator('#conversationList')).toContainText('Ana Voz');
  await expect(page.locator('#detailCustomer')).toHaveText('Ana Voz');
});

test('conversation channel filters refresh and empty states work', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E', recentCalls: []
  })));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([])));
  await page.route('**/api/v1/customers', route => route.fulfill(json([])));

  await page.goto('/conversations.html');
  await expect(page.locator('#conversationList')).toHaveText('Todavía no hay conversaciones reales.');

  await page.getByRole('button', { name: 'Llamadas' }).click();
  await expect(page.getByRole('button', { name: 'Llamadas' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('#conversationList')).toHaveText('Todavía no hay llamadas reales.');

  await page.getByRole('button', { name: 'WhatsApp' }).click();
  await expect(page.getByRole('button', { name: 'WhatsApp' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('#conversationList')).toHaveText('WhatsApp todavía no tiene conversaciones reales.');

  await page.getByRole('button', { name: 'Actualizar' }).click();
  await expect(page.getByRole('button', { name: 'Actualizar' })).toBeEnabled();
  await expect(page.locator('#conversationList')).toHaveText('WhatsApp todavía no tiene conversaciones reales.');
});

test('conversation inbox reports a complete loading failure', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill({
    status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Panel no disponible' })
  }));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill({
    status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'WhatsApp no disponible' })
  }));
  await page.route('**/api/v1/customers', route => route.fulfill(json([])));

  await page.goto('/conversations.html');
  await expect(page.locator('#conversationList')).toHaveText('No pude cargar las conversaciones.');
  await expect(page.locator('#message')).toContainText('Panel no disponible');
  await expect(page.locator('#detailEmpty')).toBeVisible();
});

test('conversation inbox is responsive on desktop tablet and mobile', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    recentCalls: [{
      id: 'call-1', callerNumber: '+56911111111', status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z', durationSeconds: 95, resolution: 'BOOKING_CREATED'
    }]
  })));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([])));
  await page.route('**/api/v1/customers', route => route.fulfill(json([])));
  await page.route('**/api/v1/calls/call-1', route => route.fulfill(json({
    call: {
      id: 'call-1', callerNumber: '+56911111111', status: 'COMPLETED',
      startedAt: '2026-09-17T18:00:00Z', durationSeconds: 95, resolution: 'BOOKING_CREATED'
    },
    summary: 'El cliente reservó una hora.', transcript: [], actions: []
  })));

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/conversations.html');
  let inboxBox = await page.locator('.inbox-panel').boundingBox();
  let detailBox = await page.locator('.conversation-detail').boundingBox();
  expect(detailBox.x).toBeGreaterThan(inboxBox.x);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);

  await page.setViewportSize({ width: 768, height: 900 });
  inboxBox = await page.locator('.inbox-panel').boundingBox();
  detailBox = await page.locator('.conversation-detail').boundingBox();
  expect(detailBox.y).toBeGreaterThan(inboxBox.y);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Llamadas' })).toBeVisible();
  await expect(page.locator('#detailCustomer')).toHaveText('+56911111111');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});
