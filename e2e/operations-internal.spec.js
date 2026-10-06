const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootInternalOperations(page, roles = ['BUSINESS_ADMIN']) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'internal-operations-e2e'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'ops@demo.cl',
    roles
  })));

  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    plan: 'PRO',
    publicPlanCode: 'PRO',
    planName: 'Profesional',
    status: 'ACTIVE',
    includedMinutes: 500,
    usedMinutes: 35
  })));

  await page.route('**/api/v1/operations/pilot-readiness', route => route.fulfill(json({
    ready: true,
    passed: 3,
    total: 3,
    blockers: [],
    checks: [
      { code: 'VOICE', label: 'Llamadas con IA', ready: true, detail: 'Telefonía operativa.' },
      { code: 'WHATSAPP', label: 'WhatsApp', ready: true, detail: 'Canal operativo.' },
      { code: 'PAYMENTS', label: 'Pagos', ready: true, detail: 'Sandbox preparado.' }
    ]
  })));

  let control = {
    status: 'READY',
    launchDecision: 'GO',
    blockers: [],
    responsibleName: 'Carla Pérez',
    responsibleContact: 'ops@demo.cl',
    goal: 'Certificar el flujo comercial',
    plannedEndAt: '2026-10-10T03:00:00Z',
    canStart: true,
    canPause: false,
    canResume: false,
    canComplete: false
  };

  await page.route('**/api/v1/operations/pilot-control', async route => {
    if (route.request().method() === 'PUT') {
      control = { ...control, ...route.request().postDataJSON() };
    }
    await route.fulfill(json(control));
  });

  await page.route('**/api/v1/operations/pilot-control/start', async route => {
    control = {
      ...control,
      status: 'RUNNING',
      launchDecision: 'RUNNING',
      canStart: false,
      canPause: true,
      canComplete: true
    };
    await route.fulfill(json(control));
  });

  await page.route('**/api/v1/operations/pilot-control/pause', async route => {
    control = {
      ...control,
      status: 'PAUSED',
      launchDecision: 'PAUSED',
      canStart: false,
      canPause: false,
      canResume: true,
      canComplete: true
    };
    await route.fulfill(json(control));
  });

  await page.route('**/api/v1/operations/pilot-control/resume', async route => {
    control = {
      ...control,
      status: 'RUNNING',
      launchDecision: 'RUNNING',
      canStart: false,
      canPause: true,
      canResume: false,
      canComplete: true
    };
    await route.fulfill(json(control));
  });

  await page.route('**/api/v1/operations/pilot-control/complete', async route => {
    control = {
      ...control,
      status: 'COMPLETED',
      launchDecision: 'COMPLETED',
      canStart: false,
      canPause: false,
      canResume: false,
      canComplete: false
    };
    await route.fulfill(json(control));
  });

  await page.route('**/api/v1/operations/pilot-preflight', route => route.fulfill(json({
    decision: 'GO',
    pilotStatus: 'READY',
    trafficMode: 'BLOCKED_GLOBAL',
    globalExternalEffectsEnabled: false,
    blockers: [],
    warnings: [],
    checks: [
      { code: 'TECH_VOICE', label: 'Llamadas con IA', passed: true, required: true, detail: 'Voz lista.' }
    ],
    snapshot: {
      ordersToday: 2,
      paymentAttemptsToday: 1,
      successfulPaymentsToday: 1,
      availableInventoryUnits: 8,
      lowStockAlerts: 0,
      outOfStockAlerts: 0,
      reconciliationAnomalies: 0
    }
  })));

  await page.route('**/api/v1/operations/pilot-metrics', route => route.fulfill(json({
    timezone: 'America/Santiago',
    today: {
      calls: 4, whatsappConversations: 3, bookings: 2, orders: 2,
      successfulPayments: 1, pendingPayments: 1, failedPayments: 0,
      humanTransfers: 1, callFailures: 1, confirmedRevenueByCurrency: { CLP: 18990 },
      paidOrderConversionPct: 50, paymentSuccessRatePct: 50,
      callFailureRatePct: 25, humanTransferRatePct: 25
    },
    last7Days: {
      calls: 30, whatsappConversations: 20, bookings: 12, orders: 10,
      successfulPayments: 8, pendingPayments: 1, failedPayments: 0,
      humanTransfers: 3, callFailures: 2, confirmedRevenueByCurrency: { CLP: 145000 },
      paidOrderConversionPct: 80, paymentSuccessRatePct: 88.9,
      callFailureRatePct: 6.7, humanTransferRatePct: 10
    }
  })));
}

test.describe('React internal operations migration', () => {
  test('specialized internal route preserves readiness, launch cage, metrics and pilot lifecycle', async ({ page }) => {
    await bootInternalOperations(page);

    const writes = [];
    page.on('request', request => {
      const url = new URL(request.url());
      if (url.pathname.startsWith('/api/v1/') && !['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
        writes.push({ method: request.method(), path: url.pathname });
      }
    });

    await page.goto('/app/internal/operations');

    await expect(page.getByRole('heading', { level: 1, name: 'Operación y certificación' })).toBeVisible();
    await expect(page.getByText('Preparación para operar')).toBeVisible();
    await expect(page.getByTestId('pilot-readiness-score')).toHaveText('3/3');
    await expect(page.getByText('Launch cage', { exact: true })).toBeVisible();
    await expect(page.getByTestId('pilot-preflight-decision')).toHaveText('GO');
    await expect(page.getByTestId('pilot-preflight-traffic')).toContainText('bloqueado globalmente');
    await expect(page.getByText('Métricas del piloto')).toBeVisible();
    await expect(page.getByTestId('pilot-metric-conversations')).toHaveText('7');
    await expect(page.getByTestId('pilot-metric-revenue')).toContainText('$18.990');

    await page.getByRole('button', { name: 'Iniciar piloto' }).click();
    await expect(page.getByTestId('pilot-control-state')).toHaveText('RUNNING');
    await expect(page.getByRole('button', { name: 'Pausar' })).toBeVisible();

    expect(writes).toEqual([
      { method: 'POST', path: '/api/v1/operations/pilot-control/start' }
    ]);
  });

  test('legacy URL is compatibility-only and no retired operations assets are requested', async ({ page }) => {
    await bootInternalOperations(page);
    const requested = [];
    page.on('request', request => requested.push(new URL(request.url()).pathname));

    await page.goto('/operations.html');

    await expect(page).toHaveURL(/\/app\/internal\/operations\/?$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Operación y certificación' })).toBeVisible();

    for (const legacyAsset of [
      '/operations.js',
      '/pilot-readiness.js',
      '/pilot-control.js',
      '/pilot-preflight.js',
      '/pilot-metrics.js'
    ]) {
      expect(requested).not.toContain(legacyAsset);
    }
  });

  test('internal operations stays out of normal customer navigation', async ({ page }) => {
    await bootInternalOperations(page);
    await page.goto('/app/internal/operations');

    const nav = page.getByRole('navigation', { name: 'Navegación principal' });
    await expect(nav.getByRole('link', { name: /piloto|certificación|operación interna/i })).toHaveCount(0);
  });

  test('unauthenticated visitors are redirected to the owner entry', async ({ page }) => {
    await page.goto('/app/internal/operations');
    await expect.poll(() => new URL(page.url()).pathname).toBe('/');
  });
});
