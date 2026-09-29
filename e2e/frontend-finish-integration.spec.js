const { test, expect } = require('@playwright/test');
const fs = require('node:fs');

const STATIC = 'src/main/resources/static';
const customerPages = [
  'index.html',
  'settings.html',
  'inventory.html',
  'conversations.html',
  'simulator.html',
  'account.html'
];

function read(name) {
  return fs.readFileSync(`${STATIC}/${name}`, 'utf8');
}

function navEntries(html) {
  const nav = html.match(/<nav[^>]+aria-label="Navegación principal"[^>]*>([\s\S]*?)<\/nav>/i);
  if (!nav) return [];
  return [...nav[1].matchAll(/<a\s+([^>]*?)>([\s\S]*?)<\/a>/gi)].map(match => {
    const attrs = match[1];
    const href = attrs.match(/href="([^"]+)"/i)?.[1] || '';
    const text = match[2].replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
    return { href, text };
  });
}

test('customer navigation stays coherent and never exposes internal operations', async () => {
  const expected = [
    { href: '/', text: 'Inicio' },
    { href: '/conversations.html', text: 'Conversaciones' },
    { href: '/#bookings', text: 'Agenda' },
    { href: '/#customers', text: 'Clientes' },
    { href: '/inventory.html', text: 'Inventario' },
    { href: '/settings.html', text: 'Configuración' },
    { href: '/account.html', text: 'Facturación' }
  ];

  for (const page of customerPages) {
    const html = read(page);
    expect(navEntries(html), `${page} primary navigation`).toEqual(expected);
    expect(html, `${page} must not expose internal operations`).not.toContain('href="/operations.html"');
  }
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

test('shared and public sales styles avoid decorative gradients', async () => {
  for (const stylesheet of ['styles.css', 'sales.css']) {
    const css = read(stylesheet);
    expect(css, stylesheet).not.toMatch(/(?:linear|radial)-gradient/i);
  }
});

test('receptionist area keeps history and safe simulation as local actions', async () => {
  const conversations = read('conversations.html');
  const simulator = read('simulator.html');

  expect(conversations).toContain('>Conversaciones</h1>');
  expect(conversations).toContain('href="/simulator.html">Probar recepcionista</a>');
  expect(simulator).toContain('href="/conversations.html">Ver historial</a>');
  expect(simulator).toMatch(/No crea datos comerciales reales ni realiza llamadas telefónicas/i);
  expect(simulator).toMatch(/Tampoco envía WhatsApp real/i);
});

test('customer surfaces stay contained at required responsive widths', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('https://cdn.jsdelivr.net/**', route => route.abort());
  await page.route('**/api/v1/**', route => route.fulfill({
    status: 404,
    contentType: 'application/json',
    body: '{}'
  }));

  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1024 });

    for (const path of ['/', '/settings.html', '/inventory.html', '/conversations.html', '/simulator.html', '/account.html']) {
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

test('primary customer navigation remains keyboard reachable at required widths', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await page.route('**/api/v1/**', route => route.fulfill({
    status: 404,
    contentType: 'application/json',
    body: '{}'
  }));

  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1024 });
    await page.goto('/conversations.html');

    const nav = page.getByRole('navigation', { name: 'Navegación principal' });
    const links = nav.getByRole('link');
    await expect(links).toHaveCount(7);

    await links.nth(0).focus();
    await expect(links.nth(0)).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(links.nth(1)).toBeFocused();
  }
});
