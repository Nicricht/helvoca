const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootReactSettings(page) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'settings-full-cutover'
  );

  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() !== 'GET') {
      return route.fulfill(json({ message: 'mutation blocked by cutover RED' }, 501));
    }
    if (path === '/api/v1/auth/me') {
      return route.fulfill(json({
        email: 'admin@demo.cl',
        roles: ['BUSINESS_ADMIN'],
        permissions: ['BUSINESS_READ', 'BUSINESS_MANAGE']
      }));
    }
    if (path === '/api/v1/business') {
      return route.fulfill(json({
        name: 'Negocio Cutover',
        timezone: 'America/Santiago',
        language: 'es'
      }));
    }
    if (path === '/api/v1/business/profile') return route.fulfill(json({ defaultCurrency: 'CLP' }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({ readyForCalls: true }));
    if (path === '/api/v1/services') {
      return route.fulfill(json([{ id: 'svc-1', name: 'Servicio', durationMinutes: 30, price: 10000, active: true }]));
    }
    if (path === '/api/v1/business/hours') {
      return route.fulfill(json([{ dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }]));
    }
    if (path === '/api/v1/knowledge') {
      return route.fulfill(json([{ id: 'k-1', title: 'FAQ', content: 'Respuesta', active: true }]));
    }
    if (path === '/api/v1/ai-agent/voices') {
      return route.fulfill(json([{ code: 'natural', selection: 'marin', name: 'Natural' }]));
    }
    if (path === '/api/v1/ai-agent') {
      return route.fulfill(json({
        configured: true,
        name: 'Helvoca',
        language: 'es',
        voice: 'marin',
        greeting: 'Hola',
        instructions: 'Responde brevemente',
        active: true,
        capabilities: []
      }));
    }
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([]));
    return route.fulfill(json({}));
  });
}

test.describe('Settings full cutover RED contract', () => {
  for (const surface of [
    { path: '/', selector: '.nav-config' },
    { path: '/inventory.html', selector: '.nav-config' },
    { path: '/account.html', selector: 'a[href*="settings"]' },
    { path: '/simulator.html', selector: 'a[href*="settings"]' },
    { path: '/conversations.html', selector: 'a[href*="settings"]' },
    { path: '/business-import.html', selector: 'a[href*="settings"]' }
  ]) {
    test(`${surface.path} sends Configuración to the canonical React route`, async ({ page }) => {
      await page.goto(surface.path);
      const links = page.locator(surface.selector);
      await expect(links.first()).toHaveAttribute('href', /^\/app\/settings(?:[?#].*)?$/);
    });
  }

  test('React Settings no longer sends users back to the legacy Settings screen', async ({ page }) => {
    await bootReactSettings(page);
    await page.goto('/app/settings?section=team');

    await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();
    await expect(page.locator('a[href^="/settings.html"]')).toHaveCount(0);
  });
});
