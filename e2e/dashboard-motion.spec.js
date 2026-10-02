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
    spacer.setAttribute('aria-hidden', 'true');
    spacer.style.height = '2600px';
    document.body.appendChild(spacer);
    document.documentElement.style.minHeight = '4000px';
    document.body.style.minHeight = '4000px';
    window.scrollTo(0, 1200);
  });

  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(300);
  await page.evaluate(() => window.loadDashboard({ resetScroll: true }));
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
});
