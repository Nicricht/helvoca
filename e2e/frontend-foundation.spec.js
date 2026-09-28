const { test, expect } = require('@playwright/test');

function stylesheetPaths(html) {
  return [...html.matchAll(/<link[^>]+rel=["']stylesheet["'][^>]+href=["']([^"']+)["'][^>]*>/gi)]
    .map(match => new URL(match[1], 'http://127.0.0.1:4173').pathname);
}

function luminance(cssColor) {
  const values = (cssColor.match(/[\d.]+/g) || []).slice(0, 3).map(Number);
  if (values.length !== 3) return 255;
  return values.reduce((sum, value) => sum + value, 0) / 3;
}

async function getText(request, path) {
  const response = await request.get(path);
  expect(response.ok(), path + ' should be publicly served').toBeTruthy();
  return response.text();
}

async function mountFoundation(page, request) {
  const styles = await Promise.all([
    getText(request, '/styles.css'),
    getText(request, '/first-user-ux-v2.css'),
    getText(request, '/commercial-ui-v3.css'),
    getText(request, '/frontend-foundation.css')
  ]);

  await page.setContent([
    '<style>', styles.join('\n'), '</style>',
    '<body class="settings-page">',
    '<div class="shell">',
    '<header class="topbar">',
    '<div><div class="brand">RECEPVOZ</div><div class="subtitle">Operación del negocio</div></div>',
    '<nav id="primaryNav" class="app-nav" aria-label="Navegación principal">',
    '<a class="active" href="#home">Inicio</a><a href="#inventory">Inventario</a><a href="#settings">Configuración</a>',
    '</nav>',
    '<div class="top-actions"><span class="badge online">Operativo</span></div>',
    '</header>',
    '<main id="dashboardView">',
    '<div class="dashboard-heading"><div><p class="eyebrow">NEGOCIO</p><h1>Ferretería Central</h1><p class="muted">Estado actual</p></div></div>',
    '<section class="card wide-card">',
    '<label>Nombre<input id="focusTarget" value="Ferretería Central"></label>',
    '<label>Idioma<select id="selectTarget"><option>Español</option></select></label>',
    '<button class="button primary" type="button">Guardar cambios</button>',
    '<button id="disabledTarget" class="button ghost" type="button" disabled>No disponible</button>',
    '<div class="inventory-table-wrap"><table class="inventory-table"><thead><tr><th>Producto</th><th>Estado</th></tr></thead>',
    '<tbody><tr><td>Taladro</td><td><span class="badge warning">Stock bajo</span></td></tr></tbody></table></div>',
    '</section>',
    '<dialog open class="inventory-dialog"><div><strong>Editar producto</strong></div></dialog>',
    '</main></div></body>'
  ].join(''));
}

test('canonical dark foundation is served and loaded after legacy styles', async ({ request }) => {
  const foundation = await getText(request, '/frontend-foundation.css');
  expect(foundation).toContain('--rv-bg-canvas: #070a10');
  expect(foundation).toContain('--rv-accent: #806bff');

  for (const path of [
    '/', '/settings.html', '/inventory.html', '/simulator.html', '/operations.html',
    '/platform.html', '/invite.html', '/phone-numbers.html',
    '/privacy.html', '/terms.html', '/data-deletion.html',
    '/sales.html', '/pricing.html'
  ]) {
    const html = await getText(request, path);
    const stylesheets = stylesheetPaths(html);
    expect(stylesheets, path).toContain('/frontend-foundation.css');
    expect(stylesheets.at(-1), path + ' should load the canonical layer last').toBe('/frontend-foundation.css');
  }

  const simulator = await getText(request, '/simulator.html');
  expect(simulator).toContain('No crea datos comerciales reales ni realiza llamadas telefónicas.');
});

test('canonical foundation owns dark surfaces and interaction states', async ({ page, request }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mountFoundation(page, request);

  const computed = await page.evaluate(() => {
    const style = selector => getComputedStyle(document.querySelector(selector));
    const root = getComputedStyle(document.documentElement);
    const body = style('body');
    const card = style('.card');
    const input = style('#focusTarget');
    const disabled = style('#disabledTarget');
    return {
      canvasToken: root.getPropertyValue('--rv-bg-canvas').trim(),
      accentToken: root.getPropertyValue('--rv-accent').trim(),
      body: body.backgroundColor,
      bodyText: body.color,
      card: card.backgroundColor,
      input: input.backgroundColor,
      disabledOpacity: Number.parseFloat(disabled.opacity),
      disabledCursor: disabled.cursor
    };
  });

  expect(computed.canvasToken).toBe('#070a10');
  expect(computed.accentToken).toBe('#806bff');
  expect(luminance(computed.body)).toBeLessThan(55);
  expect(luminance(computed.card)).toBeLessThan(70);
  expect(luminance(computed.input)).toBeLessThan(65);
  expect(luminance(computed.bodyText)).toBeGreaterThan(190);
  expect(computed.disabledOpacity).toBeLessThan(0.7);
  expect(computed.disabledCursor).toBe('not-allowed');

  const input = page.locator('#focusTarget');
  await input.focus();
  const focus = await input.evaluate(element => {
    const style = getComputedStyle(element);
    return { outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth, boxShadow: style.boxShadow };
  });
  expect(focus.outlineStyle !== 'none' || focus.boxShadow !== 'none').toBeTruthy();
  expect(Number.parseFloat(focus.outlineWidth) > 0 || focus.boxShadow !== 'none').toBeTruthy();
});

test('foundation contains tables dialogs and navigation inside compact viewport', async ({ page, request }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mountFoundation(page, request);

  const layout = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    dialog: document.querySelector('dialog').getBoundingClientRect(),
    nav: document.querySelector('#primaryNav').getBoundingClientRect()
  }));

  expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1);
  expect(layout.dialog.left).toBeGreaterThanOrEqual(0);
  expect(layout.dialog.right).toBeLessThanOrEqual(390);
  expect(layout.nav.width).toBeLessThanOrEqual(390);
});
