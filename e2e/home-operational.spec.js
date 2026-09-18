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
    { id: 'b2', customerId: 'cust2', serviceId: 'svc2', startAt: '2026-09-19T16:00:00Z', endAt: '2026-09-19T17:00:00Z', status: 'CANCELLED', source: 'AI_WHATSAPP' }
  ])));
  await page.route('**/api/v1/customers', route => route.fulfill(json([
    { id: 'cust1', name: 'Ana Reserva', phone: '+56922222222', email: 'ana@example.cl', createdAt: '2026-09-17T10:00:00Z' },
    { id: 'cust2', name: 'Bruno Masaje', phone: '+56955555555', email: 'bruno@example.cl', createdAt: '2026-09-17T11:00:00Z' }
  ])));
  await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([
    { id: 'o1', operationId: 'op1', sourceReferenceId: 'wa-order', status: 'CONFIRMED', fulfillmentType: 'DELIVERY', contactName: 'Juan Pedido', contactPhone: '+56933333333', deliveryAddress: 'Av. Demo 123, Santiago', subtotal: 15990, deliveryFee: 3000, total: 18990, currency: 'CLP', source: 'WHATSAPP', createdAt: '2026-09-17T17:30:00Z', lines: [{ name: 'Producto demo', quantity: 1, unitPrice: 15990, lineTotal: 15990 }] }
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
  await page.route('**/api/v1/bookings/b2/context', route => route.fulfill(json({
    channel: 'WHATSAPP',
    sourceReferenceId: 'wa-booking-2',
    call: null,
    whatsapp: {
      conversation: { id: 'wa-booking-2', sender: '+56955555555', recipient: '+56911111111', openedAt: '2026-09-17T19:00:00Z', lastMessageAt: '2026-09-17T19:05:00Z' },
      messages: [
        { id: 'wm1', direction: 'INBOUND', role: 'USER', content: 'Quiero reservar un masaje.', createdAt: '2026-09-17T19:00:10Z' },
        { id: 'wm2', direction: 'OUTBOUND', role: 'ASSISTANT', content: 'Tu reserva quedó confirmada.', createdAt: '2026-09-17T19:00:20Z' }
      ]
    },
    events: [
      { id: 'e2', eventType: 'BOOKING_CREATED', channel: 'WHATSAPP', createdAt: '2026-09-17T19:00:20Z' }
    ]
  })));
  await page.route('**/api/v1/messaging/conversations/wa-order', route => route.fulfill(json({
    conversation: { id: 'wa-order', sender: '+56933333333', openedAt: '2026-09-17T17:20:00Z', lastMessageAt: '2026-09-17T17:30:00Z' },
    messages: [
      { id: 'om1', direction: 'INBOUND', role: 'USER', content: 'Quiero un Producto demo.', createdAt: '2026-09-17T17:20:10Z' },
      { id: 'om2', direction: 'OUTBOUND', role: 'ASSISTANT', content: 'Pedido confirmado.', createdAt: '2026-09-17T17:20:20Z' }
    ]
  })));
  await page.route('**/api/v1/operation-events?operationId=op1', route => route.fulfill(json([
    { id: 'oe2', eventType: 'ORDER_CONFIRMED', actorType: 'AI', channel: 'WHATSAPP', createdAt: '2026-09-17T17:30:00Z' },
    { id: 'oe1', eventType: 'ORDER_QUOTED', actorType: 'AI', channel: 'WHATSAPP', createdAt: '2026-09-17T17:20:15Z' }
  ])));
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
  test.setTimeout(90000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let preparedCampaignRequest = null;
  let activationRequest = null;
  let unsafeOutboundCalls = 0;
  const campaignHistory = [{
    id: 'campaign-old',
    reason: 'Cierre temprano',
    status: 'PREPARED',
    goal: 'INFORM',
    strategy: 'CHEAPEST',
    recipientCount: 2,
    createdAt: '2026-09-17T12:00:00Z',
    activationReady: false,
    activationBlockers: [{ code: 'OUTBOUND_DELIVERY_DISABLED', message: 'La entrega real de mensajes está desactivada.' }]
  }];
  page.on('request', request => {
    if (/\/api\/v1\/outbound-messages\/.*\/(queue|dispatch)$/.test(request.url())) unsafeOutboundCalls += 1;
  });
  await page.route('**/api/v1/booking-incident-campaigns/*/activate', async route => {
    expect(route.request().method()).toBe('POST');
    activationRequest = route.request().postDataJSON();
    const target = campaignHistory.find(item => item.id === 'campaign-1');
    if (target) {
      target.status = 'ACTIVATED';
      target.activationReady = false;
      target.activationBlockers = [{ code: 'CAMPAIGN_NOT_PREPARED', message: 'La campaña ya no está en estado preparado.' }];
    }
    await route.fulfill(json({
      campaignId: 'campaign-1',
      status: 'ACTIVATED',
      queuedRecipients: 1
    }));
  });

  await page.route('**/api/v1/booking-incident-campaigns', async route => {
    if (route.request().method() === 'GET') {
      await route.fulfill(json(campaignHistory));
      return;
    }
    expect(route.request().method()).toBe('POST');
    preparedCampaignRequest = route.request().postDataJSON();
    const created = {
      id: 'campaign-1',
      status: 'PREPARED',
      goal: preparedCampaignRequest.goal,
      strategy: preparedCampaignRequest.strategy,
      recipientCount: preparedCampaignRequest.recipients.length,
      createdAt: '2026-09-18T12:00:00Z',
      recipients: []
    };
    campaignHistory.unshift({
      ...created,
      reason: preparedCampaignRequest.reason,
      activationReady: true,
      activationBlockers: []
    });
    await route.fulfill(json(created));
  });

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Negocio E2E está atendiendo 🟢');
  await expect(page.locator('#operationalOverview')).toBeVisible();
  await expect(page.locator('#operationalOverview')).toHaveCount(1);
  await expect(page.locator('#homeRecentActivity')).toHaveCount(0);
  await expect(page.locator('#statusGrid')).toBeHidden();
  await expect(page.locator('#homeCallsToday')).toHaveText('3');
  await expect(page.locator('#homeWhatsAppToday')).toHaveText('1');
  await expect(page.locator('#homeBookingsToday')).toHaveText('2');
  await expect(page.locator('#homeCustomersToday')).toHaveText('1');
  await expect(page.locator('#homeRequestsToday')).toHaveText('1');
  await expect(page.locator('#homeQuestionsToday')).toHaveText('2');
  await expect(page.locator('#homeFailuresToday')).toHaveText('0');
  await expect(page.locator('#homeMinutesToday')).toHaveText('8:00');
  await expect(page.locator('#homeCallsMetric')).toHaveAttribute('href', '/conversations.html?channel=calls');
  await expect(page.locator('#homeWhatsAppMetric')).toHaveAttribute('href', '/conversations.html?channel=whatsapp');
  await expect(page.locator('#homeBookingsMetric')).toHaveAttribute('href', '/?tab=bookings#homeBusinessWorkspace');
  await expect(page.locator('#homeRequestsMetric')).toHaveAttribute('href', '/?tab=requests#homeBusinessWorkspace');
  await expect(page.locator('#homeBusinessWorkspace')).toBeVisible();

  await page.locator('#homeIncidentToggle').click();
  await expect(page.locator('#homeIncidentPanel')).toBeVisible();
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Cierre temprano');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('PREPARED');
  await expect(page.getByRole('button', { name: 'Activar campaña' }).first()).toBeDisabled();

  await expect(page.locator('#homeIncidentCalendarDays')).toHaveCount(0);
  await expect(page.locator('#homeIncidentReason')).toBeVisible();
  await expect(page.locator('#homeIncidentDate')).toBeVisible();
  await expect(page.locator('#homeIncidentTimeFrom')).toBeVisible();
  await expect(page.locator('#homeIncidentTimeTo')).toBeVisible();
  await expect(page.locator('#homeIncidentPanel select')).toHaveCount(6);
  await expect(page.locator('#homeIncidentPanel textarea')).toHaveCount(0);

  await page.locator('#homeIncidentGoal').selectOption('INFORM');
  await expect(page.locator('#homeIncidentGoal')).toHaveValue('INFORM');
  await page.locator('#homeIncidentGoal').selectOption('RESCHEDULE');
  await expect(page.locator('#homeIncidentGoal')).toHaveValue('RESCHEDULE');

  await page.locator('#homeIncidentStrategy').selectOption('WHATSAPP');
  await expect(page.locator('#homeIncidentStrategy')).toHaveValue('WHATSAPP');
  await page.locator('#homeIncidentStrategy').selectOption('CHEAPEST');
  await expect(page.locator('#homeIncidentStrategy')).toHaveValue('CHEAPEST');

  await expect(page.locator('#homeIncidentPreviewBtn')).toBeDisabled();

  await page.locator('#homeIncidentReason').selectOption({ label: 'No abrir' });
  await expect(page.locator('#homeIncidentTimeFrom')).toHaveValue('00:00');
  await expect(page.locator('#homeIncidentTimeTo')).toHaveValue('24:00');
  await page.locator('#homeIncidentDate').selectOption('2026-09-18');
  await expect(page.locator('#homeIncidentDate')).toHaveValue('2026-09-18');
  await expect(page.locator('#homeIncidentTimeFrom')).toHaveValue('00:00');
  await expect(page.locator('#homeIncidentTimeTo')).toHaveValue('24:00');

  await page.locator('#homeIncidentReason').selectOption({ label: 'Cerrar antes' });
  await expect(page.locator('#homeIncidentPreviewBtn')).toBeEnabled();
  await expect(page.locator('#homeIncidentTimeFrom')).toHaveValue('12:00');
  await expect(page.locator('#homeIncidentTimeTo')).toHaveValue('12:30');

  await page.locator('#homeIncidentTimeFrom').selectOption('13:00');
  await expect.poll(() => page.locator('#homeIncidentTimeTo option[value="13:00"]').evaluate(option => option.disabled)).toBe(true);
  await expect.poll(() => page.locator('#homeIncidentTimeTo option[value="12:30"]').evaluate(option => option.disabled)).toBe(true);

  await page.locator('#homeIncidentTimeFrom').selectOption('09:00');
  await page.locator('#homeIncidentTimeTo').selectOption('13:00');
  await expect(page.locator('#homeIncidentImpactSummary')).toContainText('1 cliente');
  await expect(page.locator('#homeIncidentImpactSummary')).toContainText('1 reserva');
  await expect(page.locator('#homeIncidentPreviewBtn')).toBeEnabled();
  await page.locator('#homeIncidentPreviewBtn').click();
  await expect(page.locator('#homeIncidentPreview')).toContainText('1 cliente afectado');
  await expect(page.locator('#homeIncidentPreview')).toContainText('Ana Reserva');
  await expect(page.locator('#homeIncidentPreview')).toContainText('WhatsApp primero');
  await expect(page.locator('#homeIncidentPreview')).toContainText('Cierre anticipado');
  await expect(page.locator('#homeIncidentPreview')).not.toContainText('Bruno Masaje');
  await expect(page.locator('.home-incident-select')).toHaveCount(1);
  await page.locator('.home-incident-select').uncheck();
  await expect(page.locator('#homeIncidentPrepareCampaign')).toBeDisabled();
  await page.locator('.home-incident-select').check();
  await expect(page.locator('#homeIncidentPrepareCampaign')).toBeEnabled();

  page.once('dialog', dialog => dialog.accept());
  await page.locator('#homeIncidentPrepareCampaign').click();
  await expect(page.locator('#homeIncidentPreview')).toContainText('Campaña preparada');
  await expect(page.locator('#homeIncidentPreview')).toContainText('PREPARED');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Cierre anticipado');
  await expect(page.getByRole('button', { name: 'Activar campaña' }).first()).toBeEnabled();

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('contactará a 1 cliente');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Activar campaña' }).first().click();
  await expect(page.locator('#homeIncidentPreview')).toContainText('Campaña activada');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('ACTIVATED');
  await expect(page.getByRole('button', { name: 'Campaña activada' }).first()).toBeDisabled();
  expect(activationRequest).toEqual({ confirmed: true });
  expect(preparedCampaignRequest).not.toBeNull();
  expect(preparedCampaignRequest.reason).toBe('Cierre anticipado');
  expect(preparedCampaignRequest.goal).toBe('RESCHEDULE');
  expect(preparedCampaignRequest.strategy).toBe('CHEAPEST');
  expect(preparedCampaignRequest.recipients).toHaveLength(1);
  expect(preparedCampaignRequest.recipients[0].customerId).toBe('cust1');
  expect(preparedCampaignRequest.recipients[0].bookingIds).toEqual(['b1']);
  expect(preparedCampaignRequest.recipients[0].channelPreference).toBe('CHEAPEST');
  expect(unsafeOutboundCalls).toBe(0);

  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).toContainText('Peluquería');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');
  await expect(page.locator('#homeBookingSource option')).toHaveText(['Todos', 'Llamada', 'WhatsApp', 'Manual', 'API']);
  await page.locator('#homeBookingSource').selectOption('CALL');
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Bruno Masaje');
  await expect(page.locator('.home-filter-result')).toContainText('1 de 2');
  await page.locator('#homeBookingClearFilters').click();
  await page.locator('#homeBookingService').selectOption('svc1');
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Bruno Masaje');
  await page.locator('#homeBookingStatus').selectOption('CONFIRMED');
  await expect(page.locator('.home-filter-result')).toContainText('1 de 2');
  await page.locator('#homeBookingClearFilters').click();
  await page.locator('#homeBookingSearch').fill('Bruno');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Ana Reserva');
  await page.locator('#homeBookingClearFilters').click();

  await page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]').click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Ana llamó para reservar peluquería');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Quiero reservar peluquería.');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').first()).toHaveText('Ana Reserva');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').nth(1)).toHaveText('Negocio E2E');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts > * > span')).toHaveText(['Servicio', 'Teléfono', 'Llamada', 'Reserva', 'Origen', 'Estado']);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Reservada para');
  await expect(page.locator('#homeBookingDetailBody .home-detail-fact-link')).toHaveAttribute('href', '/conversations.html?channel=calls&conversation=call-1');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts')).not.toContainText('Inicio');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts')).not.toContainText('Fin');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Qué hizo Helvoca');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Historial');
  await expect(page.locator('#homeBookingDetailBody .home-detail-link')).toHaveAttribute('href', '/conversations.html?channel=calls&conversation=call-1');
  await page.locator('#homeBookingDetailClose').click();

  await page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b2"]').click();
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Conversación de WhatsApp');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Quiero reservar un masaje.');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Tu reserva quedó confirmada.');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').first()).toHaveText('Bruno Masaje');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').nth(1)).toHaveText('Negocio E2E');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts > * > span')).toHaveText(['Servicio', 'Teléfono', 'WhatsApp', 'Reserva', 'Origen', 'Estado']);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Reservada para');
  await expect(page.locator('#homeBookingDetailBody .home-detail-fact-link')).toHaveAttribute('href', '/conversations.html?channel=whatsapp&conversation=wa-booking-2');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Qué hizo Helvoca');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Historial');
  await expect(page.locator('#homeBookingDetailBody .home-detail-link')).toHaveAttribute('href', '/conversations.html?channel=whatsapp&conversation=wa-booking-2');
  await page.locator('#homeBookingDetailClose').click();

  await page.getByRole('tab', { name: /Pedidos/ }).click();
  await expect(page.locator('#homeOrdersList')).toContainText('Juan Pedido');
  await page.locator('#homeOrdersList .home-business-table [data-home-order-id="o1"]').click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Confirmado');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Producto demo');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('15.990');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Av. Demo 123, Santiago');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Quiero un Producto demo.');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').first()).toHaveText('Juan Pedido');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').nth(1)).toHaveText('Negocio E2E');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Qué hizo Helvoca');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Historial');
  await expect(page.locator('#homeBookingDetailBody .home-detail-link')).toHaveAttribute('href', '/conversations.html?channel=whatsapp&conversation=wa-order');
  await expect(page.getByRole('button', { name: 'Empezar preparación' })).toBeVisible();
  await page.locator('#homeBookingDetailClose').click();

  await page.getByRole('tab', { name: /Solicitudes/ }).click();
  await expect(page.locator('#homeRequestsList')).toContainText('No hay solicitudes recientes.');

  await page.getByRole('tab', { name: /Clientes/ }).click();
  await expect(page.locator('#homeCustomersList')).toContainText('Ana Reserva');
  await expect(page.locator('.nav-conversations')).toHaveAttribute('href', '/conversations.html');
  await expect(page.locator('#advancedPanel')).toBeHidden();
});


