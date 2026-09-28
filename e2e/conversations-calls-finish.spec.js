const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockConversations(page) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/calls?**', route => route.fulfill(json({
    content: [
      {
        id: 'call-human',
        callerNumber: '+15550000001',
        direction: 'INBOUND',
        status: 'COMPLETED',
        startedAt: '2026-09-28T18:10:00Z',
        durationSeconds: 152,
        resolution: 'UNANSWERED_QUESTION_RECORDED'
      },
      {
        id: 'call-resolved',
        callerNumber: '+15550000002',
        direction: 'INBOUND',
        status: 'COMPLETED',
        startedAt: '2026-09-28T17:00:00Z',
        durationSeconds: 78,
        resolution: 'BOOKING_CREATED'
      }
    ],
    number: 0,
    size: 100,
    totalElements: 2,
    totalPages: 1
  })));

  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([{
    id: 'wa-demo',
    channel: 'whatsapp',
    sender: '+15550000003',
    recipient: '+15550000009',
    openedAt: '2026-09-28T16:40:00Z',
    lastMessageAt: '2026-09-28T16:44:00Z'
  }])));

  await page.route('**/api/v1/calls/call-human', route => route.fulfill(json({
    call: {
      id: 'call-human',
      callerNumber: '+15550000001',
      direction: 'INBOUND',
      status: 'COMPLETED',
      startedAt: '2026-09-28T18:10:00Z',
      durationSeconds: 152,
      resolution: 'UNANSWERED_QUESTION_RECORDED'
    },
    summary: 'La clienta preguntó por instalación industrial. La IA dejó una consulta pendiente para revisión.',
    transcript: [
      { id: 't1', speaker: 'USER', content: 'Necesito saber si instalan este equipo industrial.', sequenceNumber: 1, createdAt: '2026-09-28T18:10:08Z' },
      { id: 't2', speaker: 'ASSISTANT', content: 'No tengo esa información confirmada. Una persona debe revisarla.', sequenceNumber: 2, createdAt: '2026-09-28T18:10:15Z' }
    ],
    actions: [
      { id: 'a1', actionType: 'SEARCH_KNOWLEDGE', success: true, detail: 'Instalación industrial', createdAt: '2026-09-28T18:10:11Z' },
      { id: 'a2', actionType: 'UNANSWERED_QUESTION_RECORDED', success: true, detail: 'Confirmar servicio de instalación industrial', createdAt: '2026-09-28T18:10:18Z' }
    ]
  })));

  await page.route('**/api/v1/calls/call-resolved', route => route.fulfill(json({
    call: {
      id: 'call-resolved',
      callerNumber: '+15550000002',
      direction: 'INBOUND',
      status: 'COMPLETED',
      startedAt: '2026-09-28T17:00:00Z',
      durationSeconds: 78,
      resolution: 'BOOKING_CREATED'
    },
    summary: 'El cliente pidió una hora y la reserva quedó confirmada.',
    transcript: [],
    actions: [
      { id: 'a3', actionType: 'CREATE_BOOKING', success: true, detail: 'Reserva para mañana 10:00', createdAt: '2026-09-28T17:01:00Z' }
    ]
  })));

  await page.route('**/api/v1/messaging/conversations/wa-demo', route => route.fulfill(json({
    conversation: {
      id: 'wa-demo',
      channel: 'whatsapp',
      sender: '+15550000003',
      recipient: '+15550000009',
      openedAt: '2026-09-28T16:40:00Z',
      lastMessageAt: '2026-09-28T16:44:00Z'
    },
    messages: [
      { id: 'm1', direction: 'INBOUND', role: 'USER', content: '¿Atienden mañana?', createdAt: '2026-09-28T16:40:10Z' },
      { id: 'm2', direction: 'OUTBOUND', role: 'ASSISTANT', content: 'Sí. ¿Qué horario necesitas?', createdAt: '2026-09-28T16:40:18Z' }
    ]
  })));
}

