const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockReadyHome(page, roles = ['BUSINESS_ADMIN'], options = {}) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({ email: 'admin@demo.cl', roles })));
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
  await page.route('**/api/v1/customers/cust1/profile', route => route.fulfill(json({
    customer: {
      id: 'cust1', name: 'Ana Reserva', phone: '+56922222222', email: 'ana@example.cl',
      notes: 'Prefiere horario de tarde',
      createdAt: '2026-09-17T10:00:00Z', updatedAt: '2026-09-18T10:00:00Z'
    },
    bookings: [
      {
        id: 'b1', customerId: 'cust1', serviceId: 'svc1',
        startAt: '2026-09-18T15:00:00Z', endAt: '2026-09-18T15:30:00Z',
        status: 'CONFIRMED', source: 'AI_CALL'
      },
      {
        id: 'b-old', customerId: 'cust1', serviceId: 'svc2',
        startAt: '2026-09-10T16:00:00Z', endAt: '2026-09-10T17:00:00Z',
        status: 'CANCELLED', source: 'ADMIN'
      }
    ]
  })));
  await page.route('**/api/v1/audit', route => route.fulfill(json([
    {
      id: 'audit-10', action: 'BOOKING_RESCHEDULE', resourceType: 'BOOKING', resourceId: 'b1',
      result: 'SUCCESS', actorType: 'HUMAN', actorUserId: 'user-1',
      actorName: 'Carolina Soto', actorEmail: 'carolina@example.com', actorRole: 'OPERATOR',
      beforeState: { startAt: '2026-09-18T14:00:00Z', status: 'CONFIRMED' },
      afterState: { startAt: '2026-09-18T15:00:00Z', status: 'CONFIRMED' },
      createdAt: '2026-09-18T18:05:00Z'
    },
    {
      id: 'audit-11', action: 'BOOKING_CANCEL', resourceType: 'BOOKING', resourceId: 'b2',
      result: 'SUCCESS', actorType: 'HUMAN', actorUserId: 'user-2',
      actorName: 'Diego Ruiz', actorEmail: 'diego@example.com', actorRole: 'BUSINESS_ADMIN',
      beforeState: { status: 'CONFIRMED' }, afterState: { status: 'CANCELLED' },
      createdAt: '2026-09-18T19:10:00Z'
    }
  ])));

  await page.route('**/api/v1/customers/export?format=csv', route => route.fulfill({
    status: 200,
    contentType: 'text/csv;charset=UTF-8',
    headers: { 'Content-Disposition': 'attachment; filename="helvoca-clientes-e2e.csv"' },
    body: '\uFEFFID,Nombre\r\n"1","Ana Reserva"\r\n'
  }));
  await page.route('**/api/v1/customers/export?format=xlsx', route => route.fulfill({
    status: 200,
    contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    headers: { 'Content-Disposition': 'attachment; filename="helvoca-clientes-e2e.xlsx"' },
    body: Buffer.from([0x50, 0x4b, 0x03, 0x04, 0x45, 0x32, 0x45])
  }));

  await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([
    { id: 'o1', operationId: 'op1', sourceReferenceId: 'wa-order', status: 'CONFIRMED', fulfillmentType: 'DELIVERY', contactName: 'Juan Pedido', contactPhone: '+56933333333', deliveryAddress: 'Av. Demo 123, Santiago', subtotal: 15990, deliveryFee: 3000, total: 18990, currency: 'CLP', source: 'WHATSAPP', createdAt: '2026-09-17T17:30:00Z', lines: [{ name: 'Producto demo', quantity: 1, unitPrice: 15990, lineTotal: 15990 }] }
  ])));
  await page.route('**/api/v1/bookings/b1/context', async route => {
    if (options.bookingContextGate) await options.bookingContextGate;
    return route.fulfill(json({
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
    }));
  });
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
  await page.route('**/api/v1/bookings/b1/activity', route => route.fulfill(json([
    {
      id: 'audit-1', action: 'BOOKING_CREATE', actorType: 'HUMAN',
      actorUserId: 'user-1', actorName: 'Carolina Soto', actorRole: 'OPERATOR',
      beforeState: null,
      afterState: { startAt: '2026-09-18T15:00:00Z', status: 'CONFIRMED' },
      createdAt: '2026-09-17T18:00:12Z'
    },
    {
      id: 'audit-2', action: 'BOOKING_RESCHEDULE', actorType: 'HUMAN',
      actorUserId: 'user-1', actorName: 'Carolina Soto', actorRole: 'OPERATOR',
      beforeState: { startAt: '2026-09-18T14:00:00Z', status: 'CONFIRMED' },
      afterState: { startAt: '2026-09-18T15:00:00Z', status: 'CONFIRMED' },
      createdAt: '2026-09-17T18:05:00Z'
    }
  ])));
  await page.route('**/api/v1/bookings/b2/activity', route => route.fulfill(json([
    {
      id: 'audit-3', action: 'BOOKING_CANCEL', actorType: 'HUMAN',
      actorUserId: 'user-2', actorName: 'Diego Ruiz', actorRole: 'BUSINESS_ADMIN',
      beforeState: { status: 'CONFIRMED' },
      afterState: { status: 'CANCELLED' },
      createdAt: '2026-09-17T19:10:00Z'
    }
  ])));
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
  let retryRequest = null;
  let unsafeOutboundCalls = 0;
  const campaignHistory = [{
    id: 'campaign-old',
    reason: 'Cierre temprano',
    status: 'ACTIVATED',
    goal: 'INFORM',
    strategy: 'CHEAPEST',
    recipientCount: 1,
    createdAt: '2026-09-17T12:00:00Z',
    activationReady: false,
    activationBlockers: [{ code: 'CAMPAIGN_NOT_PREPARED', message: 'La campaña ya fue activada.' }],
    recipients: [{
      recipientId: 'recipient-old',
      customerId: 'cust-old',
      customerName: 'Nicolás Vega',
      channel: 'WHATSAPP',
      status: 'FAILED',
      statusAt: '2026-09-17T12:05:00Z',
      retryable: true,
      retryCount: 0
    }]
  }];
  page.on('request', request => {
    if (/\/api\/v1\/outbound-messages\/.*\/(queue|dispatch)$/.test(request.url())) unsafeOutboundCalls += 1;
  });
  await page.route('**/api/v1/booking-incident-campaigns/*/activation-readiness', async route => {
    const parts = new URL(route.request().url()).pathname.split('/');
    const campaignId = parts[parts.length - 2];
    const target = campaignHistory.find(item => item.id === campaignId);
    await route.fulfill(json({
      ready: target?.activationReady === true,
      channel: 'WHATSAPP',
      blockers: target?.activationBlockers || []
    }));
  });

  await page.route('**/api/v1/booking-incident-campaigns/*/recipients/*/retry', async route => {
    expect(route.request().method()).toBe('POST');
    retryRequest = route.request().postDataJSON();
    const target = campaignHistory.find(item => item.id === 'campaign-old');
    const recipient = target?.recipients?.find(item => item.recipientId === 'recipient-old');
    if (recipient) {
      recipient.status = 'QUEUED';
      recipient.statusAt = '2026-09-18T12:10:00Z';
      recipient.retryable = false;
      recipient.retryCount = 1;
    }
    await route.fulfill(json({
      campaignId: 'campaign-old',
      recipientId: 'recipient-old',
      status: 'QUEUED'
    }));
  });

  await page.route('**/api/v1/booking-incident-campaigns/*/activate', async route => {
    expect(route.request().method()).toBe('POST');
    activationRequest = route.request().postDataJSON();
    const target = campaignHistory.find(item => item.id === 'campaign-1');
    if (target) {
      target.status = 'ACTIVATED';
      target.activationReady = false;
      target.activationBlockers = [{ code: 'CAMPAIGN_NOT_PREPARED', message: 'La campaña ya no está en estado preparado.' }];
      (target.recipients || []).forEach(recipient => {
        recipient.status = 'QUEUED';
        recipient.statusAt = '2026-09-18T12:01:00Z';
        recipient.retryable = false;
      });
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
      activationBlockers: [],
      recipients: preparedCampaignRequest.recipients.map((item, index) => ({
        recipientId: 'recipient-' + (index + 1),
        customerId: item.customerId,
        customerName: index === 0 ? 'Ana Reserva' : 'Cliente',
        channel: 'WHATSAPP',
        status: 'PENDING',
        statusAt: '2026-09-18T12:00:00Z',
        retryable: false,
        retryCount: 0
      }))
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
  await expect(page.locator('#homeCallsMetric')).toHaveAttribute('href', '/operations.html');
  await expect(page.locator('#homeBookingsMetric')).toHaveAttribute('href', '/?tab=bookings#homeBusinessWorkspace');
  await expect(page.locator('#homeRequestsMetric')).toHaveAttribute('href', '/?tab=requests#homeBusinessWorkspace');
  await expect(page.locator('#homeBusinessWorkspace')).toBeVisible();

  await page.locator('#homeIncidentToggle').click();
  await expect(page.locator('#homeIncidentPanel')).toBeVisible();
  await expect(page.locator('#homeIncidentHistoryBody')).toBeHidden();
  await expect(page.locator('#homeIncidentHistoryCount')).toHaveText('1');
  await expect(page.locator('#homeIncidentPanel')).not.toContainText('PREPARED');
  await page.locator('#homeIncidentHistoryToggle').click();
  await expect(page.locator('#homeIncidentHistoryBody')).toBeVisible();
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Cierre temprano');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Con errores');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Nicolás Vega');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Error');
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeEnabled();

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Reintentar este aviso');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Reintentar' }).click();
  await expect(page.locator('[data-incident-recipient="recipient-old"]')).toContainText('Enviando');
  expect(retryRequest).toEqual({ confirmed: true });

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
  await expect(page.locator('#homeIncidentPreviewBtn')).toHaveAttribute(
    'title',
    'Completa motivo, fecha y un rango Desde/Hasta válido.'
  );

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
  await expect(page.locator('#homeIncidentImpactSummary')).toContainText('1 cliente afectado');
  await expect(page.locator('#homeIncidentImpactSummary')).not.toContainText('reserva');
  await expect(page.locator('#homeIncidentPreviewBtn')).toBeEnabled();
  await page.locator('#homeIncidentPreviewBtn').click();
  await expect(page.locator('#homeIncidentPreview')).toContainText('1 cliente afectado');
  await expect(page.locator('#homeIncidentPreview')).toContainText('Ana Reserva');
  await expect(page.locator('#homeIncidentPreview')).toContainText('WhatsApp primero');
  await expect(page.locator('#homeIncidentPreview')).not.toContainText('Bruno Masaje');
  await expect(page.getByText('Ver mensaje')).toBeVisible();
  await expect(page.locator('.home-incident-message')).toBeHidden();
  await page.getByText('Ver mensaje').click();
  await expect(page.locator('.home-incident-message')).toBeVisible();
  await expect(page.locator('.home-incident-message')).toContainText('Cierre anticipado');
  await expect(page.locator('.home-incident-select')).toHaveCount(1);
  await page.locator('.home-incident-select').uncheck();
  await expect(page.locator('#homeIncidentPrepareCampaign')).toBeDisabled();
  await expect(page.locator('#homeIncidentPrepareCampaign')).toHaveAttribute(
    'title',
    'Selecciona al menos un cliente para preparar la campaña.'
  );
  await page.locator('.home-incident-select').check();
  await expect(page.locator('#homeIncidentPrepareCampaign')).toBeEnabled();
  await expect(page.locator('#homeIncidentPrepareCampaign')).toHaveText('Continuar');

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Preparar avisos para 1 cliente');
    dialog.accept();
  });
  await page.locator('#homeIncidentPrepareCampaign').click();
  await expect(page.locator('#homeIncidentPreview')).toContainText('Listo para enviar');
  await expect(page.getByRole('button', { name: 'Enviar 1 aviso' })).toBeEnabled();
  await expect(page.locator('#homeIncidentPanel')).not.toContainText('PREPARED');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Cierre anticipado');

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Enviar avisos a 1 cliente');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Enviar 1 aviso' }).click();
  await expect(page.locator('#homeIncidentPreview')).toContainText('Envío iniciado');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('En proceso');
  await expect(page.locator('[data-incident-recipient="recipient-1"]')).toContainText('Enviando');

  const activatedCampaign = campaignHistory.find(item => item.id === 'campaign-1');
  activatedCampaign.recipients[0].status = 'DELIVERED';
  activatedCampaign.recipients[0].statusAt = '2026-09-18T12:04:00Z';
  await page.locator('#homeIncidentHistoryToggle').click();
  await page.locator('#homeIncidentHistoryToggle').click();
  await expect(page.locator('[data-incident-recipient="recipient-1"]')).toContainText('Entregado');
  await expect(page.locator('#homeIncidentHistoryList')).toContainText('Completado');
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

  const firstBooking = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]');
  await firstBooking.focus();
  await firstBooking.click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailClose')).toBeFocused();
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Ana llamó para reservar peluquería');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Quiero reservar peluquería.');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').first()).toHaveText('Ana Reserva');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').nth(1)).toHaveText('Negocio E2E');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts > * > span')).toHaveText(['Servicio', 'Teléfono', 'Llamada', 'Reserva', 'Origen', 'Estado']);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Reservada para');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts')).not.toContainText('Inicio');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts')).not.toContainText('Fin');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Qué hizo Helvoca');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Historial');
  await page.locator('#homeBookingDetailClose').click();

  await page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b2"]').click();
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Conversación de WhatsApp');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Quiero reservar un masaje.');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Tu reserva quedó confirmada.');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').first()).toHaveText('Bruno Masaje');
  await expect(page.locator('#homeBookingDetailBody .home-detail-message strong').nth(1)).toHaveText('Negocio E2E');
  await expect(page.locator('#homeBookingDetailBody .home-detail-facts > * > span')).toHaveText(['Servicio', 'Teléfono', 'WhatsApp', 'Reserva', 'Origen', 'Estado']);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Reservada para');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Qué hizo Helvoca');
  await expect(page.locator('#homeBookingDetailBody')).not.toContainText('Historial');
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
  await expect(page.getByRole('button', { name: 'Empezar preparación' })).toBeVisible();
  await page.locator('#homeBookingDetailClose').click();

  await page.getByRole('tab', { name: /Solicitudes/ }).click();
  await expect(page.locator('#homeRequestsList')).toContainText('No hay solicitudes recientes.');

  await page.getByRole('tab', { name: /Clientes/ }).click();
  await expect(page.locator('#homeCustomersList')).toContainText('Ana Reserva');
  await expect(page.locator('#advancedPanel')).toBeHidden();
});