test('reservation filters drawer and conversation links work', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);
  await page.goto('/');

  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');

  await page.locator('#homeBookingDate').selectOption('today');
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Bruno Masaje');

  await page.locator('#homeBookingDate').selectOption('tomorrow');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Ana Reserva');

  await page.locator('#homeBookingClearFilters').click();
  await page.locator('#homeBookingService').selectOption('svc1');
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Bruno Masaje');

  await page.locator('#homeBookingStatus').selectOption('CANCELLED');
  await expect(page.locator('#homeBookingsList')).toContainText('No hay reservas que coincidan con estos filtros.');

  await page.locator('#homeBookingClearFilters').click();
  await page.locator('#homeBookingStatus').selectOption('CANCELLED');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');

  await page.locator('#homeBookingClearFilters').click();
  await page.locator('#homeBookingSource').selectOption('WHATSAPP');
  await expect(page.locator('#homeBookingsList')).toContainText('Bruno Masaje');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Ana Reserva');

  await page.locator('#homeBookingClearFilters').click();
  await page.locator('#homeBookingSearch').fill('Ana');
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingsList')).not.toContainText('Bruno Masaje');

  await page.locator('#homeBookingClearFilters').click();
  await expect(page.locator('#homeBookingSearch')).toHaveValue('');
  await expect(page.locator('#homeBookingDate')).toHaveValue('all');
  await expect(page.locator('#homeBookingService')).toHaveValue('all');
  await expect(page.locator('#homeBookingStatus')).toHaveValue('all');
  await expect(page.locator('#homeBookingSource')).toHaveValue('all');
  await expect(page.locator('.home-filter-result')).toContainText('2 de 2');

  await page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]').click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Reservada para');
  await expect(page.locator('#homeBookingDetailBody .home-detail-fact-link')).toHaveAttribute(
    'href',
    '/conversations.html?channel=calls&conversation=call-1'
  );
  await expect(page.locator('#homeBookingDetailBody .home-detail-link')).toHaveAttribute(
    'href',
    '/conversations.html?channel=calls&conversation=call-1'
  );
  await page.keyboard.press('Escape');
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();

  await page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b2"]').click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailBody .home-detail-fact-link')).toHaveAttribute(
    'href',
    '/conversations.html?channel=whatsapp&conversation=wa-booking-2'
  );
  await expect(page.locator('#homeBookingDetailBody .home-detail-link')).toHaveAttribute(
    'href',
    '/conversations.html?channel=whatsapp&conversation=wa-booking-2'
  );
  await page.locator('#homeBookingDetailClose').click();
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
});