test('conversation workspace is searchable and makes human attention obvious', async ({ page }) => {
  await mockConversations(page);
  await page.goto('/conversations.html');

  await expect(page.getByRole('heading', { level: 1, name: 'Conversaciones' })).toBeVisible();
  await expect(page.getByRole('searchbox', { name: 'Buscar conversaciones' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Necesita atención' })).toBeVisible();
  await expect(page.getByText('+15550000001')).toBeVisible();
  await expect(page.locator('#conversationList').getByText('Necesita atención humana')).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Qué ocurrió' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Conversación' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Resultado y seguimiento' })).toBeVisible();
  await expect(page.getByText('Cliente', { exact: true })).toBeVisible();
  await expect(page.getByText('RecepVoz', { exact: true })).toBeVisible();
  await expect(page.getByText('Confirmar servicio de instalación industrial')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('UNANSWERED_QUESTION_RECORDED');
  await expect(page.locator('body')).not.toContainText('SEARCH_KNOWLEDGE');

  await page.getByRole('searchbox', { name: 'Buscar conversaciones' }).fill('+15550000002');
  await expect(page.getByText('+15550000002')).toBeVisible();
  await expect(page.getByText('+15550000001')).toHaveCount(0);

  await page.getByRole('searchbox', { name: 'Buscar conversaciones' }).fill('');
  await page.getByRole('button', { name: 'Necesita atención' }).click();
  await expect(page.getByText('+15550000001')).toBeVisible();
  await expect(page.getByText('+15550000002')).toHaveCount(0);
});

test('simulator uses dark safe UI, progress feedback and human action labels', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Negocio Demo' })));
  await page.route('**/api/v1/simulator/sessions', async route => {
    await new Promise(resolve => setTimeout(resolve, 150));
    await route.fulfill(json({
      sessionId: '11111111-1111-1111-1111-111111111111',
      greeting: 'Hola, ¿en qué te ayudo?'
    }));
  });
  await page.route('**/api/v1/calls/11111111-1111-1111-1111-111111111111', route => route.fulfill(json({
    call: { resolution: 'BOOKING_CREATED' },
    actions: [{ actionType: 'CREATE_BOOKING', success: true, detail: 'Reserva simulada para mañana 10:00' }]
  })));

  await page.goto('/simulator.html');

  await expect(page.getByText(/no realiza llamadas reales/i)).toBeVisible();
  await expect(page.getByText(/no envía WhatsApp real/i)).toBeVisible();

  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  await expect(page.getByRole('status')).toContainText('Iniciando prueba segura');
  await expect(page.getByRole('button', { name: 'Nueva prueba' })).toBeDisabled();

  await expect(page.getByText('Hola, ¿en qué te ayudo?')).toBeVisible();
  await expect(page.locator('#resolution')).toHaveText('Reserva creada');
  await expect(page.locator('body')).not.toContainText('CREATE_BOOKING');

  const palette = await page.evaluate(() => ({
    bg: getComputedStyle(document.documentElement).getPropertyValue('--bg').trim(),
    surface: getComputedStyle(document.documentElement).getPropertyValue('--surface').trim()
  }));
  expect(palette.bg).toBe('#070a10');
  expect(palette.surface).toBe('#0d131d');
});


test('conversation workspace keeps WhatsApp usable when call history is unavailable', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/calls?**', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'Historial temporalmente no disponible' })
  }));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([{
    id: 'wa-demo',
    channel: 'whatsapp',
    sender: '+15550000003',
    recipient: '+15550000009',
    openedAt: '2026-09-28T16:40:00Z',
    lastMessageAt: '2026-09-28T16:44:00Z'
  }])));
  await page.route('**/api/v1/messaging/conversations/wa-demo', route => route.fulfill(json({
    conversation: {
      id: 'wa-demo',
      channel: 'whatsapp',
      sender: '+15550000003',
      recipient: '+15550000009',
      openedAt: '2026-09-28T16:40:00Z',
      lastMessageAt: '2026-09-28T16:44:00Z'
    },
    messages: [
      { id: 'm1', direction: 'INBOUND', role: 'USER', content: '¿Atienden mañana?', createdAt: '2026-09-28T16:40:10Z' }
    ]
  })));

  await page.goto('/conversations.html?whatsapp=wa-demo');

  await expect(page.locator('#sourceStatus')).toContainText('historial de llamadas');
  await expect(page.locator('#detailCustomer')).toHaveText('+15550000003');
  await expect(page.getByText('¿Atienden mañana?')).toBeVisible();
  await expect(page.getByRole('button', { name: 'WhatsApp' })).toHaveAttribute('aria-pressed', 'true');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});

test('conversation workspace has an intentional empty state', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/calls?**', route => route.fulfill(json({
    content: [], number: 0, size: 100, totalElements: 0, totalPages: 0
  })));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([])));

  await page.goto('/conversations.html');

  await expect(page.getByText('Todavía no hay conversaciones')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Selecciona una conversación' })).toBeVisible();
});