test('reservation filters drawer and embedded conversation work', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  let releaseBookingContext;
  const bookingContextGate = new Promise(resolve => { releaseBookingContext = resolve; });
  await mockReadyHome(page, ['BUSINESS_ADMIN'], { bookingContextGate });
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

  const firstBooking = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]');
  await firstBooking.focus();
  await firstBooking.click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailClose')).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  releaseBookingContext();
  await expect.poll(() => page.locator('#homeBookingDetailDrawer').evaluate(
    drawer => drawer.contains(document.activeElement)
  )).toBe(true);
  await page.locator('#homeBookingDetailClose').focus();
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Reservada para');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Actividad');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Reserva creada');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Reserva reprogramada');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Carolina Soto');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Operador');
  await page.getByRole('button', { name: 'Reprogramar' }).focus();
  await page.locator('#homeBookingDetailBody').evaluate(body => {
    body.innerHTML = '<button id="replacementDrawerAction" type="button">Acción reemplazada</button>';
  });
  await page.keyboard.press('Tab');
  await expect.poll(() => page.locator('#homeBookingDetailDrawer').evaluate(
    drawer => drawer.contains(document.activeElement)
  )).toBe(true);
  await page.locator('#homeBookingDetailClose').focus();
  await page.keyboard.press('Escape');
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
  await expect(firstBooking).toBeFocused();

  const secondBooking = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b2"]');
  await secondBooking.focus();
  await secondBooking.click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailClose')).toBeFocused();
  await page.locator('#homeBookingDetailBackdrop').click({ position: { x: 8, y: 8 } });
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
  await expect(secondBooking).toBeFocused();
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
  const orderOpener = page.locator('#homeOrdersList .home-business-table [data-home-order-id="o1"]');
  await orderOpener.focus();
  await orderOpener.click();
  await page.getByRole('button', { name: 'Empezar preparación' }).click();

  await expect.poll(() => statusPatch).toEqual({ status: 'PREPARING' });
  await expect(page.locator('#homeOrdersList')).toContainText('Preparando');
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
  await expect(page.locator('#homeOrdersList .home-business-table [data-home-order-id="o1"]')).toBeFocused();
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

