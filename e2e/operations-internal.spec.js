const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('internal operations surface hosts readiness control and pilot metrics away from owner home', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'ops@demo.cl',
    roles: ['BUSINESS_ADMIN']
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

  await page.goto('/operations.html');

  await expect(page.getByRole('heading', { level: 1, name: 'Operación y certificación' })).toBeVisible();
  await expect(page.locator('#operationalOverview')).toHaveCount(0);
  await expect(page.locator('#pilotReadinessCard')).toBeVisible();
  await expect(page.locator('#pilotReadinessScore')).toHaveText('3/3');
  await expect(page.locator('#pilotControlCard')).toBeVisible();
  await expect(page.locator('#pilotControlBadge')).toHaveText('GO');
  await expect(page.locator('#pilotMetricsCard')).toBeVisible();
  await expect(page.locator('#pilotMetricConversations')).toHaveText('7');
  await expect(page.locator('#pilotMetricRevenue')).toContainText('$18.990');

  await page.locator('#pilotStart').click();
  await expect(page.locator('#pilotControlBadge')).toHaveText('RUNNING');
  await expect(page.locator('#pilotPause')).toBeVisible();

  await page.locator('[data-pilot-period="last7Days"]').click();
  await expect(page.locator('#pilotMetricConversations')).toHaveText('50');
  await expect(page.locator('#pilotMetricRevenue')).toContainText('$145.000');
});

test('internal operations surface redirects unauthenticated visitors to owner entry', async ({ page }) => {
  await page.goto('/operations.html');
  await expect.poll(() => new URL(page.url()).pathname).toBe('/');
});
