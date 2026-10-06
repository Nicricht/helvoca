const { test, expect } = require('@playwright/test');
const fs = require('node:fs');

const STATIC = 'src/main/resources/static';

function read(name) {
  return fs.readFileSync(`${STATIC}/${name}`, 'utf8');
}

test('remaining legacy shells point only to canonical application destinations', async () => {
  const home = read('index.html');
  const simulator = read('simulator.html');
  const importer = read('business-import.html');

  for (const [name, html] of [
    ['index.html', home],
    ['business-import.html', importer]
  ]) {
    expect(html, name).not.toContain('href="/conversations.html"');
    expect(html, name).not.toContain('href="/account.html"');
    expect(html, name).not.toContain('href="/operations.html"');
  }

  expect(home).toContain('href="/app/agenda">Agenda</a>');
  expect(home).toContain('href="/app/orders">Operaciones</a>');
  expect(home).toContain('href="/app/inventory">Inventario</a>');
  expect(home).toContain('href="/app/settings">Configuración</a>');
  expect(home).toContain('href="/app/plan">Plan y consumo</a>');

  expect(simulator).toContain('url=/app/simulator');
  expect(simulator).toContain("window.location.replace('/app/simulator')");
  expect(importer).toContain('url=/app/settings/import');
  expect(importer).toContain("window.location.replace(\'/app/settings/import\')");
  expect(importer).not.toContain('/business-import.js');
  expect(importer).not.toContain('/business-import.css');
});

test('home Agenda shortcuts use the canonical React route', async () => {
  const home = read('index.html');
  const status = read('commercial-status.js');

  expect(home).toContain('href="/app/agenda">Agenda</a>');
  expect(home).not.toContain('href="/#bookings">Agenda</a>');
  expect(status).toContain('href="/app/agenda"');
  expect(status).toContain('href="/app/orders">Operaciones</a>');
  expect(status).not.toContain('/conversations.html');
  expect(status).not.toContain('/?tab=bookings#homeBusinessWorkspace');
});

test('canonical foundation remains dark, solid and readable', async () => {
  const css = read('frontend-foundation.css');

  expect(css).toContain('--rv-bg-canvas: #06111c');
  expect(css).toContain('--rv-surface-1: #0b1b29');
  expect(css).toContain('--rv-text-primary: #f4fbff');
  expect(css).toContain('--rv-accent: #16d9f5');
  expect(css).not.toMatch(/(?:linear|radial)-gradient/i);
  expect(css).toContain('.app-nav a,.inventory-nav a,.topbar nav a,.account-nav a,.rv-nav a{font-size:14px}');
});

test('shared legacy styles stay solid and retired sales stylesheet stays absent', async () => {
  const css = read('styles.css');

  expect(css, 'styles.css').not.toMatch(/(?:linear|radial)-gradient/i);
  expect(
    fs.existsSync(`${STATIC}/sales.css`),
    'sales.css should remain retired after the React sales migration'
  ).toBe(false);
});

test('legacy simulator document is compatibility-only', async () => {
  const simulator = read('simulator.html');

  expect(simulator).toContain('url=/app/simulator');
  expect(simulator).not.toContain('/simulator.js');
  expect(simulator).not.toContain('/simulator.css');
});

test('canonical customer surfaces stay contained at required responsive widths', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('https://cdn.jsdelivr.net/**', route => route.abort());
  await page.route('**/api/v1/**', route => route.fulfill({
    status: 404,
    contentType: 'application/json',
    body: '{}'
  }));
  await page.route('**/api/v1/auth/me', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ email: 'admin@demo.cl', roles: ['BUSINESS_ADMIN'] })
  }));
  await page.route('**/api/v1/business', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ name: 'Negocio responsive', timezone: 'America/Santiago', language: 'es' })
  }));

  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1024 });

    for (const path of ['/', '/app', '/app/settings', '/app/settings/import', '/app/inventory', '/app/agenda', '/app/plan', '/app/simulator']) {
      await page.goto(path);
      await page.waitForTimeout(80);
      const layout = await page.evaluate(() => {
        const clientWidth = document.documentElement.clientWidth;
        const offenders = [...document.querySelectorAll('body *')]
          .map(element => {
            const rect = element.getBoundingClientRect();
            return {
              tag: element.tagName.toLowerCase(),
              id: element.id || '',
              className: typeof element.className === 'string' ? element.className : '',
              left: Math.round(rect.left),
              right: Math.round(rect.right),
              width: Math.round(rect.width)
            };
          })
          .filter(item => item.width > 0 && (item.right > clientWidth + 1 || item.left < -1))
          .sort((a, b) => b.right - a.right)
          .slice(0, 8);
        return {
          scrollWidth: document.documentElement.scrollWidth,
          clientWidth,
          offenders
        };
      });
      expect(
        layout.scrollWidth,
        `${path} at ${width}px overflow: ${JSON.stringify(layout.offenders)}`
      ).toBeLessThanOrEqual(layout.clientWidth + 1);
    }
  }
});

test('remaining simulator navigation stays keyboard reachable', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/business', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ name: 'Negocio teclado' })
  }));

  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1024 });
    await page.goto('/app/simulator');

    const nav = page.getByRole('navigation', { name: 'Navegación principal' });
    const links = nav.getByRole('link');
    expect(await links.count()).toBeGreaterThan(1);

    await links.nth(0).focus();
    await expect(links.nth(0)).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(links.nth(1)).toBeFocused();
  }
});