test('operator cannot see audit or export controls but keeps reservations and customers', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  let auditRequests = 0;
  page.on('request', request => {
    if (request.url().includes('/api/v1/audit')) auditRequests += 1;
  });
  await mockReadyHome(page, ['OPERATOR']);
  await page.goto('/');

  await expect(page.getByRole('tab', { name: /Auditoría/ })).toBeHidden();
  await page.getByRole('tab', { name: /Clientes/ }).click();
  await expect(page.locator('#homeCustomersList')).toContainText('Ana Reserva');
  await expect(page.getByRole('button', { name: 'Descargar CSV' })).toBeHidden();
  await expect(page.getByRole('button', { name: 'Descargar Excel' })).toBeHidden();
  await page.getByRole('tab', { name: /Reservas/ }).click();
  await expect(page.locator('#homeBookingsList')).toContainText('Ana Reserva');
  expect(auditRequests).toBe(0);
});

test('audit workspace shows actor role resource and before after changes', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);
  await page.goto('/');

  await page.getByRole('tab', { name: /Auditoría/ }).click();
  await expect(page.locator('#homeAuditList .home-audit-row')).toHaveCount(2);
  await expect(page.locator('#homeAuditList')).toContainText('Reserva reprogramada');
  await expect(page.locator('#homeAuditList')).toContainText('Carolina Soto');
  await expect(page.locator('#homeAuditList')).toContainText('Operador');
  await expect(page.locator('#homeAuditList')).toContainText('carolina@example.com');
  await expect(page.locator('#homeAuditList')).toContainText('Reserva #b1');
  await expect(page.locator('#homeAuditList')).toContainText('Reserva cancelada');
  await expect(page.locator('#homeAuditList')).toContainText('Diego Ruiz');
  await expect(page.locator('#homeAuditList')).toContainText('Administrador');
  await expect(page.locator('#homeBusinessAuditCount')).toHaveText('2');
});

