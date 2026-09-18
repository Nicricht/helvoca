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
    { id: 'svc2', name: 'Masaje', durationMinutes: 60, price: 30000, active: true }
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
    { id: 'b1', customerId: 'cust1', serviceId: 'svc1', startAt: '2026-09-18T15:00:00Z', endAt: '2026-09-18T15:30:00Z', status: 'CONFIRMED', source: 'AI_CALL' },
    { id: 'b2', customerId: 'cust2', serviceId: 'svc2', startAt: '2026-09-19T16:00:00Z', endAt: '2026-09-19T17:00:00Z', status: 'CANCELLED', source: 'WHATSAPP' }
  ])));
  await page.route('**/api/v1/customers', route => route.fulfill(json([
    { id: 'cust1', name: 'Ana Reserva', phone: '+56922222222', email: 'ana@example.cl', createdAt: '2026-09-17T10:00:00Z' },
    { id: 'cust2', name: 'Bruno Masaje', phone: '+56955555555', email: 'bruno@example.cl', createdAt: '2026-09-17T11:00:00Z' }
  ])));
  await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([
    { id: 'o1', status: 'CONFIRMED', fulfillmentType: 'PICKUP', contactName: 'Juan Pedido', contactPhone: '+56933333333', total: 18990, currency: 'CLP', source: 'WHATSAPP', createdAt: '2026-09-17T17:30:00Z', lines: [{ name: 'Producto demo', quantity: 1, lineTotal: 18990 }] }
  ])));
  await page.route('**/api/v1/bookings/b1/context', route => route.fulfill(json({
    channel: 'VOICE',
    sourceReferenceId: 'call-1',
    call: {
      call: { id: 'call-1', callerNumber: '+56922222222', status: 'COMPLETED', resolution: 'BOOKING_CREATED', startedAt: '2026-09-17T18:00:00Z' },
      summary: 'Ana llamó para reservar peluquería y confirmó la hora.',
      transcript: [
        { id: 't1', speaker: 'USER', content: 'Quiero reservar peluquería.', createdAt: '2026-09-17T18:00:05Z' },
        { id: 't2', speaker: 'ASSISTANT', content: 'Tengo una hora disponible mañana.', createdAt: '2026-09-17T18:00:08Z' }
      ],
      actions: [
        { id: 'a1', actionType: 'AVAILABILITY_CHECKED', success: true, createdAt: '2026-09-17T18:00:09Z' },
        { id: 'a2', actionType: 'BOOKING_CREATED', success: true, createdAt: '2026-09-17T18:00:12Z' }
      ]
    },
    whatsapp: null,
    events: [
      { id: 'e1', eventType: 'BOOKING_CREATED', channel: 'VOICE', createdAt: '2026-09-17T18:00:12Z' }
    ]
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
  await expect(page.locator('#homeBookingsMetric')).toHaveAttribute('href', '/operations.html?tab=bookings');
  await expect(page.locator('#homePendingMetric')).toHaveAttribute('href', '/operations.html?tab=requests');
  await expect(page.locator('#homeRecentActivity')).toContainText('+56922222222');
  await expect(page.locator('#homeRecentActivity')).toContainText('+56911111111');
  await expect(page.locator('#homeBusinessWorkspace')).toBeVisible();
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).toContainText('Peluquería');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');
  await page.locator('#homeBookingService').selectOption('svc1');
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Bruno Masaje');
  await page.locator('#homeBookingStatus').selectOption('CONFIRMED');
  await expect(page.locator('.home-filter-result')).toContainText('1 de 2');
  await page.locator('#homeBookingClearFilters').click();
  await page.locator('#homeBookingSearch').fill('Bruno');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Ana Reserva');
  await page.getByRole('button', { name: /Pedidos/ }).click();
  await expect(page.locator('#homeOrdersList')).toContainText('Juan Pedido');
  await page.getByRole('button', { name: /Clientes/ }).click();
  await expect(page.locator('#homeCustomersList')).toContainText('Ana Reserva');
  await expect(page.locator('.nav-conversations')).toHaveAttribute('href', '/conversations.html');
  await expect(page.locator('#advancedPanel')).toBeHidden();
});
