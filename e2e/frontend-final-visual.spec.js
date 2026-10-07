const { test, expect } = require('@playwright/test');
const fs = require('node:fs');

const STATIC = 'src/main/resources/static';

function read(name) {
  return fs.readFileSync(`${STATIC}/${name}`, 'utf8');
}

async function mountAuthenticatedShell(page, request) {
  const paths = ['/styles.css', '/frontend-foundation.css'];
  const css = [];
  for (const path of paths) {
    const response = await request.get(path);
    expect(response.ok(), path).toBeTruthy();
    css.push(await response.text());
  }

  await page.setContent([
    '<style>', css.join('\n'), '</style>',
    '<body class="settings-page">',
    '<div class="shell">',
    '<header class="topbar">',
    '<div><div class="brand">RECEPVOZ</div><div class="subtitle">Recepcionista IA</div></div>',
    '<nav id="primaryNav" class="app-nav" aria-label="Navegación principal">',
    '<a class="active" aria-current="page" href="#home">Inicio</a>',
    '<a href="#conversations">Conversaciones</a>',
    '<a href="#bookings">Agenda</a>',
    '<a href="#inventory">Inventario</a>',
    '<a href="#settings">Configuración</a>',
    '</nav>',
    '<div class="top-actions"><span class="badge online">Operativo</span></div>',
    '</header>',
    '<main id="dashboardView">',
    '<section class="dashboard-heading"><div><p class="eyebrow">HOY</p><h1>Inicio</h1><p class="muted">Resumen de tu negocio en tiempo real</p></div></section>',
    '<section class="card"><button class="button primary">Nueva cita</button><span class="ai-chip">IA</span></section>',
    '</main></div></body>'
  ].join(''));
}

test('final visual authority uses cyan for product actions and violet only for AI accents', async () => {
  const css = read('frontend-foundation.css');

  expect(css).toContain('--rv-accent: #16d9f5');
  expect(css).toContain('--rv-ai-accent: #8b5cf6');
  expect(css).toContain('--rv-sidebar-width: 220px');
  expect(css).not.toMatch(/(?:linear|radial)-gradient/i);
});

test('authenticated desktop shell reads as a persistent left rail with a cyan active state', async ({ page, request }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mountAuthenticatedShell(page, request);

  const layout = await page.evaluate(() => {
    const nav = document.querySelector('#primaryNav');
    const main = document.querySelector('#dashboardView');
    const active = document.querySelector('#primaryNav a.active');
    const primary = document.querySelector('.button.primary');
    const ai = document.querySelector('.ai-chip');
    const root = getComputedStyle(document.documentElement);
    const navRect = nav.getBoundingClientRect();
    const mainRect = main.getBoundingClientRect();
    return {
      sidebarToken: root.getPropertyValue('--rv-sidebar-width').trim(),
      navPosition: getComputedStyle(nav).position,
      navWidth: navRect.width,
      navLeft: navRect.left,
      navRight: navRect.right,
      mainLeft: mainRect.left,
      activeBackground: getComputedStyle(active).backgroundColor,
      primaryBackground: getComputedStyle(primary).backgroundColor,
      aiBackground: getComputedStyle(ai).backgroundColor
    };
  });

  expect(layout.sidebarToken).toBe('220px');
  expect(layout.navPosition).toBe('fixed');
  expect(layout.navWidth).toBeGreaterThanOrEqual(216);
  expect(layout.navWidth).toBeLessThanOrEqual(224);
  expect(layout.navLeft).toBeLessThanOrEqual(28);
  expect(layout.mainLeft).toBeGreaterThan(layout.navRight + 24);
  expect(layout.activeBackground).not.toBe('rgba(0, 0, 0, 0)');
  expect(layout.primaryBackground).toBe('rgb(22, 217, 245)');
  expect(layout.aiBackground).not.toBe(layout.primaryBackground);
});

test('compact shell releases the fixed rail and keeps touch navigation usable', async ({ page, request }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mountAuthenticatedShell(page, request);

  const layout = await page.evaluate(() => {
    const nav = document.querySelector('#primaryNav');
    const firstLink = nav.querySelector('a');
    return {
      position: getComputedStyle(nav).position,
      navWidth: nav.getBoundingClientRect().width,
      pageWidth: document.documentElement.scrollWidth,
      viewportWidth: document.documentElement.clientWidth,
      linkHeight: firstLink.getBoundingClientRect().height
    };
  });

  expect(layout.position).not.toBe('fixed');
  expect(layout.navWidth).toBeLessThanOrEqual(390);
  expect(layout.pageWidth).toBeLessThanOrEqual(layout.viewportWidth + 1);
  expect(layout.linkHeight).toBeGreaterThanOrEqual(42);
});
