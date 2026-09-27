const { test, expect } = require('@playwright/test');
const path = require('path');

test('pilot preflight renders GO with real traffic still blocked', async ({ page }) => {
  await page.goto('/');
  await page.setContent(`
    <div id="dashboardView"></div>
    <button id="refreshBtn" type="button">refresh</button>
    <script>
      sessionStorage.setItem('helvoca_access_token', 'e2e-token');
      window.api = async () => ({
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
    </script>
  `);

  await page.addScriptTag({
    path: path.resolve('src/main/resources/static/pilot-preflight.js')
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
  await page.goto('/');
  await page.setContent(`
    <div id="dashboardView"></div>
    <script>
      sessionStorage.setItem('helvoca_access_token', 'e2e-token');
      window.api = async () => ({
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
    </script>
  `);

  await page.addScriptTag({
    path: path.resolve('src/main/resources/static/pilot-preflight.js')
  });

  await expect(page.locator('#pilotPreflightDecision')).toHaveText('NO_GO');
  await expect(page.locator('#pilotPreflightChecks .fail')).toHaveCount(2);
  await expect(page.locator('#pilotPreflightFooter')).toContainText('INVENTORY');
  await expect(page.locator('#pilotPreflightFooter')).toContainText('RECONCILIATION');
});
