const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mockReleaseCandidate(page) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'rc-e2e-token'));

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === '/api/v1/auth/me') return route.fulfill(json({
      email: 'admin@ferreteria-rc.cl',
      roles: ['BUSINESS_ADMIN']
    }));
    if (path === '/api/v1/business') return route.fulfill(json({
      name: 'Ferretería Release Candidate',
      timezone: 'America/Santiago',
      language: 'es',
      humanTransferPhone: '+56999999999'
    }));
    if (path === '/api/v1/business/profile') return route.fulfill(json({
      businessId: 'business-rc',
      presetKey: 'store',
      publicDescription: 'Ferretería y materiales',
      publicPhone: '+56911112222',
      publicEmail: 'ventas@ferreteria-rc.cl',
      websiteUrl: 'https://example.test',
      addressLine: 'Av. Demo 123',
      commune: 'Santiago',
      city: 'Santiago',
      region: 'Metropolitana',
      countryCode: 'CL',
      defaultCurrency: 'CLP',
      sellsProducts: true,
      sellsServices: true,
      usesReservations: true
    }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({
      businessProfileConfigured: true,
      servicesConfigured: true,
      scheduleConfigured: true,
      knowledgeConfigured: true,
      humanTransferConfigured: true,
      phoneConfigured: true,
      readyForCalls: true,
      nextStep: 'READY'
    }));
    if (path === '/api/v1/onboarding/guide') return route.fulfill(json({
      readyForPilot: true,
      completed: 7,
      total: 7,
      progressPercent: 100,
      nextStep: null,
      steps: []
    }));
    if (path === '/api/v1/onboarding/activation') return route.fulfill(json({
      ready: true,
      completed: 10,
      total: 10,
      progressPercent: 100,
      blockers: [],
      steps: []
    }));
    if (path === '/api/v1/services') return route.fulfill(json([
      { id: 'svc-rc', name: 'Corte de madera', durationMinutes: 30, price: 12990, active: true }
    ]));
    if (path === '/api/v1/business/hours') return route.fulfill(json([
      { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
    ]));
    if (path === '/api/v1/business/schedule-exceptions') return route.fulfill(json([]));
    if (path === '/api/v1/knowledge') return route.fulfill(json([]));
    if (path === '/api/v1/ai-agent/voices') return route.fulfill(json([
      { code: 'natural', selection: 'marin', name: 'Natural', description: 'Conversacional' }
    ]));
    if (path === '/api/v1/ai-agent') return route.fulfill(json({
      configured: true,
      name: 'Sofía',
      language: 'es',
      voice: 'marin',
      greeting: 'Hola, ¿en qué te ayudo?',
      instructions: 'Atiende con claridad.',
      active: true,
      capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
    }));
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([
      {
        id: 'phone-rc',
        provider: 'TWILIO',
        externalId: 'PNrc',
        phoneNumber: '+56911111111',
        active: true,
        whatsappEnabled: false
      }
    ]));
    if (path === '/api/v1/phone-numbers/provisioning/status') return route.fulfill(json({
      enabled: false,
      configured: false,
      purchaseAvailable: false,
      provider: 'TWILIO',
      message: 'No disponible en RC'
    }));
    if (path === '/api/v1/payment-provider/managed-sandbox') return route.fulfill(json({
      available: true,
      configured: false,
      enabled: false,
      blockedByCustomConfiguration: false,
      provider: null,
      mode: null,
      webhookPath: null
    }));
    if (path === '/api/v1/billing/status') return route.fulfill(json({
      provider: 'mercadopago',
      billingEnabled: true,
      checkoutConfigured: true,
      currentPlanCode: 'PRO',
      currentPlanName: 'Profesional',
      currentMonthlyPriceClp: 69990,
      subscriptionStatus: 'ACTIVE',
      awaitingProviderVerification: false
    }));
    if (path === '/api/v1/subscription') return route.fulfill(json({
      businessId: 'business-rc',
      plan: 'PRO',
      publicPlanCode: 'PRO',
      planName: 'Profesional',
      status: 'ACTIVE',
      serviceAllowed: true,
      maxConcurrentCalls: 2,
      includedMinutes: 500,
      usedMinutes: 125,
      overageMinutes: 0,
      currentPeriodStart: '2026-09-01T00:00:00Z',
      currentPeriodEnd: '2026-10-01T00:00:00Z',
      billingProviderConnected: true,
      entitlements: [],
      legacyFallback: false
    }));
    if (path === '/api/v1/usage/summary') return route.fulfill(json([
      {
        meterKey: 'VOICE_SECONDS',
        unit: 'SECONDS',
        quantity: 7500,
        estimatedCostUsd: 4.20,
        actualCostUsd: 4.10,
        eventCount: 12
      }
    ]));
    if (path === '/api/v1/public/pricing') return route.fulfill(json([]));
    if (path === '/api/v1/admin/users') return route.fulfill(json([]));
    if (path === '/api/v1/admin/invitations') return route.fulfill(json([]));

    if (path === '/api/v1/bookings') return route.fulfill(json([]));
    if (path === '/api/v1/customers') return route.fulfill(json([]));
    if (path === '/api/v1/commercial/orders') return route.fulfill(json([]));
    if (path === '/api/v1/commercial/pipeline') return route.fulfill(json({
      total: 0,
      active: 0,
      paid: 0,
      needsAction: 0,
      items: []
    }));
    if (path === '/api/v1/operations/dashboard') return route.fulfill(json({
      businessName: 'Ferretería Release Candidate',
      timezone: 'America/Santiago',
      recentRequests: []
    }));
    if (path === '/api/v1/operations/pilot-metrics') return route.fulfill(json({
      conversationsHandled: 12,
      bookingsCreated: 3,
      requiresAttention: 1
    }));
    if (path === '/api/v1/audit') return route.fulfill(json([]));

    if (path === '/api/v1/catalog') return route.fulfill(json([
      {
        id: 'product-rc',
        kind: 'PRODUCT',
        name: 'Taladro percutor',
        description: '750W',
        price: 54990,
        currency: 'CLP',
        active: true
      }
    ]));
    if (path === '/api/v1/inventory') return route.fulfill(json([
      {
        catalogItemId: 'product-rc',
        productName: 'Taladro percutor',
        sku: 'TAL-RC',
        trackingEnabled: true,
        onHand: 8,
        reserved: 1,
        available: 7,
        reorderThreshold: 2,
        lowStock: false
      }
    ]));
    if (path === '/api/v1/inventory/alerts') return route.fulfill(json([]));
    if (path === '/api/v1/inventory/restock-subscriptions') return route.fulfill(json([]));
    if (path === '/api/v1/inventory/restock-subscriptions/notifications') return route.fulfill(json([]));
    if (/^\/api\/v1\/inventory\/[^/]+\/variants$/.test(path)) return route.fulfill(json([]));
    if (/^\/api\/v1\/inventory\/[^/]+\/movements$/.test(path)) return route.fulfill(json([]));

    if (path === '/api/v1/calls') return route.fulfill(json({
      content: [{
        id: 'call-rc',
        callerNumber: '+56955556666',
        direction: 'INBOUND',
        status: 'COMPLETED',
        startedAt: '2026-09-28T18:10:00Z',
        durationSeconds: 95,
        resolution: 'BOOKING_CREATED'
      }],
      number: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1
    }));
    if (path === '/api/v1/calls/call-rc') return route.fulfill(json({
      call: {
        id: 'call-rc',
        callerNumber: '+56955556666',
        direction: 'INBOUND',
        status: 'COMPLETED',
        startedAt: '2026-09-28T18:10:00Z',
        durationSeconds: 95,
        resolution: 'BOOKING_CREATED'
      },
      summary: 'El cliente pidió una reserva y quedó confirmada.',
      transcript: [
        { id: 't-rc', speaker: 'USER', content: 'Necesito una hora mañana.', sequenceNumber: 1, createdAt: '2026-09-28T18:10:05Z' }
      ],
      actions: [
        { id: 'a-rc', actionType: 'CREATE_BOOKING', success: true, detail: 'Reserva para mañana 10:00', createdAt: '2026-09-28T18:10:15Z' }
      ]
    }));
    if (path === '/api/v1/messaging/conversations') return route.fulfill(json([]));

    if (path === '/api/v1/simulator/sessions' && method === 'POST') return route.fulfill(json({
      sessionId: 'rc-session',
      greeting: 'Hola, soy Sofía. ¿En qué te ayudo?'
    }));
    if (path === '/api/v1/calls/rc-session') return route.fulfill(json({
      call: { resolution: 'BOOKING_CREATED' },
      actions: [{ actionType: 'CREATE_BOOKING', success: true, detail: 'Reserva simulada' }]
    }));

    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'RC mock endpoint not defined', path })
    });
  });
}

