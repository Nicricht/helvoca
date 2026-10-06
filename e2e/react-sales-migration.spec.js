const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

test.describe('React public sales landing migration', () => {
  test('canonical sales route is public, premium and performs no API writes on visit', async ({ page }) => {
    const writes = [];
    await page.route('**/api/v1/**', async route => {
      if (route.request().method() !== 'GET') {
        writes.push({
          method: route.request().method(),
          path: new URL(route.request().url()).pathname
        });
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({})
      });
    });

    await page.goto('/app/sales');

    await expect(page).toHaveURL(/\/app\/sales\/?$/);
    await expect(page).toHaveTitle(/RecepVoz.*IA|IA.*RecepVoz/i);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      'Que una llamada o un WhatsApp sin responder'
    );
    await expect(page.getByRole('link', { name: 'Probar con mi negocio' })).toHaveAttribute('href', '/');
    await expect(page.getByRole('link', { name: /Planes desde \$24\.990/ })).toHaveAttribute('href', '/pricing.html');
    await expect(page.getByRole('navigation', { name: 'Navegación principal' })).toHaveCount(0);
    expect(writes).toEqual([]);
  });

  test('product storytelling uses the existing first-party visual assets', async ({ page }) => {
    await page.goto('/app/sales');

    await expect(page.locator('img[src="/app/assets/home/hero-bot.webp"]')).toBeVisible();
    await expect(page.locator('img[src="/app/assets/home/agenda.webp"]')).toBeVisible();
    await expect(page.locator('img[src="/app/assets/home/orders.webp"]')).toBeVisible();
    await expect(page.locator('img[src="/app/assets/home/inventory.webp"]')).toBeVisible();
    await expect(page.locator('img[src="/app/assets/home/automation.webp"]')).toBeVisible();

    await expect(page.getByRole('heading', { name: /RecepVoz trabaja mientras tu equipo sigue con el negocio/i })).toBeVisible();
  });

  test('legacy sales URL redirects to canonical React route and does not load sales.css', async ({ page }) => {
    const requests = [];
    page.on('request', request => requests.push(new URL(request.url()).pathname));

    await page.goto('/sales.html');

    await expect(page).toHaveURL(/\/app\/sales\/?$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      'Que una llamada o un WhatsApp sin responder'
    );
    expect(requests).not.toContain('/sales.css');
  });

  for (const viewport of [
    { width: 1536, height: 950 },
    { width: 768, height: 1024 },
    { width: 390, height: 844 }
  ]) {
    test(\`sales landing stays contained at \${viewport.width}px\`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.goto('/app/sales');
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

      const layout = await page.evaluate(() => ({
        clientWidth: document.documentElement.clientWidth,
        scrollWidth: document.documentElement.scrollWidth
      }));

      expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1);
    });
  }

  test('reduced motion still exposes the complete commercial journey', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/app/sales');

    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Cómo empieza' })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Pruébalo con tu propio negocio/i })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Configurar mi prueba' })).toBeVisible();
  });

  test('sales is public while authenticated application routes remain protected', async () => {
    const root = path.resolve(__dirname, '..');
    const app = fs.readFileSync(path.join(root, 'frontend/src/app/App.tsx'), 'utf8');

    const publicSales = app.indexOf('path="/sales"');
    const protectedOutlet = app.indexOf('<Route element={<ProtectedOutlet />}>');

    expect(publicSales).toBeGreaterThan(-1);
    expect(protectedOutlet).toBeGreaterThan(-1);
    expect(publicSales).toBeLessThan(protectedOutlet);
    expect(app).toContain('<AuthBoundary>');
  });
});
