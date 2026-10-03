const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mockCanonicalApp(page) {
  await page.addInitScript(() => {
    if (window.location.pathname.startsWith('/app')) {
      sessionStorage.setItem('helvoca_access_token', 'rc-react-token');
    }
  });

  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const p = url.pathname;
    const method = route.request().method();

    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      return route.fulfill(json({ message: 'RC read-only fixture blocks mutations' }, 501));
    }

    if (p === '/api/v1/auth/me') return route.fulfill(json({
      email: 'admin@ferreteria-rc.cl',
      roles: ['BUSINESS_ADMIN'],
      permissions: [
        'BUSINESS_READ', 'BUSINESS_CONFIGURE', 'CATALOG_READ', 'CATALOG_MANAGE',
        'CUSTOMERS_READ', 'CUSTOMERS_MANAGE', 'CUSTOMERS_EXPORT',
        'BOOKINGS_READ', 'BOOKINGS_MANAGE', 'ORDERS_READ', 'ORDERS_MANAGE',
        'INVENTORY_READ', 'INVENTORY_MANAGE', 'ANALYTICS_READ',
        'OPERATIONS_READ', 'REQUESTS_READ', 'REQUESTS_MANAGE', 'AUDIT_READ'
      ]
    }));
    if (p === '/api/v1/business') return route.fulfill(json({
      id: 'business-rc',
      name: 'Ferretería Release Candidate',
      timezone: 'America/Santiago',
      language: 'es',
      humanTransferPhone: '+56999999999'
    }));
    if (p === '/api/v1/business/profile') return route.fulfill(json({
      businessId: 'business-rc',
      presetKey: 'store',
      publicDescription: 'Ferretería y materiales',
      publicPhone: '+56911112222',
      publicEmail: 'ventas@ferreteria-rc.cl',
      websiteUrl: null,
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
    if (p === '/api/v1/onboarding/status') return route.fulfill(json({
      businessProfileConfigured: true,
      servicesConfigured: true,
      scheduleConfigured: true,
      knowledgeConfigured: true,
      humanTransferConfigured: true,
      phoneConfigured: true,
      readyForCalls: true,
      nextStep: 'READY'
    }));
    if (p === '/api/v1/operations/dashboard') return route.fulfill(json({
      businessName: 'Ferretería Release Candidate',
      timezone: 'America/Santiago',
      localNow: '2026-10-02T18:00:00-03:00',
      callsToday: 12,
      callDurationSecondsToday: 1220,
      bookingsToday: 3,
      newCustomersToday: 2,
      openRequests: 1,
      unansweredQuestions: 0,
      callFailuresToday: 0,
      estimatedCallCostTodayUsd: 2.1,
      recentCalls: [],
      recentRequests: [],
      unanswered: []
    }));
    if (p === '/api/v1/commercial/analytics') return route.fulfill(json({
      days: 7,
      timezone: 'America/Santiago',
      primaryCurrency: 'CLP',
      totalRevenue: 189900,
      paidOrders: 5,
      unitsSold: 7,
      averageTicket: 37980,
      revenueChangePercent: 12.5,
      currencyTotals: [{ currency: 'CLP', amount: 189900 }],
      salesOverTime: [],
      topProducts: [],
      channels: [],
      peakWeekday: 'THURSDAY',
      peakHour: 13,
      recepVozOrders: 4,
      recepVozRevenue: 159900,
      bookingCurrency: 'CLP',
      paidBookings: 3,
      bookingRevenue: 75000,
      providerVerifiedBookingRevenue: 50000,
      manualRecordedBookingRevenue: 25000,
      recepVozPaidBookings: 2,
      recepVozBookingRevenue: 50000,
      bookingCurrencyTotals: [{ currency: 'CLP', amount: 75000 }],
      insights: []
    }));
    if (p === '/api/v1/services') return route.fulfill(json([
      { id: 'svc-rc', name: 'Corte de madera', durationMinutes: 30, price: 12990, active: true }
    ]));
    if (p === '/api/v1/bookings') return route.fulfill(json([]));
    if (p === '/api/v1/customers') return route.fulfill(json([]));
    if (p === '/api/v1/business/hours') return route.fulfill(json([
      { dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }
    ]));
    if (p === '/api/v1/knowledge') return route.fulfill(json([]));
    if (p === '/api/v1/ai-agent/voices') return route.fulfill(json([
      { code: 'natural', selection: 'marin', name: 'Natural', description: 'Conversacional' }
    ]));
    if (p === '/api/v1/ai-agent') return route.fulfill(json({
      configured: true,
      name: 'Sofía',
      language: 'es',
      voice: 'marin',
      greeting: 'Hola, ¿en qué te ayudo?',
      instructions: 'Atiende con claridad.',
      active: true,
      capabilities: ['GET_BUSINESS_INFORMATION', 'LIST_SERVICES']
    }));
    if (p === '/api/v1/phone-numbers') return route.fulfill(json([]));

    if (p === '/api/v1/commercial/orders') return route.fulfill(json([]));
    if (p === '/api/v1/commercial/deliveries') return route.fulfill(json([]));
    if (p === '/api/v1/requests') return route.fulfill(json([]));
    if (p === '/api/v1/audit') return route.fulfill(json([]));

    if (p === '/api/v1/catalog') return route.fulfill(json([
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
    if (p === '/api/v1/inventory') return route.fulfill(json([
      {
        id: 'stock-rc',
        catalogItemId: 'product-rc',
        sku: 'TAL-RC',
        trackingEnabled: true,
        onHand: 8,
        reserved: 1,
        available: 7,
        reorderThreshold: 2,
        lowStock: false
      }
    ]));
    if (p === '/api/v1/inventory/alerts') return route.fulfill(json([]));
    if (p === '/api/v1/inventory/restock-subscriptions') return route.fulfill(json([]));
    if (p === '/api/v1/inventory/restock-subscriptions/notifications') return route.fulfill(json([]));
    if (/^\/api\/v1\/inventory\/[^/]+\/variants$/.test(p)) return route.fulfill(json([]));
    if (/^\/api\/v1\/inventory\/[^/]+\/movements$/.test(p)) return route.fulfill(json([]));

    if (p === '/api/v1/subscription') return route.fulfill(json({
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
    if (p === '/api/v1/usage/summary') return route.fulfill(json([
      { meterKey: 'VOICE_SECONDS', unit: 'SECONDS', quantity: 7500, eventCount: 12 }
    ]));
    if (p === '/api/v1/usage/status') return route.fulfill(json({
      includedMinutes: 500,
      usedMinutes: 125,
      overageMinutes: 0,
      usagePercent: 25,
      alertLevel: 'NORMAL',
      estimatedOverageChargeClp: 0,
      safetyLimitMinutes: 700,
      safetyRemainingMinutes: 575,
      safetyExceeded: false
    }));

    return route.fulfill(json([]));
  });
}

const canonical = [
  { name: 'home', route: '/app', heading: 'Inicio' },
  { name: 'agenda', route: '/app/agenda', heading: 'Agenda' },
  { name: 'operations', route: '/app/orders', heading: 'Pedidos' },
  { name: 'inventory', route: '/app/inventory', heading: 'Inventario' },
  { name: 'settings', route: '/app/settings', heading: 'Configuración' },
  { name: 'plan', route: '/app/plan', heading: 'Plan y consumo' }
];

test('release candidate owner traverses every canonical React workspace', async ({ page }) => {
  await mockCanonicalApp(page);

  await page.goto('/app');
  await expect(page.getByRole('heading', { level: 1, name: 'Inicio' })).toBeVisible();

  const nav = page.getByRole('navigation', { name: 'Navegación principal' });
  const destinations = [
    ['Agenda', '/app/agenda', 'Agenda'],
    ['Operaciones', '/app/orders', 'Pedidos'],
    ['Inventario', '/app/inventory', 'Inventario'],
    ['Configuración', '/app/settings', 'Configuración'],
    ['Plan y consumo', '/app/plan', 'Plan y consumo']
  ];

  for (const [label, href, heading] of destinations) {
    await page.goto('/app');
    const link = page.getByRole('navigation', { name: 'Navegación principal' })
      .getByRole('link', { name: label });
    await expect(link).toHaveAttribute('href', href);
    await link.click();
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
  }

  await page.goto('/app');
  await page.getByRole('button', { name: 'Salir' }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator('#authView')).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeFalsy();
});

test('release candidate canonical React surfaces remain usable on mobile', async ({ page }) => {
  await mockCanonicalApp(page);
  await page.setViewportSize({ width: 390, height: 844 });

  for (const surface of canonical) {
    await page.goto(surface.route);
    await expect(page.getByRole('heading', { level: 1, name: surface.heading })).toBeVisible();
    const layout = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth
    }));
    expect(layout.scrollWidth, surface.route).toBeLessThanOrEqual(layout.clientWidth + 1);
  }
});

test('release candidate captures exact-head React visual evidence for every canonical viewport', async ({ page }) => {
  test.setTimeout(120000);
  await mockCanonicalApp(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });

  const head = (process.env.VISUAL_EVIDENCE_SHA || 'local').slice(0, 12);
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

  for (const viewport of viewports) {
    await page.setViewportSize(viewport);

    for (const surface of canonical) {
      await page.goto(surface.route);
      await expect(page.getByRole('heading', { level: 1, name: surface.heading })).toBeVisible();

      const layout = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth
      }));
      expect(layout.scrollWidth, `${surface.route} at ${viewport.width}px`)
        .toBeLessThanOrEqual(layout.clientWidth + 1);

      await page.screenshot({
        path: path.join(evidenceDir, `${surface.name}-${viewport.width}x${viewport.height}.png`),
        fullPage: false,
        animations: 'disabled'
      });
    }
  }
});