test('new customer booking lifecycle appears in audit workspace', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  await page.route('**/api/v1/audit', route => route.fulfill(json([
    {
      id: 'audit-carla-4',
      action: 'BOOKING_CANCEL',
      resourceType: 'BOOKING',
      resourceId: 'b3',
      result: 'SUCCESS',
      actorType: 'HUMAN',
      actorUserId: 'secretary-1',
      actorName: 'Secretaria Demo',
      actorEmail: 'secretaria@demo.cl',
      actorRole: 'BUSINESS_ADMIN',
      beforeState: { status: 'CONFIRMED' },
      afterState: { status: 'CANCELLED' },
      createdAt: '2026-09-18T22:43:00Z'
    },
    {
      id: 'audit-carla-3',
      action: 'BOOKING_RESCHEDULE',
      resourceType: 'BOOKING',
      resourceId: 'b3',
      result: 'SUCCESS',
      actorType: 'HUMAN',
      actorUserId: 'secretary-1',
      actorName: 'Secretaria Demo',
      actorEmail: 'secretaria@demo.cl',
      actorRole: 'BUSINESS_ADMIN',
      beforeState: { startAt: '2026-09-19T13:00:00Z', status: 'CONFIRMED' },
      afterState: { startAt: '2026-09-20T17:30:00Z', status: 'CONFIRMED' },
      createdAt: '2026-09-18T22:42:00Z'
    },
    {
      id: 'audit-carla-2',
      action: 'BOOKING_CREATE',
      resourceType: 'BOOKING',
      resourceId: 'b3',
      result: 'SUCCESS',
      actorType: 'HUMAN',
      actorUserId: 'secretary-1',
      actorName: 'Secretaria Demo',
      actorEmail: 'secretaria@demo.cl',
      actorRole: 'BUSINESS_ADMIN',
      beforeState: null,
      afterState: { startAt: '2026-09-19T13:00:00Z', status: 'CONFIRMED' },
      createdAt: '2026-09-18T22:41:00Z'
    },
    {
      id: 'audit-carla-1',
      action: 'CUSTOMER_CREATE',
      resourceType: 'CUSTOMER',
      resourceId: 'cust3',
      result: 'SUCCESS',
      actorType: 'HUMAN',
      actorUserId: 'secretary-1',
      actorName: 'Secretaria Demo',
      actorEmail: 'secretaria@demo.cl',
      actorRole: 'BUSINESS_ADMIN',
      beforeState: null,
      afterState: { name: 'Carla Nueva', phone: '+56977777777', email: 'carla@example.cl' },
      createdAt: '2026-09-18T22:40:00Z'
    }
  ])));

  await page.goto('/');
  await page.getByRole('tab', { name: /Auditoría/ }).click();

  const audit = page.locator('#homeAuditList');
  await expect(audit.locator('.home-audit-row')).toHaveCount(4);
  await expect(page.locator('#homeBusinessAuditCount')).toHaveText('4');

  await expect(audit).toContainText('Cliente creado');
  await expect(audit).toContainText('Reserva creada');
  await expect(audit).toContainText('Reserva reprogramada');
  await expect(audit).toContainText('Reserva cancelada');

  await expect(audit).toContainText('Secretaria Demo');
  await expect(audit).toContainText('secretaria@demo.cl');
  await expect(audit).toContainText('Administrador');
  await expect(audit).toContainText('Cliente #cust3');
  await expect(audit).toContainText('Reserva #b3');
  await expect(audit).toContainText('Confirmada → Cancelada');
});


test('audit filters query by actor action resource and date range', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let filteredParams = null;
  await page.route('**/api/v1/audit?*', route => {
    const url = new URL(route.request().url());
    filteredParams = url.searchParams;
    return route.fulfill(json([
      {
        id: 'audit-10', action: 'BOOKING_RESCHEDULE', resourceType: 'BOOKING', resourceId: 'b1',
        result: 'SUCCESS', actorType: 'HUMAN', actorUserId: 'user-1',
        actorName: 'Carolina Soto', actorEmail: 'carolina@example.com', actorRole: 'OPERATOR',
        beforeState: { startAt: '2026-09-18T14:00:00Z' },
        afterState: { startAt: '2026-09-18T15:00:00Z' },
        createdAt: '2026-09-18T18:05:00Z'
      }
    ]));
  });

  await page.goto('/');
  await page.getByRole('tab', { name: /Auditoría/ }).click();
  await page.getByLabel('Usuario').fill('Carolina');
  await page.getByLabel('Acción').selectOption('BOOKING_RESCHEDULE');
  await page.getByLabel('Recurso').selectOption('BOOKING');
  await page.locator('#homeAuditFrom').fill('2026-09-18');
  await page.locator('#homeAuditTo').fill('2026-09-19');
  await page.getByRole('button', { name: 'Filtrar' }).click();

  await expect(page.locator('#homeAuditList .home-audit-row')).toHaveCount(1);
  await expect(page.locator('#homeAuditList')).toContainText('Carolina Soto');
  await expect(page.locator('#homeAuditFilterMessage')).toContainText('1 evento encontrado');
  expect(filteredParams.get('actor')).toBe('Carolina');
  expect(filteredParams.get('action')).toBe('BOOKING_RESCHEDULE');
  expect(filteredParams.get('resourceType')).toBe('BOOKING');
  expect(filteredParams.get('from')).toBeTruthy();
  expect(filteredParams.get('to')).toBeTruthy();
  expect(new Date(filteredParams.get('to')).getTime()).toBeGreaterThan(new Date(filteredParams.get('from')).getTime());

  await page.getByRole('button', { name: 'Limpiar' }).click();
  await expect(page.locator('#homeAuditList .home-audit-row')).toHaveCount(2);
});

test('audit exports preserve active filters in csv and xlsx', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  const exportRequests = [];
  await page.route('**/api/v1/audit/export?*', route => {
    const url = new URL(route.request().url());
    exportRequests.push(url.searchParams);
    const format = url.searchParams.get('format');
    if (format === 'xlsx') {
      return route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        headers: { 'Content-Disposition': 'attachment; filename="helvoca-auditoria-e2e.xlsx"' },
        body: Buffer.from([0x50, 0x4b, 0x03, 0x04, 0x41, 0x55, 0x44])
      });
    }
    return route.fulfill({
      status: 200,
      contentType: 'text/csv;charset=UTF-8',
      headers: { 'Content-Disposition': 'attachment; filename="helvoca-auditoria-e2e.csv"' },
      body: '\uFEFFFecha,Accion,Actor\r\n"2026-09-18","BOOKING_RESCHEDULE","Carolina Soto"\r\n'
    });
  });

  await page.goto('/');
  await page.getByRole('tab', { name: /Auditoría/ }).click();
  await page.getByLabel('Usuario').fill('Carolina');
  await page.getByLabel('Acción').selectOption('BOOKING_RESCHEDULE');
  await page.getByLabel('Recurso').selectOption('BOOKING');
  await page.locator('#homeAuditFrom').fill('2026-09-18');
  await page.locator('#homeAuditTo').fill('2026-09-19');

  const csvPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar CSV' }).click();
  const csv = await csvPromise;
  expect(csv.suggestedFilename()).toBe('helvoca-auditoria-e2e.csv');

  const xlsxPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar Excel' }).click();
  const xlsx = await xlsxPromise;
  expect(xlsx.suggestedFilename()).toBe('helvoca-auditoria-e2e.xlsx');

  expect(exportRequests).toHaveLength(2);
  for (const params of exportRequests) {
    expect(params.get('actor')).toBe('Carolina');
    expect(params.get('action')).toBe('BOOKING_RESCHEDULE');
    expect(params.get('resourceType')).toBe('BOOKING');
    expect(params.get('from')).toBeTruthy();
    expect(params.get('to')).toBeTruthy();
  }
  expect(exportRequests[0].get('format')).toBe('csv');
  expect(exportRequests[1].get('format')).toBe('xlsx');
  await expect(page.locator('#homeAuditFilterMessage')).toContainText('XLSX de auditoría descargado');
});

