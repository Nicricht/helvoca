const { test, expect } = require('./inventory-coverage-fixture');
const { INVENTORY_VIEWPORTS, captureInventoryVisual } = require('./inventory-visual-evidence-helper');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootInventory(page, options = {}) {
  await page.addInitScript(token => sessionStorage.setItem('helvoca_access_token', token), options.token || 'inventory-react-e2e');

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) {
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'expired' })
      });
    }
    return route.fulfill(json({
      email: 'owner@negocio.cl',
      roles: options.roles || ['BUSINESS_OWNER']
    }));
  });

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Taller Norte'
  })));

  await page.route('**/api/v1/catalog', route => {
    if (options.catalogError) {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'catalog unavailable' })
      });
    }
    return route.fulfill(json([
      {
        id: 'prod-1',
        kind: 'PRODUCT',
        name: 'Taladro percutor',
        description: 'Taladro 18V',
        price: 54990,
        currency: 'CLP',
        active: true
      },
      {
        id: 'prod-2',
        kind: 'PRODUCT',
        name: 'Broca metal 8 mm',
        description: 'Broca HSS',
        price: 4990,
        currency: 'CLP',
        active: true
      },
      {
        id: 'svc-1',
        kind: 'SERVICE',
        name: 'Instalación',
        active: true
      }
    ]));
  });

  await page.route('**/api/v1/catalog/*/media', route => {
    const id = route.request().url().split('/').at(-2);
    return route.fulfill(json(options.mediaByProduct?.[id] ?? []));
  });

  await page.route('**/api/v1/inventory', route => route.fulfill(json([
    {
      id: 'stock-1',
      catalogItemId: 'prod-1',
      sku: 'TAL-18V',
      trackingEnabled: true,
      onHand: 8,
      reserved: 3,
      available: 5,
      reorderThreshold: 4,
      lowStock: false
    },
    {
      id: 'stock-2',
      catalogItemId: 'prod-2',
      sku: 'BRO-8MM',
      trackingEnabled: true,
      onHand: 2,
      reserved: 1,
      available: 1,
      reorderThreshold: 2,
      lowStock: true
    }
  ])));

  await page.route('**/api/v1/inventory/prod-1/movements', route => route.fulfill(json([
    {
      id: 'move-2',
      type: 'ADJUSTMENT',
      quantityDelta: 3,
      reservedDelta: 0,
      onHandAfter: 8,
      reservedAfter: 3,
      referenceType: 'MANUAL',
      referenceId: null,
      note: 'Reposición bodega',
      createdAt: '2026-10-02T12:30:00Z'
    },
    {
      id: 'move-1',
      type: 'RESERVATION',
      quantityDelta: 0,
      reservedDelta: 2,
      onHandAfter: 5,
      reservedAfter: 3,
      referenceType: 'ORDER_OPERATION',
      referenceId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      note: 'Reserva pedido',
      createdAt: '2026-10-02T11:00:00Z'
    }
  ])));

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([
    {
      id: 'alert-1',
      catalogItemId: 'prod-2',
      type: 'LOW_STOCK',
      acknowledged: false
    }
  ])));

  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));
}

