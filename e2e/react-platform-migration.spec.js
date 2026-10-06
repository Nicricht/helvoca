const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function boot(page, roles = ['PLATFORM_ADMIN']) {
  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'platform-react-e2e');
  });

  const writes = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;

    if (request.method() !== 'GET') {
      writes.push({ method: request.method(), path: pathname });
      return route.fulfill(json({}));
    }

    if (pathname === '/api/v1/auth/me') {
      return route.fulfill(json({
        email: 'platform@recepvoz.cl',
        roles,
        permissions: []
      }));
    }

    if (pathname === '/api/v1/platform/demos/readiness') {
      return route.fulfill(json({
        runtimeConfigured: true,
        runtimeBusinessId: '99999999-8888-7777-6666-555555555555',
        runtime: { state: 'READY', detail: 'Live Demo Runtime' },
        voiceNumber: { state: 'READY', detail: '+56911112222' },
        voiceAi: { state: 'READY', detail: 'Demo AI' },
        businessData: { state: 'NOT_CONFIGURED', detail: 'No staged profile.' },
        operations: { state: 'READY', detail: 'DEMO isolated.' },
        whatsapp: { state: 'NOT_CONFIGURED', detail: 'Not armed.' },
        payment: { state: 'SANDBOX_ONLY', detail: 'LIVE disabled.' },
        externalEffects: { state: 'DISARMED', detail: 'Outbound disabled.' }
      }));
    }

    if (pathname === '/api/v1/platform/demos') return route.fulfill(json([]));
    if (pathname === '/api/v1/platform/demo-sessions/current') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (pathname === '/api/v1/platform/economics') {
      return route.fulfill(json({
        businessCount: 0,
        estimatedCommercialValueClp: 0,
        estimatedPlatformCostUsd: 0,
        businesses: [],
        providers: []
      }));
    }
    if (pathname === '/api/v1/subscription') {
      return route.fulfill(json({
        plan: 'PRO',
        publicPlanCode: 'PRO',
        planName: 'Profesional',
        status: 'ACTIVE',
        includedMinutes: 500,
        usedMinutes: 0
      }));
    }

    return route.fulfill(json({}));
  });

  return { writes };
}

test.describe('React PLATFORM_ADMIN console migration', () => {
  test('canonical platform route renders a dedicated non-tenant console without writes on visit', async ({ page }) => {
    const state = await boot(page);

    await page.goto('/app/platform');

    await expect(page).toHaveURL(/\/app\/platform\/?$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Control de plataforma' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Centro de Demos' })).toBeVisible();
    await expect(page.locator('header').getByText('PLATFORM ADMIN', { exact: true })).toBeVisible();
    await expect(page.getByText('RecepVoz activo', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: 'Navegación principal' })).toHaveCount(0);
    expect(state.writes).toEqual([]);
  });

  test('legacy platform URL redirects to React and never loads platform.js', async ({ page }) => {
    await boot(page);
    const requests = [];
    page.on('request', request => requests.push(new URL(request.url()).pathname));

    await page.goto('/platform.html');

    await expect(page).toHaveURL(/\/app\/platform\/?$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Control de plataforma' })).toBeVisible();
    expect(requests).not.toContain('/platform.js');
  });

  test('non PLATFORM_ADMIN role cannot enter the platform console', async ({ page }) => {
    await boot(page, ['BUSINESS_ADMIN']);

    await page.goto('/app/platform');

    await expect(page).toHaveURL(/\/$/);
  });

  test('legacy auth bridge sends PLATFORM_ADMIN directly to canonical React route', async () => {
    const root = path.resolve(__dirname, '..');
    const appJs = fs.readFileSync(path.join(root, 'src/main/resources/static/app.js'), 'utf8');

    expect(appJs).toContain('location.replace("/app/platform")');
    expect(appJs).not.toContain('location.replace("/platform.html")');
  });
});