test('orders list drawer and conversation work', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);
  await page.goto('/');

  await page.getByRole('tab', { name: /Pedidos/ }).click();
  await expect(page.locator('#homeOrdersList')).toContainText('Juan Pedido');
  await expect(page.locator('#homeOrdersList')).toContainText('Confirmado');

  await page.locator('#homeOrdersList .home-business-table [data-home-order-id="o1"]').click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Confirmado');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Producto demo');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('15.990');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('3.000');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('18.990');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Av. Demo 123, Santiago');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts > * > span')).toHaveText([
    'Cliente', 'Teléfono', 'Entrega', 'Origen', 'Estado', 'Subtotal', 'Despacho', 'Total'
  ]);
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts')).toContainText('Juan Pedido');
  await expect(page.locator('#homeBookingDetailBody .home-detail-link')).toHaveAttribute(
    'href',
    '/conversations.html?channel=whatsapp&conversation=wa-order'
  );
  await expect(page.getByRole('button', { name: 'Empezar preparación' })).toBeVisible();
  await page.locator('#homeBookingDetailClose').click();
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
});

test('orders status transition works', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let orderStatus = 'CONFIRMED';
  let statusPatch = null;

  await page.route('**/api/v1/commercial/orders/o1/status', async route => {
    statusPatch = route.request().postDataJSON();
    orderStatus = statusPatch.status;
    await route.fulfill(json({ id: 'o1', status: orderStatus }));
  });

  await page.route('**/api/v1/commercial/orders', async route => {
    await route.fulfill(json([{
      id: 'o1', operationId: 'op1', sourceReferenceId: 'wa-order',
      status: orderStatus, fulfillmentType: 'DELIVERY',
      contactName: 'Juan Pedido', contactPhone: '+56933333333',
      deliveryAddress: 'Av. Demo 123, Santiago',
      subtotal: 15990, deliveryFee: 3000, total: 18990, currency: 'CLP',
      source: 'WHATSAPP', createdAt: '2026-09-17T17:30:00Z',
      lines: [{ name: 'Producto demo', quantity: 1, unitPrice: 15990, lineTotal: 15990 }]
    }]));
  });

  await page.goto('/');
  await page.getByRole('tab', { name: /Pedidos/ }).click();
  await page.locator('#homeOrdersList .home-business-table [data-home-order-id="o1"]').click();
  await page.getByRole('button', { name: 'Empezar preparación' }).click();

  await expect.poll(() => statusPatch).toEqual({ status: 'PREPARING' });
  await expect(page.locator('#homeOrdersList')).toContainText('Preparando');
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
});


