const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockReadyHome(page) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({ email: 'admin@demo.cl' })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Negocio E2E', timezone: 'America/Santiago', language: 'es', humanTransferPhone: null
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: true,
    humanTransferConfigured: false,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'OPTIONAL_HUMAN_TRANSFER'
  })));
  await page.route('**/api/v1/services', route => route.fulfill(json([
    { id: 'svc1', name: 'Peluquería', durationMinutes: 30, price: 25000, active: true },
    { id: 'svc2', name: 'Barbería', durationMinutes: 45, price: 30000, active: true }
  ])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([
    { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
  ])));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([])));
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: true, name: 'Helvoca', language: 'es', active: true, capabilities: []
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([
    { id: 'phone1', provider: 'TWILIO', phoneNumber: '+56911111111', active: true }
  ])));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false, configured: false, purchaseAvailable: false, provider: 'TWILIO', message: 'No disponible en E2E'
  })));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    billingEnabled: true, checkoutConfigured: true, currentPlanCode: 'PRO', currentPlanName: 'Pro',
    subscriptionStatus: 'ACTIVE', awaitingProviderVerification: false
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    plan: 'PRO', status: 'ACTIVE', serviceAllowed: true, includedMinutes: 500, usedMinutes: 23,
    overageMinutes: 0, billingProviderConnected: true, legacyFallback: false
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/bookings', route => route.fulfill(json([
    { id: 'b1', customerId: 'cust1', serviceId: 'svc1', startAt: '2026-09-18T15:00:00Z', endAt: '2026-09-18T15:30:00Z', status: 'CONFIRMED', source: 'VOICE' },
    { id: 'b2', customerId: 'cust2', serviceId: 'svc2', startAt: '2026-09-19T16:00:00Z', endAt: '2026-09-19T16:45:00Z', status: 'CANCELLED', source: 'AI_WHATSAPP' }
  ])));
  await page.route('**/api/v1/bookings/b1/trace', route => route.fulfill(json({
    bookingId: 'b1', operationId: 'op1', origin: 'VOICE', callId: 'call-1', conversationId: null,
    history: [{ eventType: 'BOOKING_CREATED', status: 'CONFIRMED', channel: 'VOICE', createdAt: '2026-09-17T18:01:00Z' }]
  })));
  await page.route('**/api/v1/calls/call-1', route => route.fulfill(json({
    call: { id: 'call-1', callerNumber: '+56922222222', status: 'COMPLETED', resolution: 'BOOKING_CREATED', startedAt: '2026-09-17T18:00:00Z', durationSeconds: 95 },
    summary: 'Ana llamó para reservar Peluquería.',
    transcript: [
      { id: 't1', speaker: 'USER', content: 'Quiero reservar peluquería.', createdAt: '2026-09-17T18:00:05Z' },
      { id: 't2', speaker: 'ASSISTANT', content: 'Perfecto, quedó reservada.', createdAt: '2026-09-17T18:01:00Z' }
    ],
    actions: [{ id: 'a1', actionType: 'BOOKING_CREATED', success: true, detail: 'Peluquería', createdAt: '2026-09-17T18:01:00Z' }]
  })));
  await page.route('**/api/v1/customers', route => route.fulfill(json([
    { id: 'cust1', name: 'Ana Reserva', phone: '+56922222222', email: 'ana@example.cl', createdAt: '2026-09-10T09:00:00Z' },
    { id: 'cust2', name: 'Bruno Corte', phone: '+56955555555', email: 'bruno@example.cl', createdAt: '2026-09-11T09:00:00Z' }
  ])));
  await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([
    { id: 'o1', operationId: 'op-order-1', status: 'CONFIRMED', fulfillmentType: 'PICKUP', contactName: 'Juan Pedido', contactPhone: '+56933333333', total: 18990, currency: 'CLP', source: 'WHATSAPP', createdAt: '2026-09-17T19:00:00Z', lines: [{ name: 'Hamburguesa', quantity: 2, unitPrice: 7000, lineTotal: 14000 }, { name: 'Bebida', quantity: 1, unitPrice: 4990, lineTotal: 4990 }] }
  ])));
  await page.route('**/api/v1/operation-events?operationId=op-order-1', route => route.fulfill(json([
    { eventType: 'ORDER_CONFIRMED', channel: 'WHATSAPP', sourceReferenceId: 'wa-order-1', status: 'CONFIRMED', createdAt: '2026-09-17T19:00:00Z' }
  ])));
  await page.route('**/api/v1/messaging/conversations/wa-order-1', route => route.fulfill(json({
    conversation: { id: 'wa-order-1', sender: '+56933333333', channel: 'whatsapp', openedAt: '2026-09-17T18:55:00Z', lastMessageAt: '2026-09-17T19:00:00Z' },
    messages: [{ id: 'm1', role: 'USER', content: 'Quiero dos hamburguesas.', createdAt: '2026-09-17T18:56:00Z' }, { id: 'm2', role: 'ASSISTANT', content: 'Pedido confirmado.', createdAt: '2026-09-17T19:00:00Z' }]
  })));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    timezone: 'America/Santiago',
    localNow: '2026-09-17T16:30:00-03:00',
    callsToday: 3,
    callDurationSecondsToday: 480,
    bookingsToday: 2,
    newCustomersToday: 1,
    openRequests: 1,
    unansweredQuestions: 2,
    callFailuresToday: 0,
    estimatedCallCostTodayUsd: 0.7,
    recentCalls: [{
      id: 'call-1', callerNumber: '+56911111111', status: 'COMPLETED', resolution: 'Reserva creada',
      startedAt: '2026-09-17T18:00:00Z', durationSeconds: 95
    }],
    recentRequests: [],
    unanswered: []
  })));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([
    {
      id: 'wa-today', channel: 'whatsapp', sender: '+56922222222', recipient: '+56933333333',
      openedAt: '2026-09-17T17:00:00Z', lastMessageAt: '2026-09-17T18:10:00Z'
    },
    {
      id: 'wa-old', channel: 'whatsapp', sender: '+56944444444', recipient: '+56933333333',
      openedAt: '2026-09-16T15:00:00Z', lastMessageAt: '2026-09-16T15:10:00Z'
    }
  ])));
}