test('customer exports download csv and xlsx', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);
  await page.goto('/');
  await page.getByRole('tab', { name: /Clientes/ }).click();

  const csvPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar CSV' }).click();
  const csv = await csvPromise;
  expect(csv.suggestedFilename()).toBe('helvoca-clientes-e2e.csv');
  await expect(page.locator('#homeCustomersExportMessage')).toContainText('CSV descargado');

  const xlsxPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  const xlsx = await xlsxPromise;
  expect(xlsx.suggestedFilename()).toBe('helvoca-clientes-e2e.xlsx');
  await expect(page.locator('#homeCustomersExportMessage')).toContainText('XLSX descargado');
});

test('newly created customer is included in csv and xlsx exports', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let customerPayload = null;
  await page.route('**/api/v1/customers', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    customerPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'cust3',
        name: customerPayload.name,
        phone: customerPayload.phone,
        email: customerPayload.email,
        notes: customerPayload.notes,
        createdAt: '2026-09-18T22:45:00Z',
        updatedAt: '2026-09-18T22:45:00Z'
      })
    });
  });

  await page.route('**/api/v1/customers/export?format=csv', route => route.fulfill({
    status: 200,
    contentType: 'text/csv;charset=UTF-8',
    headers: { 'Content-Disposition': 'attachment; filename="helvoca-clientes-carla.csv"' },
    body: '\uFEFFID,Nombre,Telefono,Email\r\n"cust1","Ana Reserva","+56922222222","ana@example.cl"\r\n"cust2","Bruno Masaje","+56955555555","bruno@example.cl"\r\n"cust3","Carla Nueva","+56977777777","carla@example.cl"\r\n'
  }));

  await page.route('**/api/v1/customers/export?format=xlsx', route => route.fulfill({
    status: 200,
    contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    headers: { 'Content-Disposition': 'attachment; filename="helvoca-clientes-carla.xlsx"' },
    body: Buffer.from('PK Helvoca Excel Carla Nueva +56977777777 carla@example.cl', 'utf8')
  }));

  await page.goto('/');
  await page.getByRole('tab', { name: /Clientes/ }).click();
  await page.getByRole('button', { name: 'Nuevo cliente' }).click();
  await page.locator('#homeCustomerCreateName').fill('Carla Nueva');
  await page.locator('#homeCustomerCreatePhone').fill('+56977777777');
  await page.locator('#homeCustomerCreateEmail').fill('carla@example.cl');
  await page.getByRole('button', { name: 'Crear cliente' }).click();

  await expect.poll(() => customerPayload).not.toBeNull();
  await expect(page.locator('#homeCustomersList')).toContainText('Carla Nueva');
  await expect(page.locator('#homeBusinessCustomersCount')).toHaveText('3');

  const csvPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar CSV' }).click();
  const csv = await csvPromise;
  expect(csv.suggestedFilename()).toBe('helvoca-clientes-carla.csv');
  const csvStream = await csv.createReadStream();
  let csvBody = '';
  for await (const chunk of csvStream) csvBody += chunk.toString('utf8');
  expect(csvBody).toContain('Carla Nueva');
  expect(csvBody).toContain('+56977777777');
  expect(csvBody).toContain('carla@example.cl');
  await expect(page.locator('#homeCustomersExportMessage')).toContainText('CSV descargado · 3 clientes');

  const xlsxPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  const xlsx = await xlsxPromise;
  expect(xlsx.suggestedFilename()).toBe('helvoca-clientes-carla.xlsx');
  const xlsxStream = await xlsx.createReadStream();
  const xlsxChunks = [];
  for await (const chunk of xlsxStream) xlsxChunks.push(chunk);
  const xlsxBody = Buffer.concat(xlsxChunks).toString('utf8');
  expect(xlsxBody).toContain('Carla Nueva');
  expect(xlsxBody).toContain('+56977777777');
  expect(xlsxBody).toContain('carla@example.cl');
  await expect(page.locator('#homeCustomersExportMessage')).toContainText('XLSX descargado · 3 clientes');
});


test('manual customer creation adds customer and updates booking selector', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let createPayload = null;
  await page.route('**/api/v1/customers', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    createPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'cust3',
        name: createPayload.name,
        phone: createPayload.phone,
        email: createPayload.email,
        notes: createPayload.notes,
        createdAt: '2026-09-18T21:55:00Z',
        updatedAt: '2026-09-18T21:55:00Z'
      })
    });
  });

  await page.goto('/');
  await page.getByRole('tab', { name: /Clientes/ }).click();
  await page.getByRole('button', { name: 'Nuevo cliente' }).click();

  await page.locator('#homeCustomerCreateName').fill('Carla Nueva');
  await page.locator('#homeCustomerCreatePhone').fill('+56977777777');
  await page.locator('#homeCustomerCreateEmail').fill('carla@example.cl');
  await page.getByRole('button', { name: 'Crear cliente' }).click();

  expect(createPayload).toEqual({
    name: 'Carla Nueva',
    phone: '+56977777777',
    email: 'carla@example.cl',
    notes: null
  });
  await expect(page.locator('#homeCustomerCreateMessage')).toContainText('Cliente creado');
  await expect(page.locator('#homeBusinessCustomersCount')).toHaveText('3');
  await expect(page.locator('#homeCustomersList')).toContainText('Carla Nueva');
  await expect(page.locator('#homeCustomersList')).toContainText('+56977777777');

  await page.getByRole('tab', { name: /Reservas/ }).click();
  await page.getByRole('button', { name: 'Nueva reserva' }).click();
  await expect(page.locator('#homeBookingCreateCustomer option[value="cust3"]')).toHaveText('Carla Nueva');
});