test('requests workspace renders active requests', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E',
    timezone: 'America/Santiago',
    localNow: '2026-09-17T16:30:00-03:00',
    callsToday: 3, callDurationSecondsToday: 480, bookingsToday: 2,
    newCustomersToday: 1, openRequests: 1, unansweredQuestions: 0,
    callFailuresToday: 0, estimatedCallCostTodayUsd: 0.7,
    recentCalls: [],
    recentRequests: [{
      id: 'req1',
      title: 'Confirmar dirección',
      description: 'Cliente pidió cambiar dirección de entrega',
      status: 'OPEN',
      contactName: 'Juan Pedido'
    }],
    unanswered: []
  })));

  await page.goto('/');
  await page.getByRole('tab', { name: /Solicitudes/ }).click();
  await expect(page.locator('#homeRequestsList')).toContainText('Confirmar dirección');
  await expect(page.locator('#homeRequestsList')).toContainText('Cliente pidió cambiar dirección de entrega');
  await expect(page.locator('#homeRequestsList')).toContainText('Abierta');
});

test('requests workspace renders its empty state', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);
  await page.goto('/');

  await page.getByRole('tab', { name: /Solicitudes/ }).click();
  await expect(page.locator('#homeRequestsList')).toHaveText('No hay solicitudes recientes.');
});