test.describe('React Inventory migration', () => {
  test('keeps the official robot in a compact operational lead with the product table above the desktop fold', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await bootInventory(page);
    await page.goto('/app/inventory');

    const intro = page.getByTestId('inventory-intro');
    await expect(intro).toBeVisible();
    await expect(intro).toContainText('2 de 2 productos con stock controlado');
    await expect(intro.locator('img[src="/app/assets/recepvoz/v2/inventory/hero-stock-robot.webp"]')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'Inventario' })).toHaveCount(1);

    const height = await intro.evaluate(element => element.getBoundingClientRect().height);
    expect(height).toBeLessThanOrEqual(210);

    const tableTop = await page.locator('table').first().evaluate(element => element.getBoundingClientRect().top);
    expect(tableTop).toBeLessThan(900);

    const importTop = await page.getByRole('link', { name: 'Importar archivos' })
      .evaluate(element => element.getBoundingClientRect().top);
    const createTop = await page.getByRole('button', { name: 'Nuevo producto' })
      .evaluate(element => element.getBoundingClientRect().top);
    const refreshTop = await page.getByRole('button', { name: 'Actualizar', exact: true })
      .evaluate(element => element.getBoundingClientRect().top);
    expect(Math.abs(importTop - createTop)).toBeLessThan(8);
    expect(Math.abs(createTop - refreshTop)).toBeLessThan(8);

    await expect(page.getByTestId('inventory-available')).toContainText('6');
    await expect(page.getByTestId('inventory-reserved')).toContainText('4');
    await expect(page.getByTestId('inventory-row-prod-2')).toContainText('Stock bajo');
  });

  test('distinguishes an unconfigured product from exhausted stock in the compact header', async ({ page }) => {
    await bootInventory(page);
    await page.route('**/api/v1/inventory', route => route.fulfill(json([{
      id: 'stock-1',
      catalogItemId: 'prod-1',
      sku: 'TAL-18V',
      trackingEnabled: true,
      onHand: 8,
      reserved: 3,
      available: 5,
      reorderThreshold: 4,
      lowStock: false
    }])));
    await page.goto('/app/inventory');
    const intro = page.getByTestId('inventory-intro');
    await expect(intro).toContainText('1 de 2 productos con stock controlado');
    await expect(intro.locator('[data-tone="warning"]')).toHaveCount(1);
    await expect(page.getByTestId('inventory-available')).toContainText('5');
    await expect(page.getByRole('button', { name: 'Ver sin configurar' })).toBeVisible();
    await page.getByRole('button', { name: 'Ver sin configurar' }).click();
    await expect(page.getByTestId('inventory-row-prod-2')).toContainText('Sin configurar');
    await expect(page.getByTestId('inventory-row-prod-1')).toHaveCount(0);
  });

  test('preserves the compact lead, all four metrics and no page overflow on a mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await bootInventory(page);
    await page.goto('/app/inventory');

    await expect(page.getByTestId('inventory-intro')).toBeVisible();
    await expect(page.getByTestId('inventory-products')).toBeVisible();
    await expect(page.getByTestId('inventory-available')).toBeVisible();
    await expect(page.getByTestId('inventory-reserved')).toBeVisible();
    await expect(page.getByTestId('inventory-low-stock')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
  test('uses real catalog price and media in the table and inspector', async ({ page }) => {
    await bootInventory(page, {
      roles: ['BUSINESS_ADMIN'],
      mediaByProduct: {
        'prod-1': [{
          id: 'media-1', catalogItemId: 'prod-1', mediaType: 'IMAGE',
          mediaUrl: 'https://assets.example.test/product-one.png', sortOrder: 0, active: true
        }],
        'prod-2': []
      }
    });
    await page.route('https://assets.example.test/**', route => route.fulfill({
      status: 200, contentType: 'image/png',
      body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO9FI1EAAAAASUVORK5CYII=', 'base64')
    }));
    await page.goto('/app/inventory');

    const first = page.getByTestId('inventory-row-prod-1');
    await expect(first).toContainText('54.990');
    await expect(first.locator('img[src="https://assets.example.test/product-one.png"]')).toBeVisible();
    const second = page.getByTestId('inventory-row-prod-2');
    await expect(second).toContainText('4.990');
    await expect(second.locator('img')).toHaveCount(0);

    await first.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor' });
    await expect(inspector).toContainText('54.990');
    await expect(inspector).toContainText('SKU: TAL-18V');
    await expect(inspector).toContainText('Disponible');
    await expect(inspector).toContainText('Reservado');
    await expect(inspector).toContainText('Físico');
    await expect(inspector.locator('img[src="https://assets.example.test/product-one.png"]')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(inspector).toHaveCount(0);
  });

  test('inspector contains keyboard tab focus and restores focus on close', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.goto('/app/inventory');
    const trigger = page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' });
    await trigger.click();

    const inspector = page.getByRole('dialog', { name: 'Taladro percutor' });
    const close = inspector.getByRole('button', { name: 'Cerrar detalles' });
    await expect(close).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(inspector.getByRole('button', { name: 'Ajustar stock' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(close).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(inspector).toHaveCount(0);
    await expect(trigger).toBeFocused();
  });

  test('absent catalog price and unauthorized media read cannot fabricate product data', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_OWNER'] });
    await page.route('**/api/v1/catalog', route => route.fulfill(json([
      { id: 'prod-1', kind: 'PRODUCT', name: 'Taladro percutor', price: null, currency: 'CLP', active: true }
    ])));
    let mediaRequests = 0;
    await page.route('**/api/v1/catalog/*/media', route => {
      mediaRequests++;
      return route.fulfill(json([]));
    });
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-prod-1');
    await expect(row).toContainText('Sin precio');
    await expect(row.locator('img')).toHaveCount(0);
    await row.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    await expect(page.getByRole('dialog', { name: 'Taladro percutor' }))
      .toContainText('Sin precio');
    expect(mediaRequests).toBe(0);
  });

  test('read-only operator can inspect and review history without inventory mutation controls', async ({ page }) => {
    await bootInventory(page, { roles: ['OPERATOR'] });
    await page.goto('/app/inventory');

    await page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor' });
    await expect(inspector.getByRole('button', { name: 'Ver historial' })).toBeVisible();
    await expect(inspector.getByRole('button', { name: 'Editar stock' })).toHaveCount(0);
    await expect(inspector.getByRole('button', { name: 'Configurar stock' })).toHaveCount(0);
    await expect(inspector.getByRole('button', { name: 'Ajustar stock' })).toHaveCount(0);
    await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
    await expect(inspector).toHaveCount(0);
  });

  test('local pagination bounds media reads and resets after a filtered search', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    const extra = Array.from({ length: 18 }, (_, index) => ({
      id: `extra-${index}`, kind: 'PRODUCT',
      name: `Producto extra ${String(index).padStart(2, '0')}`,
      description: '', price: index, currency: 'CLP', active: true
    }));
    await page.route('**/api/v1/catalog', route => route.fulfill(json(extra)));
    let mediaRequests = 0;
    await page.route('**/api/v1/catalog/*/media', route => {
      mediaRequests++;
      return route.fulfill(json([]));
    });
    await page.goto('/app/inventory');

    const pagination = page.getByRole('navigation', { name: 'Paginación de productos' });
    await expect(pagination).toContainText('Página 1 de 3');
    await expect(page.locator('[data-testid^="inventory-row-extra-"]')).toHaveCount(8);
    await expect.poll(() => mediaRequests).toBe(8);
    await page.getByRole('button', { name: 'Página siguiente' }).click();
    await expect(pagination).toContainText('Página 2 de 3');
    await expect(page.locator('[data-testid^="inventory-row-extra-"]')).toHaveCount(8);
    await expect.poll(() => mediaRequests).toBe(16);
    await page.getByRole('button', { name: 'Página anterior' }).click();
    await expect(pagination).toContainText('Página 1 de 3');
    await expect(page.getByTestId('inventory-row-extra-0')).toContainText('Producto extra 00');
    await page.getByRole('button', { name: 'Página siguiente' }).click();
    await expect(pagination).toContainText('Página 2 de 3');
    await page.getByRole('searchbox', { name: 'Buscar productos' }).fill('Producto extra 17');
    await expect(page.getByTestId('inventory-row-extra-17')).toBeVisible();
    await expect(pagination).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });

  test('renders authoritative stock without confusing physical, reserved and available', async ({ page }) => {
    await bootInventory(page);
    await page.goto('/app/inventory');

    await expect(page.getByRole('heading', { level: 1, name: 'Inventario' })).toBeVisible();
    await expect(page.getByTestId('inventory-products')).toContainText('2');
    await expect(page.getByTestId('inventory-available')).toContainText('6');
    await expect(page.getByTestId('inventory-reserved')).toContainText('4');
    await expect(page.getByTestId('inventory-low-stock')).toContainText('1');

    const taladro = page.getByTestId('inventory-row-prod-1');
    await expect(taladro).toContainText('Taladro percutor');
    await expect(taladro).toContainText('5');
    await expect(taladro).toContainText('3');
    await expect(taladro).toContainText('8');

    const broca = page.getByTestId('inventory-row-prod-2');
    await expect(broca).toContainText('Stock bajo');
  });

  test('keeps search, filtering and sorting local to the inventory workspace', async ({ page }) => {
    await bootInventory(page);
    await page.goto('/app/inventory');

    await page.getByRole('searchbox', { name: 'Buscar productos' }).fill('Broca');
    await expect(page.getByTestId('inventory-row-prod-2')).toBeVisible();
    await expect(page.getByTestId('inventory-row-prod-1')).toHaveCount(0);

    await page.getByRole('searchbox', { name: 'Buscar productos' }).fill('');
    await page.getByLabel('Estado').selectOption('LOW');
    await expect(page.getByTestId('inventory-row-prod-2')).toBeVisible();
    await expect(page.getByTestId('inventory-row-prod-1')).toHaveCount(0);
  });

  test('stays useful when catalog data fails instead of inventing stock data', async ({ page }) => {
    await bootInventory(page, { catalogError: true });
    await page.goto('/app/inventory');

    await expect(page.getByRole('alert')).toContainText('No pudimos cargar');
    await expect(page.getByTestId('inventory-available')).not.toContainText('6');
    await expect(page.getByRole('button', { name: /reintentar/i })).toBeVisible();
  });

  test('shows authoritative movement history to a read-only operator', async ({ page }) => {
    await bootInventory(page, { roles: ['OPERATOR'] });
    await page.goto('/app/inventory');

    const row = page.getByTestId('inventory-row-prod-1');
    await row.getByRole('button', { name: 'Ver historial' }).click();

    const dialog = page.getByRole('dialog', { name: 'Historial · Taladro percutor' });
    await expect(dialog).toBeVisible();

    const adjustment = dialog.getByTestId('inventory-movement-move-2');
    await expect(adjustment).toContainText('Ajuste manual');
    await expect(adjustment).toContainText('+3');
    await expect(adjustment).toContainText('Físico después: 8');
    await expect(adjustment).toContainText('Reservado después: 3');
    await expect(adjustment).toContainText('Reposición bodega');
    await expect(adjustment.getByTestId('movement-created-at')).toHaveAttribute('datetime', '2026-10-02T12:30:00Z');

    const reservation = dialog.getByTestId('inventory-movement-move-1');
    await expect(reservation).toContainText('Reserva');
    await expect(reservation).toContainText('Reservado +2');
    await expect(reservation).toContainText('Reserva pedido');

    await expect(page.getByRole('button', { name: /ajustar stock/i })).toHaveCount(0);
  });

  test('applies an explicit stock adjustment through the React workspace', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });

    let adjustmentPayload = null;
    await page.route('**/api/v1/inventory/prod-1/adjustments', async route => {
      adjustmentPayload = route.request().postDataJSON();
      await route.fulfill(json({
        id: 'stock-1',
        catalogItemId: 'prod-1',
        sku: 'TAL-18V',
        trackingEnabled: true,
        onHand: 12,
        reserved: 3,
        available: 9,
        reorderThreshold: 4,
        lowStock: false
      }));
    });

    await page.goto('/app/inventory');

    const row = page.getByTestId('inventory-row-prod-1');
    await row.getByRole('button', { name: 'Ajustar stock' }).click();

    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Taladro percutor' });
    await dialog.getByLabel('Ajuste').fill('4');
    await dialog.getByLabel('Nota').fill('Reposición manual');
    await dialog.getByRole('button', { name: 'Aplicar ajuste' }).click();

    await expect.poll(() => adjustmentPayload).toEqual({
      delta: 4,
      referenceType: 'MANUAL',
      referenceId: null,
      note: 'Reposición manual'
    });
    await expect(dialog).toHaveCount(0);
  });

  test('does not expose manage actions to a restricted role', async ({ page }) => {
    await bootInventory(page, { roles: ['OPERATOR'] });
    await page.goto('/app/inventory');

    await expect(page.getByRole('heading', { level: 1, name: 'Inventario' })).toBeVisible();
    await expect(page.getByRole('button', { name: /nuevo producto/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /ajustar stock/i })).toHaveCount(0);
  });

  test('redirects to auth on expired session', async ({ page }) => {
    await bootInventory(page, { expired: true });
    await page.goto('/app/inventory');

    await expect(page).toHaveURL(/\/app\/auth\/?$/);
  });

  test('shows no invented values in responsive loading skeleton and respects reduced-motion', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootInventory(page);
    await page.route('**/api/v1/catalog', async route => {
      await new Promise(resolve => setTimeout(resolve, 1200));
      return route.fallback();
    });

    await page.goto('/app/inventory');
    const loading = page.getByTestId('inventory-loading');
    const skeleton = page.getByTestId('inventory-loading-skeleton');
    await expect(loading).toBeVisible();
    await expect(skeleton.locator(':scope > span')).toHaveCount(5);
    await expect(page.getByTestId('inventory-products')).toHaveCount(0);
    const animation = await skeleton.locator(':scope > span').first()
      .evaluate(element => getComputedStyle(element).animationName);
    expect(animation).toBe('none');
    expect(await page.evaluate(() =>
      document.documentElement.scrollWidth <= document.documentElement.clientWidth
    )).toBe(true);
    await expect(loading).toHaveCount(0);
    await expect(page.getByTestId('inventory-products')).toContainText('2');
  });

  test('secondary source outage never represents unavailable alert and waiting counts as zero', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let calls = 0;
    await page.route('**/api/v1/inventory/alerts', route => {
      calls++;
      if (calls === 1) return route.fulfill({
        status: 503, contentType: 'application/json',
        body: JSON.stringify({ message: 'alerts unavailable' })
      });
      return route.fulfill(json([]));
    });
    await page.goto('/app/inventory');

    const degraded = page.getByTestId('inventory-partial-error');
    await expect(degraded).toBeVisible();
    await expect(page.getByTestId('inventory-products')).toContainText('2');
    await expect(page.getByTestId('inventory-available')).toContainText('6');
    await expect(page.getByLabel('Resumen de reposición')).toContainText('— alertas (sin datos)');
    await expect(page.getByText('Alertas no disponibles. Reintenta la consulta.')).toBeVisible();
    await expect(page.getByText('No hay alertas abiertas.')).toHaveCount(0);
    await degraded.getByRole('button', { name: 'Reintentar consultas' }).click();
    await expect.poll(() => calls).toBeGreaterThan(1);
    await expect(degraded).toHaveCount(0);
    await expect(page.getByLabel('Resumen de reposición')).toContainText('0 alertas');
    await expect(page.getByText('No hay alertas abiertas.')).toBeVisible();
    await expect(page.getByTestId('inventory-last-sync')).toContainText('Última consulta');
  });

  test('all secondary data errors keep stock usable but do not invent empty waiting or delivery queues', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    const unavailable = route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ message: 'temporarily unavailable' })
    });
    await page.route('**/api/v1/inventory/restock-subscriptions', unavailable);
    await page.route('**/api/v1/inventory/restock-subscriptions/notifications', unavailable);
    await page.goto('/app/inventory');

    await expect(page.getByTestId('inventory-partial-error')).toBeVisible();
    await expect(page.getByTestId('inventory-reserved')).toContainText('4');
    await expect(page.getByLabel('Resumen de reposición')).toContainText('— esperando (sin datos)');
    await expect(page.getByText('Lista de espera no disponible. Reintenta la consulta.')).toBeVisible();
    await expect(page.getByText('Avisos no disponibles. Reintenta la consulta.')).toBeVisible();
    await expect(page.getByText('Nadie está esperando reposición.')).toHaveCount(0);
    await expect(page.getByText('No hay avisos pendientes.')).toHaveCount(0);
  });

  test('reduced-motion preference stops inspector entrance animation without hiding its actions', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.goto('/app/inventory');

    await page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    const inspector = page.getByTestId('inventory-inspector');
    await expect(inspector).toBeVisible();
    await expect(inspector.getByRole('button', { name: 'Ajustar stock' })).toBeVisible();
    expect(await inspector.evaluate(el => getComputedStyle(el).animationName)).toBe('none');
    await page.keyboard.press('Escape');
    await expect(inspector).toHaveCount(0);
  });

  test('stays contained at desktop tablet and mobile widths', async ({ page }) => {
    await bootInventory(page);

    for (const viewport of [
      { width: 1536, height: 950 },
      { width: 1440, height: 900 },
      { width: 1366, height: 768 },
      { width: 1280, height: 720 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/inventory');
      await expect(page.getByRole('heading', { level: 1, name: 'Inventario' })).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth
      )).toBe(true);
    }
  });

  test('captures exact-head inspector and degraded states at six canonical viewports', async ({ page }) => {
    test.setTimeout(180000);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });

    for (const viewport of INVENTORY_VIEWPORTS) {
      await page.setViewportSize(viewport);
      await page.goto('/app/inventory');
      const row = page.getByTestId('inventory-row-prod-1');
      await expect(row).toBeVisible();
      await row.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
      const inspector = page.getByRole('dialog', { name: 'Taladro percutor' });
      await expect(inspector).toBeVisible();
      await expect(inspector).toContainText('Reservado');
      await captureInventoryVisual(page, 'inspector');
      await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
      await expect(inspector).toHaveCount(0);

      const alertsUnavailable = route => route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'alerts unavailable' })
      });
      await page.route('**/api/v1/inventory/alerts', alertsUnavailable);
      await page.goto('/app/inventory');
      await expect(page.getByTestId('inventory-partial-error')).toBeVisible();
      await expect(page.getByTestId('inventory-available')).toContainText('6');
      await expect(page.getByLabel('Resumen de reposición')).toContainText('— alertas (sin datos)');
      await captureInventoryVisual(page, 'alerts-unavailable');
      await page.unroute('**/api/v1/inventory/alerts', alertsUnavailable);

      const catalogUnavailable = route => route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'catalog unavailable' })
      });
      await page.route('**/api/v1/catalog', catalogUnavailable);
      await page.goto('/app/inventory');
      await expect(page.getByRole('alert')).toContainText('No pudimos cargar');
      await expect(page.getByTestId('inventory-available')).not.toContainText('6');
      await captureInventoryVisual(page, 'catalog-unavailable');
      await page.unroute('**/api/v1/catalog', catalogUnavailable);
    }
  });


  test('all stock status filters and sort choices apply to the current table only', async ({ page }) => {
    await bootInventory(page);
    await page.goto('/app/inventory');
    const rows = page.locator('tbody tr[data-testid^="inventory-row-"]');
    await expect(rows).toHaveCount(2);
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-2');

    const status = page.getByLabel('Estado');
    await status.selectOption('LOW');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-2');
    await status.selectOption('OUT');
    await expect(rows).toHaveCount(0);
    await expect(page.getByText('No hay productos que coincidan.')).toBeVisible();
    await status.selectOption('TRACKED');
    await expect(rows).toHaveCount(2);
    await status.selectOption('RESTOCKED');
    await expect(rows).toHaveCount(0);
    await status.selectOption('UNCONFIGURED');
    await expect(rows).toHaveCount(0);
    await status.selectOption('ALL');

    const order = page.getByLabel('Orden');
    await order.selectOption('NAME_ASC');
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-2');
    await order.selectOption('AVAILABLE_DESC');
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-1');
    await order.selectOption('AVAILABLE_ASC');
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-2');
    await order.selectOption('ATTENTION');
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-2');
    await expect(page.getByRole('heading', { name: 'Alertas y reposición' })).toHaveCount(0);
  });



  test('failed history fetch presents an error and a later successful reopen recovers', async ({ page }) => {
    await bootInventory(page, { roles: ['OPERATOR'] });
    let fail = true;
    await page.route('**/api/v1/inventory/prod-1/movements', route => {
      if (fail) return route.fulfill({
        status: 503, contentType: 'application/json',
        body: JSON.stringify({ message: 'history temporarily unavailable' })
      });
      return route.fallback();
    });
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-prod-1');
    await row.getByRole('button', { name: 'Ver historial' }).click();
    const history = page.getByRole('dialog', { name: 'Historial · Taladro percutor' });
    await expect(history.getByRole('alert')).toBeVisible();
    await expect(history.getByTestId('inventory-movement-move-2')).toHaveCount(0);
    await history.getByRole('button', { name: 'Cerrar' }).click();
    await expect(history).toHaveCount(0);

    fail = false;
    await row.getByRole('button', { name: 'Ver historial' }).click();
    await expect(history.getByTestId('inventory-movement-move-2')).toContainText('Reposición bodega');
    await expect(history.getByRole('alert')).toHaveCount(0);
  });

  test('zero base-stock delta is rejected client-side without any network mutation', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    const requests = [];
    await page.route('**/api/v1/inventory/prod-1/adjustments', route => {
      requests.push(route.request().postDataJSON());
      return route.fulfill(json({}));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-prod-1').getByRole('button', { name: 'Ajustar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Taladro percutor' });
    await dialog.getByLabel('Ajuste').fill('0');
    await dialog.getByRole('button', { name: 'Aplicar ajuste' }).click();
    await expect(dialog.getByRole('alert')).toContainText('entero distinto de cero');
    expect(requests).toHaveLength(0);
    await dialog.getByRole('button', { name: 'Cancelar' }).click();
    await expect(dialog).toHaveCount(0);
    expect(requests).toHaveLength(0);
  });

  test('inspector actions navigate to variants and history without mutating stock', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    const writes = [];
    await page.route('**/api/v1/inventory/prod-1/variants', route => {
      if (route.request().method() !== 'GET') writes.push(route.request().method());
      return route.fulfill(json([]));
    });
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-prod-1');
    await row.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    let inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await inspector.getByRole('button', { name: 'Variantes' }).click();
    await expect(inspector).toHaveCount(0);
    const variantDialog = page.getByRole('dialog', { name: 'Variantes · Taladro percutor' });
    await expect(variantDialog).toContainText('todavía no tiene variantes');
    await variantDialog.getByRole('button', { name: 'Cerrar', exact: true }).click();
    await expect(variantDialog).toHaveCount(0);

    await row.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await inspector.getByRole('button', { name: 'Ver historial' }).click();
    await expect(inspector).toHaveCount(0);
    const history = page.getByRole('dialog', { name: 'Historial · Taladro percutor' });
    await expect(history.getByTestId('inventory-movement-move-2')).toContainText('Ajuste manual');
    await history.getByRole('button', { name: 'Cerrar' }).click();
    expect(writes).toHaveLength(0);
  });



  test('broken and unsafe catalog images fall back to local icons without retrying media providers', async ({ page }) => {
    const requestedImages = [];
    await bootInventory(page, {
      roles: ['BUSINESS_ADMIN'],
      mediaByProduct: {
        'prod-1': [
          { id: 'inactive', mediaType: 'IMAGE', mediaUrl: 'https://cdn.example.test/inactive.png', active: false },
          { id: 'bad-type', mediaType: 'VIDEO', mediaUrl: 'https://cdn.example.test/clip.mp4', active: true },
          { id: 'valid', mediaType: 'IMAGE', mediaUrl: 'https://cdn.example.test/broken.png', active: true }
        ],
        'prod-2': [
          { id: 'unsafe', mediaType: 'IMAGE', mediaUrl: 'javascript:alert(1)', active: true },
          { id: 'insecure', mediaType: 'IMAGE', mediaUrl: 'http://cdn.example.test/insecure.png', active: true }
        ]
      }
    });
    await page.route('https://cdn.example.test/**', route => {
      requestedImages.push(route.request().url());
      return route.fulfill({ status: 404, body: 'not found' });
    });
    await page.goto('/app/inventory');
    const first = page.getByTestId('inventory-row-prod-1');
    const second = page.getByTestId('inventory-row-prod-2');
    await expect(first).toContainText('54.990');
    // Wait for the one permitted remote image request before asserting fallback.
    // Without this poll, an unloaded image looks identical to a failed image.
    await expect.poll(() => [...requestedImages]).toEqual(['https://cdn.example.test/broken.png']);
    await expect(first.locator('img')).toHaveCount(0);
    await expect(second.locator('img')).toHaveCount(0);
    await first.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await expect(inspector.locator('img')).toHaveCount(0);
    await expect(inspector).toContainText('SKU: TAL-18V');
    await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
    expect(requestedImages).toEqual(['https://cdn.example.test/broken.png']);
  });

  test('product detail distinguishes unconfigured, untracked and exhausted stock without fabricating availability', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let currentStock = [];
    await page.route('**/api/v1/inventory', route => route.fulfill(json(currentStock)));
    const states = [
      {
        stock: [], message: 'Stock sin configurar.',
        available: '—', manage: 'Configurar stock', adjust: false
      },
      {
        stock: [{
          id: 'stock-1', catalogItemId: 'prod-1', trackingEnabled: false,
          onHand: 8, reserved: 3, available: 5, reorderThreshold: 4, lowStock: false
        }], message: 'control de stock está desactivado',
        available: '5', manage: 'Editar stock', adjust: false
      },
      {
        stock: [{
          id: 'stock-1', catalogItemId: 'prod-1', trackingEnabled: true,
          onHand: 5, reserved: 5, available: 0, reorderThreshold: 2, lowStock: false
        }], message: 'Sin unidades disponibles.',
        available: '0', manage: 'Editar stock', adjust: true
      }
    ];
    for (const state of states) {
      currentStock = state.stock;
      await page.goto('/app/inventory');
      await page.getByTestId('inventory-row-prod-1')
        .getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
      const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
      await expect(inspector).toContainText(state.message);
      await expect(inspector.locator('dl')).toContainText(state.available);
      await expect(inspector.getByRole('button', { name: state.manage })).toBeVisible();
      await expect(inspector.getByRole('button', { name: 'Ajustar stock' }))
        .toHaveCount(state.adjust ? 1 : 0);
      await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
      await expect(inspector).toHaveCount(0);
    }
  });

  test('inspector keyboard focus wraps backward and forward and non-escape typing keeps dialog open', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.goto('/app/inventory');
    const trigger = page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' });
    await trigger.click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    const close = inspector.getByRole('button', { name: 'Cerrar detalles' });
    await expect(close).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(inspector.getByRole('button', { name: 'Variantes' })).toBeFocused();
    await page.keyboard.press('a');
    await expect(inspector).toBeVisible();
    await page.keyboard.press('Shift+Tab');
    await expect(close).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(inspector.getByRole('button', { name: 'Ajustar stock' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(close).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(inspector).toHaveCount(0);
    await expect(trigger).toBeFocused();
  });

  test('invalid currency format and non-finite catalog price are never presented as a real price', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/catalog', route => route.fulfill(json([
      { id: 'prod-1', kind: 'PRODUCT', name: 'Taladro percutor',
        price: 'Infinity', currency: 'CLP', active: true },
      { id: 'prod-2', kind: 'PRODUCT', name: 'Broca metal 8 mm',
        price: 4990, currency: 'US', active: true }
    ])));
    await page.goto('/app/inventory');
    const first = page.getByTestId('inventory-row-prod-1');
    const second = page.getByTestId('inventory-row-prod-2');
    await expect(first).toContainText('Sin precio');
    await expect(second).toContainText('Precio no disponible');
    await first.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await expect(inspector).toContainText('Sin precio');
    await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
  });



  test('Intl currency formatter failure never produces a fabricated catalog price', async ({ page }) => {
    await page.addInitScript(() => {
      const RealFormat = Intl.NumberFormat;
      Intl.NumberFormat = function(locale, options) {
        if (options?.style === 'currency' && options.currency === 'XYZ') {
          throw new RangeError('Currency presentation unavailable');
        }
        return new RealFormat(locale, options);
      };
    });
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/catalog', route => route.fulfill(json([
      { id: 'prod-1', kind: 'PRODUCT', name: 'Taladro percutor',
        price: 1200.5, currency: 'XYZ', active: true },
      { id: 'prod-2', kind: 'PRODUCT', name: 'Broca metal 8 mm',
        price: 4990, currency: '', active: true }
    ])));
    await page.goto('/app/inventory');
    await expect(page.getByTestId('inventory-row-prod-1')).toContainText('Precio no disponible');
    await expect(page.getByTestId('inventory-row-prod-2')).toContainText('4.990');
    await page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await expect(inspector).toContainText('Precio no disponible');
    await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
  });

  test('unknown movement type and invalid timestamp remain readable without stock mutation', async ({ page }) => {
    await bootInventory(page, { roles: ['OPERATOR'] });
    const calls = { writes: 0 };
    await page.route('**/api/v1/inventory/prod-1/movements', route => route.fulfill(json([
      {
        id: 'legacy-event-1', type: 'LEGACY_CONSUMPTION',
        quantityDelta: -2, reservedDelta: -1,
        onHandAfter: 5, reservedAfter: 1,
        note: '', createdAt: 'unparseable-time'
      },
      {
        id: 'legacy-event-2', type: 'RELEASE',
        quantityDelta: 0, reservedDelta: -3,
        onHandAfter: 5, reservedAfter: 1,
        note: 'Liberación controlada', createdAt: '2026-10-09T10:00:00Z'
      }
    ])));
    await page.route('**/api/v1/inventory/prod-1/adjustments', route => {
      calls.writes += 1;
      return route.fulfill(json({}));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver historial' }).click();
    const dialog = page.getByRole('dialog', { name: 'Historial · Taladro percutor' });
    const unknown = dialog.getByTestId('inventory-movement-legacy-event-1');
    await expect(unknown).toContainText('Consumo');
    await expect(unknown).toContainText('Físico -2');
    await expect(unknown).toContainText('Reservado -1');
    await expect(unknown).toContainText('unparseable-time');
    await expect(dialog.getByTestId('inventory-movement-legacy-event-2')).toContainText('Liberación');
    await expect(dialog.getByTestId('inventory-movement-legacy-event-2')).toContainText('Liberación controlada');
    expect(calls.writes).toBe(0);
  });



  test('inspector distinguishes low, healthy and partial stock without fictional values', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let stock = [];
    await page.route('**/api/v1/inventory', route => route.fulfill(json(stock)));
    const scenarios = [
      {
        record: { sku: 'LOW-01', trackingEnabled: true, onHand: 5,
          reserved: 1, available: 4, reorderThreshold: 5, lowStock: true },
        hint: 'Stock bajo: revisa el mínimo', sku: 'SKU: LOW-01',
        amount: '4', expectedAdjust: 1
      },
      {
        record: { sku: 'OK-01', trackingEnabled: true, onHand: 10,
          reserved: 0, available: 10, reorderThreshold: 2, lowStock: false },
        hint: 'Disponibilidad calculada desde el backend.',
        sku: 'SKU: OK-01', amount: '10', expectedAdjust: 1
      },
      {
        record: { sku: '', trackingEnabled: true, onHand: null,
          reserved: null, available: null, reorderThreshold: null, lowStock: false },
        hint: 'Disponibilidad calculada desde el backend.',
        sku: 'SKU: Sin SKU', amount: '—', expectedAdjust: 1
      }
    ];
    for (const state of scenarios) {
      stock = [{ id: 'stock-1', catalogItemId: 'prod-1', ...state.record }];
      await page.goto('/app/inventory');
      await page.getByTestId('inventory-row-prod-1')
        .getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
      const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
      await expect(inspector).toContainText(state.hint);
      await expect(inspector).toContainText(state.sku);
      await expect(inspector.locator('dl')).toContainText(state.amount);
      await expect(inspector.getByRole('button', { name: 'Ajustar stock' })).toHaveCount(state.expectedAdjust);
      await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
      await expect(inspector).toHaveCount(0);
    }
  });

  test('inspector Escape works even after its focusable controls are removed dynamically', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.goto('/app/inventory');
    const trigger = page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' });
    await trigger.click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await expect(inspector).toBeVisible();
    await inspector.evaluate(element => {
      for (const button of element.querySelectorAll('button')) button.remove();
    });
    await page.keyboard.press('Tab');
    await expect(inspector).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(inspector).toHaveCount(0);
    await expect(trigger).toBeFocused();
  });



  test('inspector stock actions open the correct forms without silently mutating inventory', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    const writes = [];
    page.on('request', request => {
      const pathname = new URL(request.url()).pathname;
      if ((pathname.startsWith('/api/v1/inventory') || pathname.startsWith('/api/v1/catalog'))
        && request.method() !== 'GET') {
        writes.push({ method: request.method(), url: request.url() });
      }
    });
    await page.goto('/app/inventory');
    const trigger = page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' });
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });

    await trigger.click();
    await inspector.getByRole('button', { name: 'Editar stock' }).click();
    await expect(inspector).toHaveCount(0);
    const config = page.getByRole('dialog', { name: 'Editar stock · Taladro percutor' });
    await expect(config).toBeVisible();
    await expect(config.getByLabel('SKU')).toHaveValue('TAL-18V');
    await expect(config.getByLabel('Stock físico inicial')).toHaveValue('8');
    await expect(config.getByLabel('Umbral de reposición')).toHaveValue('4');
    await config.getByRole('button', { name: 'Cancelar' }).click();
    await expect(config).toHaveCount(0);

    await trigger.click();
    await inspector.getByRole('button', { name: 'Ajustar stock' }).click();
    await expect(inspector).toHaveCount(0);
    const adjustment = page.getByRole('dialog', { name: 'Ajustar stock · Taladro percutor' });
    await expect(adjustment).toBeVisible();
    await expect(adjustment).toContainText('Disponible ahora: 5');
    await expect(adjustment.getByLabel('Ajuste')).toBeEmpty();
    await adjustment.getByRole('button', { name: 'Cancelar' }).click();
    await expect(adjustment).toHaveCount(0);
    await expect(page.getByTestId('inventory-row-prod-1')).toContainText('5');
    expect(writes).toHaveLength(0);
  });

  test('legacy whitespace-only catalog currency keeps the explicit CLP default consistently in list and inspector', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/catalog', route => route.fulfill(json([{
      id: 'prod-1', kind: 'PRODUCT', name: 'Taladro percutor',
      description: 'Legacy item', price: 54990, currency: '   ', active: true
    }])));
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-prod-1');
    await expect(row).toContainText('54.990');
    await row.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await expect(inspector).toContainText('54.990');
    await expect(inspector).not.toContainText('Precio no disponible');
    await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
  });


  test('HTTP 400 during manual stock adjustment preserves authoritative stock and a useful correction message', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let writes = 0;
    await page.route('**/api/v1/inventory/prod-1/adjustments', route => {
      writes += 1;
      return route.fulfill({ status: 400, contentType: 'application/json',
        body: JSON.stringify({ message: 'invalid warehouse adjustment' }) });
    });
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-prod-1');
    await row.getByRole('button', { name: 'Ajustar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Taladro percutor' });
    await dialog.getByLabel('Ajuste').fill('2');
    await dialog.getByRole('button', { name: 'Aplicar ajuste' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Revisa los datos ingresados');
    await expect(dialog).toBeVisible();
    await expect(row).toContainText('TAL-18V');
    await expect(row).toContainText('5');
    expect(writes).toBe(1);
  });

  test('unknown inventory alert types and legacy contact channels render read-only without any actions', async ({ page }) => {
    await bootInventory(page, { roles: ['OPERATOR'] });
    await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([{
      id: 'unknown-alert', catalogItemId: 'prod-1', type: 'UNKNOWN_EVENT',
      acknowledged: false, subjectName: null, available: null, reorderThreshold: null
    }])));
    await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([
      { id: 'sms-contact', contact: 'Customer A', preferredChannel: 'SMS' },
      { id: 'fallback-contact', contact: null, preferredChannel: null },
      { id: 'custom-channel', contact: 'Customer B', preferredChannel: 'MESSENGER' }
    ])));
    await page.route('**/api/v1/inventory/restock-subscriptions/notifications',
      route => route.fulfill(json([{ id: 'sms-notice', subjectName: null,
        contact: null, preferredChannel: 'SMS', available: null }])));
    await page.goto('/app/inventory');

    const alert = page.getByTestId('inventory-alert-unknown-alert');
    await expect(alert).toContainText('Alerta de inventario');
    await expect(alert).toContainText('Producto');
    await expect(alert).toContainText('Disponible: —');
    await expect(alert.getByRole('button', { name: 'Reponer stock' })).toHaveCount(0);
    await expect(page.getByTestId('restock-subscription-sms-contact')).toContainText('SMS');
    await expect(page.getByTestId('restock-subscription-fallback-contact')).toContainText('Canal');
    await expect(page.getByTestId('restock-subscription-custom-channel')).toContainText('MESSENGER');
    await expect(page.getByTestId('restock-notification-sms-notice')).toContainText('SMS');
    await expect(page.getByRole('button', { name: 'Marcar atendida' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cancelar espera' })).toHaveCount(0);
  });

  test('primary catalog outage recovers through the visible retry instead of inventing inventory totals', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let unavailable = true;
    await page.route('**/api/v1/catalog', route => unavailable
      ? route.fulfill({ status: 503, contentType: 'application/json',
        body: JSON.stringify({ message: 'temporarily unavailable' }) })
      : route.fulfill(json([{ id: 'prod-1', kind: 'PRODUCT', name: 'Taladro percutor',
        price: 54990, currency: 'CLP', active: true }]))
    );
    await page.goto('/app/inventory');
    await expect(page.getByRole('alert')).toContainText('No pudimos cargar el inventario completo');
    await expect(page.getByTestId('inventory-available')).toContainText('—');
    unavailable = false;
    await page.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByTestId('inventory-row-prod-1')).toBeVisible();
    await expect(page.getByTestId('inventory-available')).toContainText('5');
    await expect(page.getByRole('alert')).toHaveCount(0);
  });


  test('available stock ordering is deterministic when products have missing quantities and equal counts', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/catalog', route => route.fulfill(json([
      { id: 'prod-1', kind: 'PRODUCT', name: 'Zulu con stock', price: 100, currency: 'CLP', active: true },
      { id: 'prod-2', kind: 'PRODUCT', name: 'Alfa con stock', price: 100, currency: 'CLP', active: true },
      { id: 'prod-3', kind: 'PRODUCT', name: 'Sin configurar', price: 100, currency: 'CLP', active: true },
      { id: 'prod-4', kind: 'PRODUCT', name: 'Beta con stock', price: 100, currency: 'CLP', active: true }
    ])));
    await page.route('**/api/v1/inventory', route => route.fulfill(json([
      { id: 's1', catalogItemId: 'prod-1', trackingEnabled: true, onHand: 5, reserved: 0,
        available: 5, reorderThreshold: 1, lowStock: false },
      { id: 's2', catalogItemId: 'prod-2', trackingEnabled: true, onHand: 5, reserved: 0,
        available: 5, reorderThreshold: 1, lowStock: false },
      { id: 's4', catalogItemId: 'prod-4', trackingEnabled: true, onHand: 1, reserved: 0,
        available: 1, reorderThreshold: 0, lowStock: false }
    ])));
    await page.goto('/app/inventory');
    const rowIds = () => page.locator('tbody tr[data-testid^="inventory-row-"]')
      .evaluateAll(rows => rows.map(row => row.getAttribute('data-testid')));
    await expect(page.getByTestId('inventory-row-prod-3')).toContainText('Sin configurar');
    await page.getByLabel('Orden').selectOption('AVAILABLE_ASC');
    await expect.poll(rowIds).toEqual([
      'inventory-row-prod-4', 'inventory-row-prod-2', 'inventory-row-prod-1', 'inventory-row-prod-3'
    ]);
    await page.getByLabel('Orden').selectOption('AVAILABLE_DESC');
    await expect.poll(rowIds).toEqual([
      'inventory-row-prod-2', 'inventory-row-prod-1', 'inventory-row-prod-4', 'inventory-row-prod-3'
    ]);
  });

  test('closing inspector after its original trigger is detached does not focus a disconnected element', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/app/inventory');
    const trigger = page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' });
    await trigger.click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await expect(inspector).toBeVisible();
    await trigger.evaluate(button => button.remove());
    await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
    await expect(inspector).toHaveCount(0);
    expect(errors).toEqual([]);
  });


  test('backend configuration and consumption events remain intelligible in product history', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/inventory/prod-1/movements', route => route.fulfill(json([
      { id: 'configured', type: 'CONFIGURE', quantityDelta: 4, reservedDelta: 0,
        onHandAfter: 4, reservedAfter: 0, note: 'Inventario inicial',
        createdAt: '2026-10-02T09:00:00Z' },
      { id: 'consumed', type: 'CONSUMPTION', quantityDelta: -1, reservedDelta: 0,
        onHandAfter: 3, reservedAfter: 0, note: 'Venta registrada',
        createdAt: '2026-10-02T11:00:00Z' }
    ])));
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-prod-1').getByRole('button', { name: 'Ver historial' }).click();
    const dialog = page.getByRole('dialog', { name: 'Historial · Taladro percutor' });
    await expect(dialog.getByTestId('inventory-movement-configured')).toContainText('Configuración');
    await expect(dialog.getByTestId('inventory-movement-configured')).toContainText('Inventario inicial');
    await expect(dialog.getByTestId('inventory-movement-consumed')).toContainText('Consumo');
    await expect(dialog.getByTestId('inventory-movement-consumed')).toContainText('Físico -1');
    await dialog.getByRole('button', { name: 'Cerrar' }).click();
  });

  test('empty product history and nameless legacy catalog item never imply nonexistent inventory activity', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/catalog', route => route.fulfill(json([
      { id: 'prod-1', kind: 'PRODUCT', name: '', description: null,
        price: null, currency: null, active: true }
    ])));
    await page.route('**/api/v1/inventory/prod-1/movements', route => route.fulfill(json([])));
    await page.goto('/app/inventory');
    const product = page.getByTestId('inventory-row-prod-1');
    await expect(product).toContainText('Producto');
    await expect(product).toContainText('Sin precio');
    await expect(product).toContainText('TAL-18V');
    await product.getByRole('button', { name: 'Ver historial' }).click();
    const dialog = page.getByRole('dialog', { name: 'Historial · Producto' });
    await expect(dialog).toContainText('todavía no tiene movimientos registrados');
    await expect(dialog.locator('article')).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Cerrar' }).click();
  });


  
  test('inspector remains accessible when the host app lacks the preferred portal marker', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.goto('/app/inventory');
    await page.locator('[data-react-app="recepvoz"]')
      .evaluate(element => element.removeAttribute('data-react-app'));
    const trigger = page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ver detalles de Taladro percutor' });
    await trigger.click();
    const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
    await expect(inspector).toBeVisible();
    await expect(inspector).toContainText('Disponible');
    await inspector.getByRole('button', { name: 'Cerrar detalles' }).click();
    await expect(inspector).toHaveCount(0);
  });

  test('availability ordering puts unknown stock last without coercing it into zero', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/inventory', route => route.fulfill(json([
      { id: 'stock-1', catalogItemId: 'prod-1', sku: 'TAL-18V',
        trackingEnabled: true, onHand: 8, reserved: 3, available: 5,
        reorderThreshold: 4, lowStock: false }
    ])));
    await page.goto('/app/inventory');
    const rows = page.locator('tbody tr[data-testid^="inventory-row-"]');
    await expect(rows).toHaveCount(2);
    await expect(page.getByTestId('inventory-row-prod-2')).toContainText('Sin configurar');
    await page.getByLabel('Orden').selectOption('AVAILABLE_ASC');
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-1');
    await page.getByLabel('Orden').selectOption('AVAILABLE_DESC');
    await expect(rows.first()).toHaveAttribute('data-testid', 'inventory-row-prod-1');
    await expect(page.getByTestId('inventory-available')).toContainText('5');
  });

  test('base-stock adjustment draft cannot write when permissions are revoked after opening', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let canWrite = true;
    await page.route('**/api/v1/auth/me', route => route.fulfill(json({
      email: 'owner@negocio.cl', roles: canWrite ? ['BUSINESS_ADMIN'] : ['OPERATOR']
    })));
    let writes = 0;
    await page.route('**/api/v1/inventory/prod-1/adjustments', route => {
      writes++;
      return route.fulfill(json({}));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-prod-1')
      .getByRole('button', { name: 'Ajustar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Taladro percutor' });
    await dialog.getByLabel('Ajuste').fill('2');
    canWrite = false;
    await page.getByRole('button', { name: 'Actualizar', exact: true })
      .evaluate(button => button.click());
    await expect(page.getByText('Solo lectura')).toBeVisible();
    await dialog.getByRole('button', { name: 'Aplicar ajuste' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Tus permisos cambiaron');
    expect(writes).toBe(0);
    await expect(page.getByTestId('inventory-available')).toContainText('6');
  });

  test('message-less network failures keep history and adjustments safe with plain-language fallback', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.goto('/app/inventory');
    await page.evaluate(() => {
      const originalFetch = window.fetch.bind(window);
      window.fetch = (input, init) => {
        const url = typeof input === 'string' ? input : input.url;
        if (url.endsWith('/api/v1/inventory/prod-1/movements')
          || (url.endsWith('/api/v1/inventory/prod-1/adjustments') && init?.method === 'POST')) {
          return Promise.reject(new Error(''));
        }
        return originalFetch(input, init);
      };
    });
    const row = page.getByTestId('inventory-row-prod-1');
    await row.getByRole('button', { name: 'Ver historial' }).click();
    const history = page.getByRole('dialog', { name: 'Historial · Taladro percutor' });
    await expect(history.getByRole('alert')).toContainText('No pudimos cargar el historial.');
    await history.getByRole('button', { name: 'Cerrar' }).click();
    await row.getByRole('button', { name: 'Ajustar stock' }).click();
    const adjustment = page.getByRole('dialog', { name: 'Ajustar stock · Taladro percutor' });
    await adjustment.getByLabel('Ajuste').fill('4');
    await adjustment.getByRole('button', { name: 'Aplicar ajuste' }).click();
    await expect(adjustment.getByRole('alert')).toContainText('No pudimos guardar el cambio.');
    await expect(row).toContainText('TAL-18V');
    await expect(page.getByTestId('inventory-available')).toContainText('6');
  });

  test('legacy blank business name does not introduce an invented title or separator', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/business', route => route.fulfill(json({
      id: '11111111-1111-1111-1111-111111111111', name: '   '
    })));
    await page.goto('/app/inventory');
    const heading = page.locator('.rv-page-header').first();
    await expect(heading).toContainText('Stock físico, reservado y disponible');
    await expect(heading).not.toContainText(' · Stock físico');
    await expect(page.getByTestId('inventory-row-prod-1')).toBeVisible();
  });


  test('malformed catalog success response fails closed rather than rendering invented or crashed stock totals', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/catalog', route => route.fulfill(json({ unexpected: 'object' })));
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/app/inventory');
    await expect(page.getByRole('alert')).toContainText('No pudimos cargar el inventario completo');
    await expect(page.getByTestId('inventory-products')).toContainText('—');
    await expect(page.getByTestId('inventory-available')).toContainText('—');
    await expect(page.getByTestId('inventory-row-prod-1')).toHaveCount(0);
    expect(errors).toEqual([]);
  });

  test('malformed secondary success payloads show unavailable queues instead of zeros or runtime errors', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json(null)));
    await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json({ waiting: null })));
    await page.route('**/api/v1/inventory/restock-subscriptions/notifications',
      route => route.fulfill(json({ notifications: null })));
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/app/inventory');
    await expect(page.getByTestId('inventory-row-prod-1')).toBeVisible();
    await expect(page.getByTestId('inventory-partial-error')).toBeVisible();
    const summary = page.getByLabel('Resumen de reposición');
    await expect(summary).toContainText('— alertas (sin datos)');
    await expect(summary).toContainText('— esperando (sin datos)');
    await expect(page.getByRole('heading', { name: 'Listos para enviar' }).locator('xpath=../..')).toContainText('—');
    await expect(page.getByText('Alertas no disponibles. Reintenta la consulta.')).toBeVisible();
    await expect(page.getByText('Lista de espera no disponible. Reintenta la consulta.')).toBeVisible();
    await expect(page.getByText('Avisos no disponibles. Reintenta la consulta.')).toBeVisible();
    expect(errors).toEqual([]);
  });


  
  test('malformed secondary success responses recover only after an explicit authoritative refresh', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let malformed = true;
    await page.route('**/api/v1/inventory/alerts', route =>
      route.fulfill(json(malformed ? null : [])));
    await page.route('**/api/v1/inventory/restock-subscriptions', route =>
      route.fulfill(json(malformed ? { wrong: 'shape' } : [])));
    await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route =>
      route.fulfill(json(malformed ? { wrong: 'shape' } : [])));
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/app/inventory');
    const degraded = page.getByTestId('inventory-partial-error');
    await expect(degraded).toBeVisible();
    await expect(page.getByTestId('inventory-available')).toContainText('6');
    await expect(page.getByText('No hay alertas abiertas.')).toHaveCount(0);
    await expect(page.getByText('Nadie está esperando reposición.')).toHaveCount(0);
    await expect(page.getByText('No hay avisos pendientes.')).toHaveCount(0);
    malformed = false;
    await degraded.getByRole('button', { name: 'Reintentar consultas' }).click();
    await expect(degraded).toHaveCount(0);
    await expect(page.getByLabel('Resumen de reposición')).toContainText('0 alertas');
    await expect(page.getByLabel('Resumen de reposición')).toContainText('0 esperando');
    await expect(page.getByText('No hay alertas abiertas.')).toBeVisible();
    await expect(page.getByText('Nadie está esperando reposición.')).toBeVisible();
    await expect(page.getByText('No hay avisos pendientes.')).toBeVisible();
    expect(errors).toEqual([]);
  });

  test('catalog disappearance during an open inspector preserves readable snapshot actions without writes', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let catalogVisible = true;
    await page.route('**/api/v1/catalog', route => route.fulfill(json(catalogVisible
      ? [{ id: 'prod-1', kind: 'PRODUCT', name: 'Taladro percutor',
        description: 'Taladro 18V', price: 54990, currency: 'CLP', active: true }]
      : [])));
    const writes = [];
    await page.route('**/api/v1/inventory/prod-1/variants', route => {
      if (route.request().method() !== 'GET') writes.push(route.request().method());
      return route.fulfill(json([]));
    });
    const targets = [
      ['Variantes', 'Variantes · Taladro percutor'],
      ['Ver historial', 'Historial · Taladro percutor'],
      ['Editar stock', 'Editar stock · Taladro percutor'],
      ['Ajustar stock', 'Ajustar stock · Taladro percutor']
    ];
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    for (const [action, destination] of targets) {
      catalogVisible = true;
      await page.goto('/app/inventory');
      const row = page.getByTestId('inventory-row-prod-1');
      await expect(row).toBeVisible();
      await row.getByRole('button', { name: 'Ver detalles de Taladro percutor' }).click();
      const inspector = page.getByRole('dialog', { name: 'Taladro percutor', exact: true });
      await expect(inspector).toBeVisible();
      catalogVisible = false;
      // A background refresh can complete while a product inspector remains mounted.
      await page.getByRole('button', { name: 'Actualizar', exact: true })
        .evaluate(button => button.click());
      await expect(row).toHaveCount(0);
      await expect(inspector).toContainText('Taladro percutor');
      await inspector.getByRole('button', { name: action, exact: true }).click();
      await expect(page.getByRole('dialog', { name: destination })).toBeVisible();
    }
    expect(writes).toEqual([]);
    expect(errors).toEqual([]);
  });


  test('malformed inventory success response is rejected and normal stock returns after explicit retry', async ({ page }) => {
    await bootInventory(page, { roles: ['BUSINESS_ADMIN'] });
    let malformed = true;
    await page.route('**/api/v1/inventory', route => route.fulfill(json(malformed
      ? { invalid: 'stock must be a list' }
      : [{ id: 'stock-recovered', catalogItemId: 'prod-1', sku: 'TAL-RECOVERED',
        trackingEnabled: true, onHand: 7, reserved: 2, available: 5,
        reorderThreshold: 2, lowStock: false }])));
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/app/inventory');
    await expect(page.getByRole('alert')).toContainText('No pudimos cargar el inventario completo');
    await expect(page.getByTestId('inventory-available')).toContainText('—');
    malformed = false;
    await page.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByTestId('inventory-row-prod-1')).toContainText('TAL-RECOVERED');
    await expect(page.getByTestId('inventory-available')).toContainText('5');
    expect(errors).toEqual([]);
  });

});