test('newly created customer can receive a manual booking', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let customerPayload = null;
  let availabilityCall = null;
  let bookingPayload = null;

  await page.route('**/api/v1/customers', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    customerPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'cust3',
        name: customerPayload.name,
        phone: customerPayload.phone,
        email: customerPayload.email,
        notes: customerPayload.notes,
        createdAt: '2026-09-18T22:05:00Z',
        updatedAt: '2026-09-18T22:05:00Z'
      })
    });
  });

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
    bookingPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'b3',
        customerId: bookingPayload.customerId,
        serviceId: bookingPayload.serviceId,
        startAt: bookingPayload.startAt,
        endAt: new Date(new Date(bookingPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
        status: 'CONFIRMED',
        source: 'ADMIN',
        notes: bookingPayload.notes,
        createdAt: '2026-09-18T22:06:00Z',
        updatedAt: '2026-09-18T22:06:00Z'
      })
    });
  });

  await page.goto('/');
  await page.getByRole('tab', { name: /Clientes/ }).click();
  await page.getByRole('button', { name: 'Nuevo cliente' }).click();
  await page.locator('#homeCustomerCreateName').fill('Carla Nueva');
  await page.locator('#homeCustomerCreatePhone').fill('+56977777777');
  await page.locator('#homeCustomerCreateEmail').fill('carla@example.cl');
  await page.getByRole('button', { name: 'Crear cliente' }).click();

  await expect(page.locator('#homeCustomersList')).toContainText('Carla Nueva');

  await page.getByRole('tab', { name: /Reservas/ }).click();
  await page.getByRole('button', { name: 'Nueva reserva' }).click();
  await page.locator('#homeBookingCreateCustomer').selectOption('cust3');
  await page.locator('#homeBookingCreateService').selectOption('svc1');
  await page.locator('#homeBookingCreateDate').selectOption({ index: 1 });
  await page.locator('#homeBookingCreateTime').selectOption('10:00');
  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

  await expect(page.locator('#homeBookingCreateMessage')).toHaveText('Horario disponible ✓');
  await expect(page.getByRole('button', { name: 'Crear reserva' })).toBeVisible();

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Carla Nueva');
    expect(dialog.message()).toContain('Peluquería');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Crear reserva' }).click();

  expect(customerPayload.name).toBe('Carla Nueva');
  expect(availabilityCall.serviceId).toBe('svc1');
  expect(availabilityCall.excludeBookingId).toBeNull();
  await expect.poll(() => bookingPayload).not.toBeNull();
  expect(bookingPayload.customerId).toBe('cust3');
  expect(bookingPayload.serviceId).toBe('svc1');
  expect(bookingPayload.startAt).toBe(availabilityCall.startAt);
  expect(bookingPayload.source).toBe('ADMIN');
  expect(bookingPayload.notes).toBeNull();

  await expect(page.locator('#homeBusinessBookingsCount')).toHaveText('3');
  await expect(page.locator('#homeBookingsList')).toContainText('Carla Nueva');
  await expect(page.locator('#homeBookingsList')).toContainText('Peluquería');
  await expect(page.locator('#homeBookingsList')).toContainText('Manual');
  await expect(page.locator('#homeBookingCreateMessage')).toHaveText('Reserva creada para Carla Nueva ✓');
});

test('newly created booking can be rescheduled from the agenda', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let customerPayload = null;
  const availabilityCalls = [];
  let bookingPayload = null;
  let patchPayload = null;

  await page.route('**/api/v1/customers', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    customerPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'cust3',
        name: customerPayload.name,
        phone: customerPayload.phone,
        email: customerPayload.email,
        notes: customerPayload.notes,
        createdAt: '2026-09-18T22:20:00Z',
        updatedAt: '2026-09-18T22:20:00Z'
      })
    });
  });

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
      available: true
    }));
  });

  await page.route('**/api/v1/bookings', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    bookingPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'b3',
        customerId: bookingPayload.customerId,
        serviceId: bookingPayload.serviceId,
        startAt: bookingPayload.startAt,
        endAt: new Date(new Date(bookingPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
        status: 'CONFIRMED',
        source: 'ADMIN',
        notes: bookingPayload.notes,
        createdAt: '2026-09-18T22:21:00Z',
        updatedAt: '2026-09-18T22:21:00Z'
      })
    });
  });

  await page.route('**/api/v1/bookings/b3/context', route => route.fulfill(json({
    channel: 'MANUAL',
    sourceReferenceId: null,
    call: null,
    whatsapp: null,
    events: []
  })));
  await page.route('**/api/v1/bookings/b3/activity', route => route.fulfill(json([])));

  await page.route('**/api/v1/bookings/b3', async route => {
    expect(route.request().method()).toBe('PATCH');
    patchPayload = route.request().postDataJSON();
    await route.fulfill(json({
      id: 'b3',
      customerId: 'cust3',
      serviceId: 'svc1',
      startAt: patchPayload.startAt,
      endAt: new Date(new Date(patchPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
      status: 'CONFIRMED',
      source: 'ADMIN',
      notes: patchPayload.notes,
      updatedAt: '2026-09-18T22:22:00Z'
    }));
  });

  await page.goto('/');
  await page.getByRole('tab', { name: /Clientes/ }).click();
  await page.getByRole('button', { name: 'Nuevo cliente' }).click();
  await page.locator('#homeCustomerCreateName').fill('Carla Nueva');
  await page.locator('#homeCustomerCreatePhone').fill('+56977777777');
  await page.locator('#homeCustomerCreateEmail').fill('carla@example.cl');
  await page.getByRole('button', { name: 'Crear cliente' }).click();

  await page.getByRole('tab', { name: /Reservas/ }).click();
  await page.getByRole('button', { name: 'Nueva reserva' }).click();
  await page.locator('#homeBookingCreateCustomer').selectOption('cust3');
  await page.locator('#homeBookingCreateService').selectOption('svc1');
  await page.locator('#homeBookingCreateDate').selectOption({ index: 1 });
  await page.locator('#homeBookingCreateTime').selectOption('10:00');
  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Crear reserva' }).click();
  await expect.poll(() => bookingPayload).not.toBeNull();

  const row = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b3"]');
  await expect(row).toContainText('Carla Nueva');
  const beforeRowText = await row.innerText();
  await row.click();

  await expect(page.locator('#homeBookingDetailTitle')).toHaveText('Carla Nueva');
  const beforeMeta = await page.locator('#homeBookingDetailMeta').innerText();

  await page.getByRole('button', { name: 'Reprogramar' }).click();
  await page.locator('#homeBookingRescheduleDate').selectOption({ index: 2 });
  await page.locator('#homeBookingRescheduleTime').selectOption('14:30');
  await page.locator('#homeBookingCheckAvailability').click();

  await expect(page.locator('#homeBookingAvailabilityMessage')).toHaveText('Horario disponible ✓');
  await expect(page.getByRole('button', { name: 'Confirmar cambio' })).toBeVisible();

  const rescheduleAvailability = availabilityCalls.at(-1);
  expect(rescheduleAvailability.serviceId).toBe('svc1');
  expect(rescheduleAvailability.excludeBookingId).toBe('b3');
  expect(rescheduleAvailability.startAt).not.toBe(bookingPayload.startAt);

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Carla Nueva');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Confirmar cambio' }).click();

  await expect.poll(() => patchPayload).not.toBeNull();
  expect(patchPayload.startAt).toBe(rescheduleAvailability.startAt);
  expect(patchPayload.notes).toBeNull();

  await expect(page.locator('#homeBookingDetailMeta')).not.toHaveText(beforeMeta);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Confirmada');
  await expect(row).not.toHaveText(beforeRowText);
  await expect(row).toContainText('Carla Nueva');
});