test('customers workspace sorts and renders contact data', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);
  await page.goto('/');

  await page.getByRole('tab', { name: /Clientes/ }).click();
  const customers = page.locator('#homeCustomersList .home-simple-row strong');
  await expect(customers).toHaveCount(2);
  await expect(customers.nth(0)).toHaveText('Ana Reserva');
  await expect(customers.nth(1)).toHaveText('Bruno Masaje');
  await expect(page.locator('#homeCustomersList')).toContainText('+56922222222');
  await expect(page.locator('#homeCustomersList')).toContainText('ana@example.cl');
  await expect(page.locator('.nav-conversations')).toHaveAttribute('href', '/conversations.html');
});

test('orders requests customers remain operable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);
  await page.goto('/');

  await page.getByRole('tab', { name: /Pedidos/ }).click();
  await expect(page.locator('#homeOrdersList .home-business-mobile-list')).toBeVisible();
  await page.locator('#homeOrdersList .home-business-mobile-card[data-home-order-id="o1"]').click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  const drawerBox = await page.locator('#homeBookingDetailDrawer').boundingBox();
  expect(drawerBox).not.toBeNull();
  expect(drawerBox.x).toBeGreaterThanOrEqual(0);
  expect(drawerBox.x + drawerBox.width).toBeLessThanOrEqual(390);
  await page.locator('#homeBookingDetailClose').click();

  await page.getByRole('tab', { name: /Solicitudes/ }).click();
  await expect(page.locator('#homeRequestsList')).toContainText('No hay solicitudes recientes.');

  await page.getByRole('tab', { name: /Clientes/ }).click();
  await expect(page.locator('#homeCustomersList')).toContainText('Ana Reserva');
});