test('release candidate owner traverses the complete safe business journey in one session', async ({ page }) => {
  await mockReleaseCandidate(page);

  await page.goto('/');
  await expect(page.locator('#firstUserOnboarding')).toBeHidden();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('está atendiendo');

  const homeNav = page.locator('#primaryNav');
  await homeNav.getByRole('link', { name: 'Agenda', exact: true }).click();
  await expect(page.locator('[data-home-tab="bookings"]')).toHaveClass(/active/);
  await homeNav.getByRole('link', { name: 'Clientes', exact: true }).click();
  await expect(page.locator('[data-home-tab="customers"]')).toHaveClass(/active/);

  await page.goto('/inventory.html');
  await expect(page.getByRole('heading', { level: 1, name: 'Inventario', exact: true })).toBeVisible();
  await expect(page.locator('[data-inventory-product-id="product-rc"]')).toContainText('Taladro percutor');

  await page.goto('/conversations.html');
  await expect(page.getByRole('heading', { level: 1, name: 'Conversaciones' })).toBeVisible();
  await expect(page.locator('#conversationList')).toContainText('+56955556666');
  await expect(page.getByText('El cliente pidió una reserva y quedó confirmada.')).toBeVisible();

  await page.goto('/simulator.html');
  await expect(page.getByText(/no crea datos comerciales reales ni realiza llamadas telefónicas/i)).toBeVisible();
  await expect(page.getByText(/WhatsApp real/i)).toBeVisible();
  await page.getByRole('button', { name: 'Nueva prueba' }).click();
  await expect(page.getByText('Hola, soy Sofía. ¿En qué te ayudo?')).toBeVisible();
  await expect(page.locator('#resolution')).toHaveText('Reserva creada');

  await page.goto('/settings.html?section=business');
  await expect(page.locator('.dashboard-heading h1')).toHaveText('Mi negocio');
  await expect(page.locator('.ux-config-nav [data-settings-section]')).toContainText([
    'Negocio', 'Servicios', 'Horarios', 'Recepcionista'
  ]);
  await page.locator('.ux-config-nav [data-settings-section="receptionist"]').click();
  await expect(page.locator('#configAgentPanel')).toBeVisible();

  await page.goto('/account.html');
  await expect(page.getByRole('heading', { level: 1, name: 'Plan y facturación' })).toBeVisible();
  await expect(page.locator('#planName')).toHaveText('Profesional');
  await expect(page.locator('#voiceUsage')).toContainText('125 de 500 min');

  await page.goto('/');
  await page.getByRole('button', { name: 'Salir' }).click();
  await expect(page.locator('#authView')).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeFalsy();
});