test('newly created booking can be cancelled from the agenda', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let customerPayload = null;
  let bookingPayload = null;
  let cancelCalls = 0;

  await page.route('**/api/v1/customers', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    customerPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'cust3',
        name: customerPayload.name,
        phone: customerPayload.phone,
        email: customerPayload.email,
        notes: customerPayload.notes,
        createdAt: '2026-09-18T22:30:00Z',
        updatedAt: '2026-09-18T22:30:00Z'
      })
    });
  });

  await page.route('**/api/v1/bookings/availability?**', async route => {
    const url = new URL(route.request().url());
    const startAt = url.searchParams.get('startAt');
    await route.fulfill(json({
      serviceId: url.searchParams.get('serviceId'),
      startAt,
      endAt: new Date(new Date(startAt).getTime() + 30 * 60 * 1000).toISOString(),
      available: true
    }));
  });

  await page.route('**/api/v1/bookings', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    bookingPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'b3',
        customerId: bookingPayload.customerId,
        serviceId: bookingPayload.serviceId,
        startAt: bookingPayload.startAt,
        endAt: new Date(new Date(bookingPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
        status: 'CONFIRMED',
        source: 'ADMIN',
        notes: bookingPayload.notes,
        createdAt: '2026-09-18T22:31:00Z',
        updatedAt: '2026-09-18T22:31:00Z'
      })
    });
  });

  await page.route('**/api/v1/bookings/b3/context', route => route.fulfill(json({
    channel: 'MANUAL',
    sourceReferenceId: null,
    call: null,
    whatsapp: null,
    events: []
  })));
  await page.route('**/api/v1/bookings/b3/activity', route => route.fulfill(json([])));

  await page.route('**/api/v1/bookings/b3', async route => {
    expect(route.request().method()).toBe('DELETE');
    cancelCalls += 1;
    await route.fulfill({ status: 204, body: '' });
  });

  await page.goto('/');
  await page.getByRole('tab', { name: /Clientes/ }).click();
  await page.getByRole('button', { name: 'Nuevo cliente' }).click();
  await page.locator('#homeCustomerCreateName').fill('Carla Nueva');
  await page.locator('#homeCustomerCreatePhone').fill('+56977777777');
  await page.locator('#homeCustomerCreateEmail').fill('carla@example.cl');
  await page.getByRole('button', { name: 'Crear cliente' }).click();

  await page.getByRole('tab', { name: /Reservas/ }).click();
  await page.getByRole('button', { name: 'Nueva reserva' }).click();
  await page.locator('#homeBookingCreateCustomer').selectOption('cust3');
  await page.locator('#homeBookingCreateService').selectOption('svc1');
  await page.locator('#homeBookingCreateDate').selectOption({ index: 1 });
  await page.locator('#homeBookingCreateTime').selectOption('10:00');
  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Crear reserva' }).click();

  await expect.poll(() => bookingPayload).not.toBeNull();
  expect(bookingPayload.customerId).toBe('cust3');
  expect(bookingPayload.serviceId).toBe('svc1');

  const row = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b3"]');
  await expect(row).toContainText('Carla Nueva');
  await row.click();

  await expect(page.locator('#homeBookingDetailTitle')).toHaveText('Carla Nueva');
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Confirmada');
  await expect(page.getByRole('button', { name: 'Cancelar reserva' })).toBeVisible();

  page.once('dialog', dialog => {
    expect(dialog.message()).toContain('Carla Nueva');
    dialog.accept();
  });
  await page.getByRole('button', { name: 'Cancelar reserva' }).click();

  await expect.poll(() => cancelCalls).toBe(1);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Cancelada');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Esta reserva está cancelada');
  await expect(page.getByRole('button', { name: 'Cancelar reserva' })).toHaveCount(0);
  await expect(row.locator('.home-pill').first()).toHaveText('Cancelada');
});