test('booking cancellation from drawer works', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let cancelCalls = 0;
  await page.route('**/api/v1/bookings/b1', async route => {
    expect(route.request().method()).toBe('DELETE');
    cancelCalls += 1;
    await route.fulfill({ status: 204, body: '' });
  });

  await page.goto('/');
  await page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]').click();

  await expect(page.getByRole('button', { name: 'Cancelar reserva' })).toBeVisible();
  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Ana Reserva');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Cancelar reserva' }).click();

  await expect.poll(() => cancelCalls).toBe(1);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Cancelada');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Esta reserva está cancelada');
  await expect(page.getByRole('button', { name: 'Cancelar reserva' })).toHaveCount(0);
  await expect(page.locator('#homeBookingsList [data-home-booking-id="b1"] .home-pill').first()).toHaveText('Cancelada');
});


test('booking reschedule checks availability and updates the drawer', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  const availabilityCalls = [];
  let patchPayload = null;

  await page.route('**/api/v1/bookings/availability?**', async route => {
    const url = new URL(route.request().url());
    const call = {
      serviceId: url.searchParams.get('serviceId'),
      startAt: url.searchParams.get('startAt'),
      excludeBookingId: url.searchParams.get('excludeBookingId')
    };
    availabilityCalls.push(call);
    await route.fulfill(json({
      serviceId: call.serviceId,
      startAt: call.startAt,
      endAt: new Date(new Date(call.startAt).getTime() + 30 * 60 * 1000).toISOString(),
      available: availabilityCalls.length > 1
    }));
  });

  await page.route('**/api/v1/bookings/b1', async route => {
    expect(route.request().method()).toBe('PATCH');
    patchPayload = route.request().postDataJSON();
    await route.fulfill(json({
      id: 'b1',
      customerId: 'cust1',
      serviceId: 'svc1',
      startAt: patchPayload.startAt,
      endAt: new Date(new Date(patchPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
      status: 'CONFIRMED',
      source: 'AI_CALL',
      notes: patchPayload.notes
    }));
  });

  await page.goto('/');
  await page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]').click();

  await page.getByRole('button', { name: 'Reprogramar' }).click();
  await expect(page.locator('#homeBookingReschedulePanel')).toBeVisible();
  await expect(page.locator('#homeBookingRescheduleDate')).toBeVisible();
  await expect(page.locator('#homeBookingRescheduleTime')).toBeVisible();

  await page.locator('#homeBookingRescheduleDate').selectOption({ index: 1 });
  await page.locator('#homeBookingRescheduleTime').selectOption('14:00');
  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

  await expect(page.locator('#homeBookingAvailabilityMessage')).toHaveText('Ese horario ya no está disponible.');
  await expect(page.getByRole('button', { name: 'Confirmar cambio' })).toBeHidden();
  expect(patchPayload).toBeNull();

  await page.locator('#homeBookingRescheduleTime').selectOption('14:30');
  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

  await expect(page.locator('#homeBookingAvailabilityMessage')).toHaveText('Horario disponible ✓');
  await expect(page.getByRole('button', { name: 'Confirmar cambio' })).toBeVisible();
  expect(availabilityCalls).toHaveLength(2);
  expect(availabilityCalls[1].serviceId).toBe('svc1');
  expect(availabilityCalls[1].excludeBookingId).toBe('b1');

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Ana Reserva');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Confirmar cambio' }).click();

  await expect.poll(() => patchPayload).not.toBeNull();
  expect(patchPayload.startAt).toBe(availabilityCalls[1].startAt);
  expect(patchPayload.notes).toBeNull();
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Confirmada');
  await expect(page.getByRole('button', { name: 'Reprogramar' })).toBeVisible();
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
});


