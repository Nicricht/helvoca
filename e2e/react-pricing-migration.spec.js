const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const plans = [
  {
    code: 'EMPRENDE',
    name: 'Emprende',
    monthlyPriceClp: 24990,
    includedMinutes: 100,
    maxConcurrentCalls: 1,
    overagePerMinuteClp: 149,
    customPricing: false,
    recommended: false
  },
  {
    code: 'NEGOCIO',
    name: 'Negocio',
    monthlyPriceClp: 39990,
    includedMinutes: 250,
    maxConcurrentCalls: 2,
    overagePerMinuteClp: 129,
    customPricing: false,
    recommended: true
  },
  {
    code: 'PRO',
    name: 'Pro',
    monthlyPriceClp: 69990,
    includedMinutes: 500,
    maxConcurrentCalls: 4,
    overagePerMinuteClp: 109,
    customPricing: false,
    recommended: false
  },
  {
    code: 'ENTERPRISE',
    name: 'Enterprise',
    monthlyPriceClp: 119990,
    includedMinutes: 1000,
    maxConcurrentCalls: 8,
    overagePerMinuteClp: null,
    customPricing: true,
    recommended: false
  }
];

test.describe('React public pricing migration', () => {
  test('canonical pricing route is public, API-backed and read-only', async ({ page }) => {
    const writes = [];
    let pricingReads = 0;

    await page.route('**/api/v1/**', async route => {
      const request = route.request();
      const pathname = new URL(request.url()).pathname;

      if (request.method() !== 'GET') {
        writes.push({ method: request.method(), path: pathname });
      }

      if (request.method() === 'GET' && pathname === '/api/v1/public/pricing') {
        pricingReads += 1;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(plans)
        });
        return;
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({})
      });
    });

    await page.goto('/app/pricing');

    await expect(page).toHaveURL(/\/app\/pricing\/?$/);
    await expect(page).toHaveTitle(/Planes.*RecepVoz|RecepVoz.*Planes/i);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(
      'Empieza pequeño. Mide resultados. Escala cuando realmente lo necesites.'
    );

    await expect(page.getByRole('heading', { name: 'Emprende' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Negocio' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Pro' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Enterprise' })).toBeVisible();
    await expect(page.getByText('RECOMENDADO')).toBeVisible();

    expect(pricingReads).toBeGreaterThan(0);
    expect(writes).toEqual([]);
  });

  test('pricing preserves trial and Sales journeys without creating checkout', async ({ page }) => {
    await page.route('**/api/v1/public/pricing', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(plans)
    }));

    await page.goto('/app/pricing');

    await expect(page.getByRole('link', { name: 'Configurar una prueba' })).toHaveAttribute('href', '/');
    await expect(page.getByRole('link', { name: 'Ver cómo funciona' })).toHaveAttribute('href', '/app/sales');
    await expect(page.locator('a[href*="checkout"], a[href*="payment"], a[href*="mercadopago"]')).toHaveCount(0);
  });

  test('pricing exposes a useful failure state when public catalog cannot load', async ({ page }) => {
    await page.route('**/api/v1/public/pricing', route => route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'unavailable' })
    }));

    await page.goto('/app/pricing');

    await expect(page.getByText('No pudimos cargar los planes. Intenta nuevamente.')).toBeVisible();
  });

  test('legacy pricing URL redirects to canonical React route and retires legacy assets', async ({ page }) => {
    const requests = [];
    page.on('request', request => requests.push(new URL(request.url()).pathname));

    await page.route('**/api/v1/public/pricing', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(plans)
    }));

    await page.goto('/pricing.html');

    await expect(page).toHaveURL(/\/app\/pricing\/?$/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    expect(requests).not.toContain('/pricing.css');
    expect(requests).not.toContain('/pricing.js');
  });

  for (const viewport of [
    { width: 1536, height: 950 },
    { width: 768, height: 1024 },
    { width: 390, height: 844 }
  ]) {
    test('pricing stays contained at ' + viewport.width + 'px', async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.route('**/api/v1/public/pricing', route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(plans)
      }));

      await page.goto('/app/pricing');
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

      const layout = await page.evaluate(() => ({
        clientWidth: document.documentElement.clientWidth,
        scrollWidth: document.documentElement.scrollWidth
      }));

      expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1);
    });
  }

  test('reduced motion keeps the complete pricing journey usable', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.route('**/api/v1/public/pricing', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(plans)
    }));

    await page.goto('/app/pricing');

    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Negocio' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Configurar una prueba' })).toBeVisible();
  });

  test('pricing route is public in React and direct-route packaging is declared', async () => {
    const root = path.resolve(__dirname, '..');
    const app = fs.readFileSync(path.join(root, 'frontend/src/app/App.tsx'), 'utf8');
    const vite = fs.readFileSync(path.join(root, 'frontend/vite.config.ts'), 'utf8');
    const controller = fs.readFileSync(
      path.join(root, 'src/main/java/cl/helvoca/frontend/ReactFrontendController.java'),
      'utf8'
    );
    const legacy = fs.readFileSync(path.join(root, 'src/main/resources/static/pricing.html'), 'utf8');

    const publicPricing = app.indexOf('path="/pricing"');
    const protectedOutlet = app.indexOf('<Route element={<ProtectedOutlet />}>');

    expect(app).toContain('PricingPage');
    expect(publicPricing).toBeGreaterThan(-1);
    expect(protectedOutlet).toBeGreaterThan(-1);
    expect(publicPricing).toBeLessThan(protectedOutlet);

    expect(vite).toContain('pricingDir');
    expect(vite).toContain('static/app/pricing/');
    expect(controller).toContain('"/app/pricing"');

    expect(legacy).toContain('/app/pricing');
    expect(legacy).not.toContain('/pricing.js');
    expect(legacy).not.toContain('/pricing.css');
  });
});
