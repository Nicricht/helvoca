const { test, expect } = require('@playwright/test');
const path = require('path');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mountPilotPreflight(page, payload) {
  await page.route('**/api/v1/operations/pilot-preflight', route => route.fulfill(json(payload)));
  await page.goto('/__pilot_preflight_fixture__');
  await page.evaluate(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.setContent(`
    <div id="dashboardView">
      <div id="operationalOverview"></div>
    </div>
    <button id="refreshBtn" type="button">refresh</button>
    <script>
      async function api(url) {
        const response = await fetch(url);
        if (!response.ok) {
          const error = new Error('HTTP ' + response.status);
          error.status = response.status;
          throw error;
        }
        return response.json();
      }
    </script>
  `);
  await page.addScriptTag({
    path: path.resolve('src/main/resources/static/pilot-preflight.js')
  });
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

  await expect(page.locator('#pilotPreflightCard')).toBeVisible();
  await expect(page.locator('#pilotPreflightDecision')).toHaveText('GO');
  await expect(page.locator('#pilotPreflightTraffic')).toContainText('bloqueado globalmente');
  await expect(page.locator('#pilotPreflightSwitch')).toHaveText('Switch global: OFF');
  await expect(page.locator('#pilotPreflightOrders')).toHaveText('3');
  await expect(page.locator('#pilotPreflightPayments')).toHaveText('2 / 1');
  await expect(page.locator('#pilotPreflightInventory')).toHaveText('14');
  await expect(page.locator('#pilotPreflightAnomalies')).toHaveText('0');
  await expect(page.locator('#pilotPreflightFooter')).toContainText('GO con observaciones');
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

  await expect(page.locator('#pilotPreflightCard')).toBeVisible();
  await expect(page.locator('#pilotPreflightDecision')).toHaveText('NO_GO');
  await expect(page.locator('#pilotPreflightChecks .fail')).toHaveCount(2);
  await expect(page.locator('#pilotPreflightFooter')).toContainText('INVENTORY');
  await expect(page.locator('#pilotPreflightFooter')).toContainText('RECONCILIATION');
});