test('manual booking creation checks availability and adds the reservation', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let availabilityCall = null;
  let createPayload = null;

  await page.route('**/api/v1/bookings/availability?**', async route => {
    const url = new URL(route.request().url());
    availabilityCall = {
      serviceId: url.searchParams.get('serviceId'),
      startAt: url.searchParams.get('startAt'),
      excludeBookingId: url.searchParams.get('excludeBookingId')
    };
    await route.fulfill(json({
      serviceId: availabilityCall.serviceId,
      startAt: availabilityCall.startAt,
      endAt: new Date(new Date(availabilityCall.startAt).getTime() + 30 * 60 * 1000).toISOString(),
      available: true
    }));
  });

  await page.route('**/api/v1/bookings', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    createPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'b3',
        customerId: createPayload.customerId,
        serviceId: createPayload.serviceId,
        startAt: createPayload.startAt,
        endAt: new Date(new Date(createPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
        status: 'CONFIRMED',
        source: 'ADMIN',
        notes: createPayload.notes,
        createdAt: '2026-09-18T18:00:00Z',
        updatedAt: '2026-09-18T18:00:00Z'
      })
    });
  });

  await page.goto('/');
  await page.getByRole('button', { name: '＋ Nueva reserva' }).click();

  await expect(page.locator('#homeBookingCreatePanel')).toBeVisible();
  await expect(page.locator('#homeBookingCreateCustomer')).toContainText('Ana Reserva');
  await expect(page.locator('#homeBookingCreateService')).toContainText('Peluquería');

  await page.locator('#homeBookingCreateCustomer').selectOption('cust1');
  await page.locator('#homeBookingCreateService').selectOption('svc1');
  await page.locator('#homeBookingCreateDate').selectOption({ index: 1 });
  await page.locator('#homeBookingCreateTime').selectOption('10:00');

  await expect(page.getByRole('button', { name: 'Comprobar disponibilidad' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Crear reserva' })).toBeHidden();

  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

  await expect(page.locator('#homeBookingCreateMessage')).toHaveText('Horario disponible ✓');
  await expect(page.getByRole('button', { name: 'Crear reserva' })).toBeVisible();
  expect(availabilityCall.serviceId).toBe('svc1');
  expect(availabilityCall.excludeBookingId).toBeNull();

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Ana Reserva');
    expect(dialog.message()).toContain('Peluquería');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Crear reserva' }).click();

  await expect.poll(() => createPayload).not.toBeNull();
  expect(createPayload.customerId).toBe('cust1');
  expect(createPayload.serviceId).toBe('svc1');
  expect(createPayload.startAt).toBe(availabilityCall.startAt);
  expect(createPayload.source).toBe('ADMIN');
  expect(createPayload.notes).toBeNull();

  await expect(page.locator('#homeBusinessBookingsCount')).toHaveText('3');
  await expect(page.locator('#homeBookingsList')).toContainText('Manual');
  await expect(page.locator('#homeBookingCreateMessage')).toHaveText('Reserva creada para Ana Reserva ✓');
});