test('ready customer sees live operational home instead of setup cards', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Helvoca está atendiendo 🟢');
  await expect(page.locator('#operationalOverview')).toBeVisible();
  await expect(page.locator('#statusGrid')).toBeHidden();
  await expect(page.locator('#homeCallsToday')).toHaveText('3');
  await expect(page.locator('#homeWhatsAppToday')).toHaveText('1');
  await expect(page.locator('#homeBookingsToday')).toHaveText('2');
  await expect(page.locator('#homePending')).toHaveText('3');
  await expect(page.locator('#homeCallsMetric')).toHaveAttribute('href', '/conversations.html?channel=calls');
  await expect(page.locator('#homeWhatsAppMetric')).toHaveAttribute('href', '/conversations.html?channel=whatsapp');
  await expect(page.locator('#homeBookingsMetric')).toHaveAttribute('href', '/?tab=bookings');
  await expect(page.locator('#homePendingMetric')).toHaveAttribute('href', '/?tab=requests');
  await expect(page.locator('#homeRecentActivity')).toContainText('+56922222222');
  await expect(page.locator('#homeRecentActivity')).toContainText('+56911111111');
  await expect(page.locator('.nav-conversations')).toHaveAttribute('href', '/conversations.html');
  await expect(page.locator('#homeBusinessWorkspace')).toBeVisible();
  await expect(page.locator('#homeBusinessWorkspace #businessTabs')).toBeVisible();
  await expect(page.locator('#homeBusinessWorkspace #bookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#bookingFilterService')).toBeVisible();
  await page.locator('#bookingFilterService').selectOption('svc2');
  await expect(page.locator('[data-table="bookings"] tbody')).toContainText('Bruno Corte');
  await expect(page.locator('[data-table="bookings"] tbody')).not.toContainText('Ana Reserva');
  await page.locator('#bookingFilterClear').click();
  await expect(page.locator('[data-table="bookings"] tbody')).toContainText('Ana Reserva');
  await expect(page.locator('#bookingFilterCount')).toContainText('2 de 2');
  await page.locator('[data-booking-open][data-entity-id="b1"]').first().click();
  await expect(page.locator('#businessDetailDrawer')).toBeVisible();
  await expect(page.locator('#businessDetailDrawer')).toContainText('Ana llamó para reservar Peluquería.');
  await expect(page.locator('#businessDetailDrawer')).toContainText('Quiero reservar peluquería.');
  await expect(page.locator('#businessDetailDrawer')).toContainText('Reserva creada');
  await expect(page.locator('#businessDetailConversationLink')).toHaveAttribute('href', '/conversations.html?call=call-1');
  await page.locator('#businessDetailClose').click();
  await page.getByRole('button', { name: /Pedidos/ }).click();
  await page.locator('[data-order-open][data-entity-id="o1"]').first().click();
  await expect(page.locator('#businessDetailDrawer')).toContainText('2 × Hamburguesa');
  await expect(page.locator('#businessDetailDrawer')).toContainText('Quiero dos hamburguesas.');
  await expect(page.locator('#businessDetailConversationLink')).toHaveAttribute('href', '/conversations.html?whatsapp=wa-order-1');
  await expect(page.locator('#advancedPanel')).toBeHidden();
});
