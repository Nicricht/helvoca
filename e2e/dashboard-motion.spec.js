const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mockDashboardBoot(page) {
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/v1/auth/me') return route.fulfill(json({ email: 'admin@demo.cl', roles: ['BUSINESS_ADMIN'] }));
    if (path === '/api/v1/business') return route.fulfill(json({
      name: 'Negocio Movimiento', timezone: 'America/Santiago', language: 'es', humanTransferPhone: null
    }));
    if (path === '/api/v1/business/profile') return route.fulfill(json({}));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({
      businessProfileConfigured: true,
      servicesConfigured: true,
      scheduleConfigured: true,
      knowledgeConfigured: true,
      phoneConfigured: true,
      readyForCalls: true,
      nextStep: 'READY'
    }));
    if (path === '/api/v1/services') return route.fulfill(json([]));
    if (path === '/api/v1/business/hours') return route.fulfill(json([]));
    if (path === '/api/v1/knowledge') return route.fulfill(json([]));
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([]));
    if (path === '/api/v1/ai-agent') return route.fulfill(json({ configured: true, active: true, capabilities: [] }));
    if (path === '/api/v1/public/pricing') return route.fulfill(json([]));
    if (path === '/api/v1/bookings') return route.fulfill(json([]));
    if (path === '/api/v1/customers') return route.fulfill(json([]));
    if (path === '/api/v1/commercial/orders') return route.fulfill(json([]));
    if (path === '/api/v1/messaging/conversations') return route.fulfill(json([]));
    if (path === '/api/v1/audit') return route.fulfill(json([]));
    return route.fulfill(json({}));
  });
}

test('dashboard motion layer is loaded and includes reduced-motion protection', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('link[href*="dashboard-motion.css"]')).toHaveCount(1);

  const response = await page.request.get('/dashboard-motion.css');
  expect(response.status()).toBe(200);
  const css = await response.text();
  expect(css).toContain('@media (prefers-reduced-motion: reduce)');
  expect(css).toContain('#operationalOverview');
});

test('dashboard entry can reset stale scroll position without affecting refresh behavior', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockDashboardBoot(page);
  await page.goto('/');
  await expect(page.locator('#dashboardView')).toBeVisible();

  await page.evaluate(() => {
    const spacer = document.createElement('div');
    spacer.id = 'motion-scroll-spacer';
    spacer.style.height = '2600px';
    document.querySelector('#dashboardView').appendChild(spacer);
    window.scrollTo(0, document.body.scrollHeight);
  });

  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(300);
  await page.evaluate(() => window.loadDashboard({ resetScroll: true }));
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
});


test('ready Inicio is a focused live business pulse with reusable visual assets', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockDashboardBoot(page);
  await page.goto('/');

  await expect(page.locator('#dashboardView')).toBeVisible();
  await expect(page.locator('.dashboard-hero-pulse')).toBeVisible();
  await expect(page.locator('.home-pulse-robot')).toHaveAttribute('src', '/assets/home/recepvoz-robot.png');
  await expect(page.locator('.home-pulse-wave')).toHaveAttribute('src', '/assets/home/recepvoz-wave.png');

  await expect(page.locator('#operationalOverview')).toHaveClass(/owner-pulse-dashboard/);
  await expect(page.locator('#operationalOverview .home-pulse-kpi:visible')).toHaveCount(3);
  await expect(page.locator('#ownerDashboardTitle')).toHaveText('RecepVoz está trabajando');
  await expect(page.locator('#ownerValueTitle')).toHaveText('Esta semana');
  await expect(page.locator('#ownerQuickActions')).toHaveCount(0);
  await expect(page.locator('#ownerPlanRow')).toHaveCount(0);

  for (const asset of [
    '/assets/home/recepvoz-robot.png',
    '/assets/home/recepvoz-wave.png',
    '/assets/home/recepvoz-nebula.png',
    '/assets/home/recepvoz-bubble.png',
    '/assets/home/recepvoz-particles.png',
    '/assets/home/recepvoz-glow.png',
    '/assets/home/recepvoz-flare.png'
  ]) {
    const response = await page.request.get(asset);
    expect(response.status(), asset).toBe(200);
  }

  const cssResponse = await page.request.get('/dashboard-motion.css');
  expect(cssResponse.status()).toBe(200);
  const css = await cssResponse.text();
  expect(css).toContain('.home-pulse-robot');
  expect(css).toContain('.home-pulse-wave');
  expect(css).toContain('@media (prefers-reduced-motion: reduce)');
});