test('release candidate principal surfaces remain usable on a 390px customer viewport', async ({ page }) => {
  await mockReleaseCandidate(page);
  await page.setViewportSize({ width: 390, height: 844 });

  for (const path of ['/', '/inventory.html', '/conversations.html', '/simulator.html', '/settings.html', '/account.html']) {
    await page.goto(path);
    await page.waitForTimeout(80);
    const layout = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth
    }));
    expect(layout.scrollWidth, path).toBeLessThanOrEqual(layout.clientWidth + 1);
  }
});


test('release candidate captures exact-head visual evidence for every canonical viewport', async ({ page }) => {
  test.setTimeout(120000);
  await mockReleaseCandidate(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });

  const head = (process.env.GITHUB_SHA || 'local').slice(0, 12);
  const evidenceDir = path.resolve('test-results', 'visual-evidence', head);
  fs.mkdirSync(evidenceDir, { recursive: true });

  const viewports = [
    { width: 1536, height: 950 },
    { width: 1440, height: 900 },
    { width: 1366, height: 768 },
    { width: 1280, height: 720 },
    { width: 768, height: 1024 },
    { width: 390, height: 844 }
  ];
  const surfaces = [
    { name: 'home', route: '/' },
    { name: 'conversations', route: '/conversations.html' },
    { name: 'inventory', route: '/inventory.html' },
    { name: 'settings', route: '/settings.html?section=business' },
    { name: 'billing', route: '/account.html' },
    { name: 'simulator', route: '/simulator.html' }
  ];

  for (const viewport of viewports) {
    await page.setViewportSize(viewport);

    for (const surface of surfaces) {
      await page.goto(surface.route);

      if (surface.name === 'home') {
        await expect(page.locator('#dashboardView')).toBeVisible();
        await expect(page.locator('#firstUserOnboarding')).toBeHidden();
      } else if (surface.name === 'conversations') {
        await expect(page.locator('#conversationList')).toContainText('+56955556666');
      } else if (surface.name === 'inventory') {
        await expect(page.locator('[data-inventory-product-id="product-rc"]')).toContainText('Taladro percutor');
      } else if (surface.name === 'settings') {
        await expect(page.locator('.dashboard-heading h1')).toHaveText('Mi negocio');
      } else if (surface.name === 'billing') {
        await expect(page.locator('#planName')).toHaveText('Profesional');
      } else if (surface.name === 'simulator') {
        await expect(page.locator('#startBtn')).toBeVisible();
      }

      const layout = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth
      }));
      expect(layout.scrollWidth, `${surface.route} at ${viewport.width}px`)
        .toBeLessThanOrEqual(layout.clientWidth + 1);

      await page.screenshot({
        path: path.join(
          evidenceDir,
          `${surface.name}-${viewport.width}x${viewport.height}.png`
        ),
        fullPage: false,
        animations: 'disabled'
      });
    }
  }
});
