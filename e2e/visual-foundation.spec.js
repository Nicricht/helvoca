const { test, expect } = require('@playwright/test');

function rgbLuminance(cssColor) {
  const values = (cssColor.match(/[\d.]+/g) || []).slice(0, 3).map(Number);
  if (values.length !== 3) return 255;
  return values.reduce((sum, value) => sum + value, 0) / 3;
}

async function foundationCss(request) {
  const paths = ['/styles.css', '/first-user-ux-v2.css', '/commercial-ui-v3.css'];
  const chunks = [];
  for (const path of paths) {
    const response = await request.get(path);
    expect(response.ok(), path + ' should load').toBeTruthy();
    chunks.push(await response.text());
  }
  return chunks.join('\n');
}

async function mountFoundation(page, request) {
  const css = await foundationCss(request);
  const markup = [
    '<style>', css, '</style>',
    '<body class="settings-page"><div class="shell"><header class="topbar">',
    '<div><div class="brand">RECEPVOZ</div><div class="subtitle">Operación del negocio</div></div>',
    '<nav id="primaryNav" class="app-nav"><a class="active" href="#">Inicio</a><a href="#">Conversaciones</a><a href="#">Inventario</a><a href="#">Configuración</a></nav>',
    '<div class="top-actions"><span class="badge online">Operativo</span></div></header>',
    '<main id="dashboardView"><div class="dashboard-heading"><div><h1>Negocio de prueba</h1><p>Estado actual</p></div></div>',
    '<section class="card wide-card"><label>Nombre<input id="focusTarget" value="Ferretería Central"></label>',
    '<div class="inventory-table-wrap"><table class="inventory-table"><thead><tr><th>Producto</th><th>Estado</th></tr></thead>',
    '<tbody><tr><td>Taladro</td><td><span class="badge warning">Stock bajo</span></td></tr></tbody></table></div>',
    '</section></main></div></body>'
  ].join('');
  await page.setContent(markup);
}

test('shared console foundation is dark-first with accessible focus states', async ({ page, request }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mountFoundation(page, request);
  const colors = await page.evaluate(() => {
    const body = getComputedStyle(document.body);
    const input = getComputedStyle(document.querySelector('#focusTarget'));
    const heading = getComputedStyle(document.querySelector('h1'));
    return { body: body.backgroundColor, input: input.backgroundColor, text: heading.color, colorScheme: getComputedStyle(document.documentElement).colorScheme };
  });
  expect(colors.colorScheme).toContain('dark');
  expect(rgbLuminance(colors.body)).toBeLessThan(55);
  expect(rgbLuminance(colors.input)).toBeLessThan(55);
  expect(rgbLuminance(colors.text)).toBeGreaterThan(190);
  const input = page.locator('#focusTarget');
  await input.focus();
  const focus = await input.evaluate(element => {
    const style = getComputedStyle(element);
    return { outline: style.outlineStyle, shadow: style.boxShadow };
  });
  expect(focus.outline !== 'none' || focus.shadow !== 'none').toBeTruthy();
});

test('desktop navigation stays in its rail and mobile layout has no page overflow', async ({ page, request }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mountFoundation(page, request);
  const nav = await page.locator('#primaryNav').boundingBox();
  const heading = await page.locator('.dashboard-heading').boundingBox();
  expect(nav).not.toBeNull();
  expect(heading).not.toBeNull();
  expect(nav.x).toBeLessThanOrEqual(32);
  expect(heading.x).toBeGreaterThan(nav.x + nav.width + 24);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  await expect(page.locator('#primaryNav')).toBeVisible();
});
