const { test, expect } = require('@playwright/test');

function stylesheetPaths(html) {
  return [...html.matchAll(/<link[^>]+rel=["']stylesheet["'][^>]+href=["']([^"']+)["'][^>]*>/gi)]
    .map(match => new URL(match[1], 'http://127.0.0.1:4173').pathname);
}

function rgbValues(cssColor) {
  return (cssColor.match(/[\d.]+/g) || []).slice(0, 3).map(Number);
}

function simpleLuminance(cssColor) {
  const values = rgbValues(cssColor);
  if (values.length !== 3) return 255;
  return values.reduce((sum, value) => sum + value, 0) / 3;
}

function relativeLuminance(cssColor) {
  const values = rgbValues(cssColor);
  if (values.length !== 3) return 1;
  const linear = values.map(value => {
    const channel = value / 255;
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return (0.2126 * linear[0]) + (0.7152 * linear[1]) + (0.0722 * linear[2]);
}

function contrastRatio(foreground, background) {
  const first = relativeLuminance(foreground);
  const second = relativeLuminance(background);
  const lighter = Math.max(first, second);
  const darker = Math.min(first, second);
  return (lighter + 0.05) / (darker + 0.05);
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
  expect(foundation).toContain('--rv-bg-canvas: #06111c');
  expect(foundation).toContain('--rv-accent: #16d9f5');

  for (const path of [
    '/', '/simulator.html', '/operations.html',
    '/platform.html', '/invite.html', '/phone-numbers.html',
    '/privacy.html', '/terms.html', '/data-deletion.html',
    '/sales.html', '/pricing.html'
  ]) {
    const html = await getText(request, path);
    const stylesheets = stylesheetPaths(html);
    expect(html, path + ' should not contain escaped newline artifacts').not.toContain('\\n</head>');
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
      mutedText: style('.muted').color,
      disabledOpacity: Number.parseFloat(disabled.opacity),
      disabledCursor: disabled.cursor
    };
  });

  expect(computed.canvasToken).toBe('#06111c');
  expect(computed.accentToken).toBe('#16d9f5');
  expect(computed.body).not.toBe('rgba(0, 0, 0, 0)');
  expect(computed.card).not.toBe('rgba(0, 0, 0, 0)');
  expect(computed.input).not.toBe('rgba(0, 0, 0, 0)');
  expect(simpleLuminance(computed.body)).toBeLessThan(55);
  expect(simpleLuminance(computed.card)).toBeLessThan(70);
  expect(simpleLuminance(computed.input)).toBeLessThan(65);
  expect(simpleLuminance(computed.bodyText)).toBeGreaterThan(190);
  expect(contrastRatio(computed.bodyText, computed.body)).toBeGreaterThanOrEqual(4.5);
  expect(contrastRatio(computed.mutedText, computed.card)).toBeGreaterThanOrEqual(4.5);
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

  const layout = await page.evaluate(() => {
    const dialog = document.querySelector('dialog').getBoundingClientRect();
    const nav = document.querySelector('#primaryNav').getBoundingClientRect();
    return {
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
      dialog: { left: dialog.left, right: dialog.right, width: dialog.width },
      nav: { left: nav.left, right: nav.right, width: nav.width }
    };
  });

  expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1);
  expect(layout.dialog.left).toBeGreaterThanOrEqual(0);
  expect(layout.dialog.right).toBeLessThanOrEqual(390);
  expect(layout.nav.width).toBeLessThanOrEqual(390);
});
