const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mountPilotPreflight(page, payload) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'pilot-preflight-e2e'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'ops@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));

  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    plan: 'PRO',
    publicPlanCode: 'PRO',
    planName: 'Profesional',
    status: 'ACTIVE',
    includedMinutes: 500,
    usedMinutes: 10
  })));

  await page.route('**/api/v1/operations/pilot-readiness', route => route.fulfill(json({
    ready: true,
    passed: 1,
    total: 1,
    blockers: [],
    checks: [
      { code: 'VOICE', label: 'Llamadas con IA', ready: true, detail: 'Voz lista.' }
    ]
  })));

  await page.route('**/api/v1/operations/pilot-control', route => route.fulfill(json({
    status: 'READY',
    launchDecision: payload.decision === 'GO' ? 'GO' : 'NO_GO',
    blockers: [],
    responsibleName: 'Carla Pérez',
    responsibleContact: 'ops@demo.cl',
    goal: 'Certificar el flujo',
    plannedEndAt: '2026-10-10T03:00:00Z',
    canStart: true,
    canPause: false,
    canResume: false,
    canComplete: false
  })));

  await page.route('**/api/v1/operations/pilot-metrics', route => route.fulfill(json({
    timezone: 'America/Santiago',
    today: {
      calls: 0,
      whatsappConversations: 0,
      bookings: 0,
      orders: 0,
      successfulPayments: 0,
      pendingPayments: 0,
      failedPayments: 0,
      humanTransfers: 0,
      callFailures: 0,
      confirmedRevenueByCurrency: { CLP: 0 }
    },
    last7Days: {}
  })));

  await page.route('**/api/v1/operations/pilot-preflight', route => route.fulfill(json(payload)));

  await page.goto('/app/internal/operations');
}

test('pilot preflight renders GO with real traffic still blocked', async ({ page }) => {
  await mountPilotPreflight(page, {
    decision: 'GO',
    pilotStatus: 'READY',
    trafficMode: 'BLOCKED_GLOBAL',
    globalExternalEffectsEnabled: false,
    externalEffectsArmed: false,
    blockers: [],
    warnings: ['LOW_STOCK:1'],
    checks: [
      { code: 'TECH_VOICE', label: 'Llamadas con IA', passed: true, required: true, detail: 'Voz lista.' },
      { code: 'RECONCILIATION', label: 'V6 sin anomalías pendientes', passed: true, required: true, detail: 'No se detectan inconsistencias.' }
    ],
    snapshot: {
      trackedInventoryItems: 2,
      availableInventoryUnits: 14,
      lowStockAlerts: 1,
      outOfStockAlerts: 0,
      reconciliationAnomalies: 0,
      ordersToday: 3,
      paymentAttemptsToday: 2,
      successfulPaymentsToday: 1,
      pendingPaymentsToday: 1,
      failedPaymentsToday: 0,
      callFailuresToday: 0
    }
  });

  await expect(page.getByTestId('pilot-preflight-decision')).toHaveText('GO');
  await expect(page.getByTestId('pilot-preflight-traffic')).toContainText('bloqueado globalmente');
  await expect(page.getByText('Switch global: OFF')).toBeVisible();
  await expect(page.getByText('3', { exact: true })).toBeVisible();
  await expect(page.getByText('2 / 1')).toBeVisible();
  await expect(page.getByText('14', { exact: true })).toBeVisible();
  await expect(page.getByText('GO con observaciones: LOW_STOCK:1')).toBeVisible();
});

test('pilot preflight makes blockers visually explicit', async ({ page }) => {
  await mountPilotPreflight(page, {
    decision: 'NO_GO',
    pilotStatus: 'READY',
    trafficMode: 'BLOCKED_GLOBAL',
    globalExternalEffectsEnabled: false,
    externalEffectsArmed: false,
    blockers: ['INVENTORY', 'RECONCILIATION'],
    warnings: [],
    checks: [
      { code: 'INVENTORY', label: 'Inventario vendible disponible', passed: false, required: true, detail: 'Configura stock.' },
      { code: 'RECONCILIATION', label: 'V6 sin anomalías pendientes', passed: false, required: true, detail: '1 anomalía requiere revisión.' }
    ],
    snapshot: {
      availableInventoryUnits: 0,
      lowStockAlerts: 0,
      outOfStockAlerts: 1,
      reconciliationAnomalies: 1,
      ordersToday: 0,
      paymentAttemptsToday: 0,
      successfulPaymentsToday: 0
    }
  });

  await expect(page.getByTestId('pilot-preflight-decision')).toHaveText('NO_GO');
  await expect(page.getByText('✕ Inventario vendible disponible')).toBeVisible();
  await expect(page.getByText('✕ V6 sin anomalías pendientes')).toBeVisible();
  await expect(page.getByText('NO-GO: INVENTORY, RECONCILIATION')).toBeVisible();
});
