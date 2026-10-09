const { test, expect } = require('@playwright/test');

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
});