test('cancelled new booking appears in the customer history', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let customerPayload = null;
  let bookingPayload = null;
  let cancelCalls = 0;
  let profileCalls = 0;

  await page.route('**/api/v1/customers/cust3/profile', async route => {
    expect(route.request().method()).toBe('GET');
    profileCalls += 1;
    await route.fulfill(json({
      customer: {
        id: 'cust3',
        name: 'Carla Nueva',
        phone: '+56977777777',
        email: 'carla@example.cl',
        notes: null,
        createdAt: '2026-09-18T22:40:00Z',
        updatedAt: '2026-09-18T22:43:00Z'
      },
      bookings: [{
        id: 'b3',
        customerId: 'cust3',
        serviceId: 'svc1',
        startAt: bookingPayload.startAt,
        endAt: new Date(new Date(bookingPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
        status: 'CANCELLED',
        source: 'ADMIN',
        notes: null,
        createdAt: '2026-09-18T22:41:00Z',
        updatedAt: '2026-09-18T22:43:00Z'
      }]
    }));
  });

  await page.route('**/api/v1/customers', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    customerPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'cust3',
        name: customerPayload.name,
        phone: customerPayload.phone,
        email: customerPayload.email,
        notes: customerPayload.notes,
        createdAt: '2026-09-18T22:40:00Z',
        updatedAt: '2026-09-18T22:40:00Z'
      })
    });
  });

  await page.route('**/api/v1/bookings/availability?**', async route => {
    const url = new URL(route.request().url());
    const startAt = url.searchParams.get('startAt');
    await route.fulfill(json({
      serviceId: url.searchParams.get('serviceId'),
      startAt,
      endAt: new Date(new Date(startAt).getTime() + 30 * 60 * 1000).toISOString(),
      available: true
    }));
  });

  await page.route('**/api/v1/bookings', async route => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    bookingPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'b3',
        customerId: bookingPayload.customerId,
        serviceId: bookingPayload.serviceId,
        startAt: bookingPayload.startAt,
        endAt: new Date(new Date(bookingPayload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
        status: 'CONFIRMED',
        source: 'ADMIN',
        notes: bookingPayload.notes,
        createdAt: '2026-09-18T22:41:00Z',
        updatedAt: '2026-09-18T22:41:00Z'
      })
    });
  });

  await page.route('**/api/v1/bookings/b3/context', route => route.fulfill(json({
    channel: 'MANUAL',
    sourceReferenceId: null,
    call: null,
    whatsapp: null,
    events: []
  })));
  await page.route('**/api/v1/bookings/b3/activity', route => route.fulfill(json([])));
  await page.route('**/api/v1/bookings/b3', async route => {
    expect(route.request().method()).toBe('DELETE');
    cancelCalls += 1;
    await route.fulfill({ status: 204, body: '' });
  });

  await page.goto('/');

  await page.getByRole('tab', { name: /Clientes/ }).click();
  await page.getByRole('button', { name: 'Nuevo cliente' }).click();
  await page.locator('#homeCustomerCreateName').fill('Carla Nueva');
  await page.locator('#homeCustomerCreatePhone').fill('+56977777777');
  await page.locator('#homeCustomerCreateEmail').fill('carla@example.cl');
  await page.getByRole('button', { name: 'Crear cliente' }).click();

  await page.getByRole('tab', { name: /Reservas/ }).click();
  await page.getByRole('button', { name: 'Nueva reserva' }).click();
  await page.locator('#homeBookingCreateCustomer').selectOption('cust3');
  await page.locator('#homeBookingCreateService').selectOption('svc1');
  await page.locator('#homeBookingCreateDate').selectOption({ index: 1 });
  await page.locator('#homeBookingCreateTime').selectOption('10:00');
  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Crear reserva' }).click();
  await expect.poll(() => bookingPayload).not.toBeNull();

  const bookingRow = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b3"]');
  await bookingRow.click();

  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Cancelar reserva' }).click();
  await expect.poll(() => cancelCalls).toBe(1);
  await expect(page.locator('#homeBookingDetailMeta')).toContainText('Cancelada');

  await page.locator('#homeBookingDetailClose').click();
  await page.getByRole('tab', { name: /Clientes/ }).click();

  const customerRow = page.locator('#homeCustomersList [data-home-customer-id="cust3"]');
  await expect(customerRow).toContainText('Carla Nueva');
  await customerRow.click();

  await expect.poll(() => profileCalls).toBe(1);
  await expect(page.locator('#homeBookingDetailDrawer .eyebrow')).toHaveText('CLIENTE');
  await expect(page.locator('#homeBookingDetailTitle')).toHaveText('Carla Nueva');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Historial de reservas');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Peluquería');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Cancelada');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Manual');
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
  await page.locator('#homeCustomersList [data-home-customer-id="cust1"]').click();
  await expect(page.locator('#homeBookingDetailDrawer')).toBeVisible();
  await expect(page.locator('#homeBookingDetailDrawer .eyebrow')).toHaveText('CLIENTE');
  await expect(page.locator('#homeBookingDetailTitle')).toHaveText('Ana Reserva');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Prefiere horario de tarde');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Historial de reservas');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Peluquería');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Masaje');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Confirmada');
  await expect(page.locator('#homeBookingDetailBody')).toContainText('Cancelada');
  await page.locator('#homeBookingDetailClose').click();
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
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
  await page.locator('#homeBookingStatus').selectOption('CONFIRMED');
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
  await expect(page.locator('#homeBookingsList')).toContainText('No hay reservas que coincidan con estos filtros.');
  await page.keyboard.press('Escape');
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
  await expect(page.getByRole('tab', { name: /Reservas/ })).toBeFocused();
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
    await new Promise(resolve => setTimeout(resolve, 150));
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
  const bookingOpener = page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]');
  await bookingOpener.focus();
  await bookingOpener.click();

  await page.getByRole('button', { name: 'Reprogramar' }).click();
  await expect(page.locator('#homeBookingReschedulePanel')).toBeVisible();
  await expect(page.locator('#homeBookingRescheduleDate')).toBeVisible();
  await expect(page.locator('#homeBookingRescheduleTime')).toBeVisible();

  await page.locator('#homeBookingRescheduleDate').selectOption({ index: 1 });
  await page.locator('#homeBookingRescheduleTime').selectOption('14:00');
  await page.locator('#homeBookingReschedulePanel').getByRole('button', { name: 'Comprobar disponibilidad' }).click();
  await expect(page.locator('#homeBookingRescheduleDate')).toBeDisabled();
  await expect(page.locator('#homeBookingRescheduleTime')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Reprogramar' })).toBeDisabled();
  await expect(page.locator('#homeBookingReschedulePanel').getByRole('button', { name: 'Comprobar disponibilidad' })).toBeDisabled();
  await expect(page.locator('#homeBookingReschedulePanel').getByRole('button', { name: 'Comprobar disponibilidad' })).toHaveAttribute('title', 'Comprobando disponibilidad…');

  await expect(page.locator('#homeBookingAvailabilityMessage')).toHaveText('Ese horario ya no está disponible.');
  await expect(page.locator('#homeBookingRescheduleDate')).toBeEnabled();
  await expect(page.locator('#homeBookingRescheduleTime')).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Reprogramar' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Confirmar cambio' })).toBeHidden();
  expect(patchPayload).toBeNull();

  await page.locator('#homeBookingRescheduleTime').selectOption('14:30');
  await page.locator('#homeBookingReschedulePanel').getByRole('button', { name: 'Comprobar disponibilidad' }).click();

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
  await page.keyboard.press('Escape');
  await expect(page.locator('#homeBookingDetailBackdrop')).toBeHidden();
  await expect(page.locator('#homeBookingsList .home-business-table [data-home-booking-id="b1"]')).toBeFocused();
});


test('manual booking creation checks availability and adds the reservation', async ({ page }) => {
  test.setTimeout(45000);
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockReadyHome(page);

  let availabilityCall = null;
  let availabilityCalls = 0;
  let createPayload = null;
  let releaseAvailability;
  const availabilityGate = new Promise(resolve => { releaseAvailability = resolve; });

  await page.route('**/api/v1/bookings/availability?**', async route => {
    availabilityCalls += 1;
    const url = new URL(route.request().url());
    availabilityCall = {
      serviceId: url.searchParams.get('serviceId'),
      startAt: url.searchParams.get('startAt'),
      excludeBookingId: url.searchParams.get('excludeBookingId')
    };
    await availabilityGate;
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
  await expect(page.getByRole('button', { name: 'Comprobar disponibilidad' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Comprobar disponibilidad' })).toHaveAttribute(
    'title',
    'Completa cliente, servicio, fecha y hora.'
  );

  await page.locator('#homeBookingCreateCustomer').selectOption('cust1');
  await page.locator('#homeBookingCreateService').selectOption('svc1');
  await page.locator('#homeBookingCreateDate').selectOption({ index: 1 });
  await page.locator('#homeBookingCreateTime').selectOption('10:00');

  await expect(page.getByRole('button', { name: 'Comprobar disponibilidad' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Crear reserva' })).toBeHidden();

  await page.getByRole('button', { name: 'Comprobar disponibilidad' }).click();
  await expect(page.getByRole('button', { name: 'Comprobar disponibilidad' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Comprobar disponibilidad' })).toHaveAttribute('title', 'Comprobando disponibilidad…');
  await expect(page.locator('#homeBookingCreateCustomer')).toBeDisabled();
  await expect(page.locator('#homeBookingCreateService')).toBeDisabled();
  await expect(page.locator('#homeBookingCreateDate')).toBeDisabled();
  await expect(page.locator('#homeBookingCreateTime')).toBeDisabled();
  await expect(page.getByRole('button', { name: '＋ Nueva reserva' })).toBeDisabled();

  releaseAvailability();
  await expect(page.locator('#homeBookingCreateMessage')).toHaveText('Horario disponible ✓');
  await expect(page.getByRole('button', { name: 'Comprobar disponibilidad' })).toBeEnabled();
  await expect(page.locator('#homeBookingCreateCustomer')).toBeEnabled();
  await expect(page.locator('#homeBookingCreateService')).toBeEnabled();
  await expect(page.getByRole('button', { name: '＋ Nueva reserva' })).toBeEnabled();
  expect(availabilityCalls).toBe(1);
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
