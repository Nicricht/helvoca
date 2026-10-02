const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function base(page, { catalog, inventory, alerts = [], roles = ['BUSINESS_ADMIN'], permissions } = {}) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'inventory-react-parity'
  );

  const state = {
    catalog: catalog || [],
    inventory: inventory || [],
    alerts,
    inventoryGets: 0,
    catalogUpdates: [],
    inventoryUpdates: [],
    variantUpdates: []
  };

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles,
    permissions: permissions || [
      'BUSINESS_READ',
      'CATALOG_READ',
      'CATALOG_MANAGE',
      'INVENTORY_READ',
      'INVENTORY_MANAGE'
    ]
  })));

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    name: 'Tienda Paridad'
  })));

  await page.route('**/api/v1/catalog/**', async route => {
    if (route.request().method() !== 'PUT') return route.fallback();
    const id = route.request().url().split('/').pop();
    const body = route.request().postDataJSON();
    state.catalogUpdates.push({ id, body });
    state.catalog = state.catalog.map(item => item.id === id ? { id, ...body } : item);
    return route.fulfill(json(state.catalog.find(item => item.id === id)));
  });

  await page.route('**/api/v1/catalog', route => route.fulfill(json(state.catalog)));

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json(state.alerts)));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));

  await page.route('**/api/v1/inventory/**', async route => {
    const url = new URL(route.request().url());
    const parts = url.pathname.split('/').filter(Boolean);
    if (route.request().method() === 'PUT' && parts.length === 4) {
      const productId = parts[3];
      const body = route.request().postDataJSON();
      state.inventoryUpdates.push({ productId, body });
      state.inventory = state.inventory.map(stock => stock.catalogItemId === productId ? {
        ...stock,
        sku: body.sku,
        trackingEnabled: body.trackingEnabled,
        onHand: body.onHand,
        available: body.onHand - (stock.reserved || 0),
        reorderThreshold: body.reorderThreshold
      } : stock);
      return route.fulfill(json(state.inventory.find(stock => stock.catalogItemId === productId)));
    }
    if (url.pathname.endsWith('/movements')) return route.fulfill(json([]));
    return route.fallback();
  });

  await page.route('**/api/v1/inventory', route => {
    if (!route.request().url().endsWith('/api/v1/inventory')) return route.fallback();
    state.inventoryGets += 1;
    return route.fulfill(json(state.inventory));
  });

  return state;
}

test.describe('React Inventory legacy parity', () => {
  test('matches legacy filters, minimum stock, refresh, descending sort and setup shortcut', async ({ page }) => {
    const state = await base(page, {
      catalog: [
        { id: 'p1', kind: 'PRODUCT', name: 'Taladro', description: '', price: 100, currency: 'CLP', active: true },
        { id: 'p2', kind: 'PRODUCT', name: 'Broca', description: '', price: 50, currency: 'CLP', active: true },
        { id: 'p3', kind: 'PRODUCT', name: 'Guante', description: '', price: 20, currency: 'CLP', active: true }
      ],
      inventory: [
        { id: 's1', catalogItemId: 'p1', sku: 'TAL', trackingEnabled: true, onHand: 10, reserved: 1, available: 9, reorderThreshold: 3, lowStock: false },
        { id: 's2', catalogItemId: 'p2', sku: 'BRO', trackingEnabled: true, onHand: 3, reserved: 1, available: 2, reorderThreshold: 2, lowStock: true }
      ],
      alerts: [
        { id: 'a1', catalogItemId: 'p1', type: 'RESTOCKED', subjectName: 'Taladro', available: 9, reorderThreshold: 3, acknowledged: false }
      ]
    });

    await page.goto('/app/inventory');

    await expect(page.getByRole('columnheader', { name: 'Mínimo' })).toBeVisible();
    await expect(page.getByTestId('inventory-row-p1')).toContainText('3');

    await page.getByLabel('Estado').selectOption('TRACKED');
    await expect(page.getByTestId('inventory-row-p1')).toBeVisible();
    await expect(page.getByTestId('inventory-row-p2')).toBeVisible();
    await expect(page.getByTestId('inventory-row-p3')).toHaveCount(0);

    await page.getByLabel('Estado').selectOption('RESTOCKED');
    await expect(page.getByTestId('inventory-row-p1')).toBeVisible();
    await expect(page.getByTestId('inventory-row-p2')).toHaveCount(0);

    await page.getByLabel('Estado').selectOption('ALL');
    await page.getByLabel('Orden').selectOption('AVAILABLE_DESC');
    const rows = page.locator('tbody [data-testid^="inventory-row-"]');
    await expect(rows.nth(0)).toHaveAttribute('data-testid', 'inventory-row-p1');

    await expect(page.getByRole('region', { name: 'Productos sin stock configurado' })).toContainText('1 producto sin stock configurado');
    await page.getByRole('button', { name: 'Ver sin configurar' }).click();
    await expect(page.getByLabel('Estado')).toHaveValue('UNCONFIGURED');
    await expect(page.getByTestId('inventory-row-p3')).toBeVisible();

    const before = state.inventoryGets;
    await page.getByRole('button', { name: 'Actualizar' }).click();
    await expect.poll(() => state.inventoryGets).toBeGreaterThan(before);
  });

  test('admin edits catalog data and stock configuration including tracking state', async ({ page }) => {
    const product = {
      id: 'p-edit',
      kind: 'PRODUCT',
      name: 'Cera control',
      description: 'Original',
      price: 5990,
      currency: 'CLP',
      durationMinutes: null,
      metadataJson: '{"origin":"legacy"}',
      active: true
    };
    const state = await base(page, {
      catalog: [product],
      inventory: [{
        id: 's-edit',
        catalogItemId: 'p-edit',
        sku: 'CER-01',
        trackingEnabled: true,
        onHand: 4,
        reserved: 1,
        available: 3,
        reorderThreshold: 1,
        lowStock: false
      }]
    });

    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-p-edit');

    await row.getByRole('button', { name: 'Editar producto Cera control' }).click();
    const productDialog = page.getByRole('dialog', { name: 'Editar producto · Cera control' });
    await productDialog.getByLabel('Nombre').fill('Cera control pro');
    await productDialog.getByLabel('Descripción').fill('Actualizada');
    await productDialog.getByLabel('Precio').fill('6490');
    await productDialog.getByRole('button', { name: 'Guardar producto' }).click();

    await expect.poll(() => state.catalogUpdates.length).toBe(1);
    expect(state.catalogUpdates[0].body).toEqual({
      kind: 'PRODUCT',
      name: 'Cera control pro',
      description: 'Actualizada',
      price: 6490,
      currency: 'CLP',
      durationMinutes: null,
      metadataJson: '{"origin":"legacy"}',
      active: true
    });
    await expect(row).toContainText('Cera control pro');

    await row.getByRole('button', { name: 'Editar stock' }).click();
    const stockDialog = page.getByRole('dialog', { name: 'Editar stock · Cera control pro' });
    await stockDialog.getByLabel('Seguimiento activo').uncheck();
    await stockDialog.getByRole('button', { name: 'Guardar configuración' }).click();

    await expect.poll(() => state.inventoryUpdates.length).toBe(1);
    expect(state.inventoryUpdates[0].body).toEqual({
      sku: 'CER-01',
      trackingEnabled: false,
      onHand: 4,
      reorderThreshold: 1,
      note: null
    });
    await expect(row).toContainText('Control desactivado');
    await expect(row.getByRole('button', { name: 'Ajustar stock' })).toHaveCount(0);
  });

  test('admin can edit variant tracking and active state like legacy', async ({ page }) => {
    const productId = 'p-variant';
    const variantId = 'v-variant';
    const state = await base(page, {
      catalog: [{ id: productId, kind: 'PRODUCT', name: 'Polera', description: '', price: 10000, currency: 'CLP', active: true }],
      inventory: [{ id: 's-var', catalogItemId: productId, sku: 'POL', trackingEnabled: true, onHand: 4, reserved: 0, available: 4, reorderThreshold: 1, lowStock: false }]
    });

    let variant = {
      id: variantId,
      catalogItemId: productId,
      name: 'Negro / M',
      optionValuesJson: '{"color":"Negro","talla":"M"}',
      sku: 'POL-N-M',
      trackingEnabled: true,
      onHand: 4,
      reserved: 0,
      available: 4,
      reorderThreshold: 1,
      lowStock: false,
      active: true
    };

    await page.route(`**/api/v1/inventory/${productId}/variants/${variantId}`, async route => {
      if (route.request().method() !== 'PUT') return route.fallback();
      const body = route.request().postDataJSON();
      state.variantUpdates.push(body);
      variant = { ...variant, ...body, available: body.onHand - variant.reserved };
      return route.fulfill(json(variant));
    });
    await page.route(`**/api/v1/inventory/${productId}/variants`, route => route.fulfill(json([variant])));
    await page.route(`**/api/v1/inventory/${productId}/variants/${variantId}/movements`, route => route.fulfill(json([])));

    await page.goto('/app/inventory');
    await page.getByTestId(`inventory-row-${productId}`).getByRole('button', { name: 'Variantes' }).click();

    const variantsDialog = page.getByRole('dialog', { name: 'Variantes · Polera' });
    const card = variantsDialog.getByTestId(`inventory-variant-${variantId}`);
    await card.getByRole('button', { name: 'Editar' }).click();

    await variantsDialog.getByLabel('Seguimiento activo').uncheck();
    await variantsDialog.getByLabel('Variante activa').uncheck();
    await variantsDialog.getByRole('button', { name: 'Guardar variante' }).click();

    await expect.poll(() => state.variantUpdates.length).toBe(1);
    expect(state.variantUpdates[0].trackingEnabled).toBe(false);
    expect(state.variantUpdates[0].active).toBe(false);
    await expect(card).toContainText('Inactiva');
  });

  test('variant stock alert opens adjustment for the exact variant', async ({ page }) => {
    const productId = 'p-alert-var';
    const variantId = 'v-alert-var';
    await base(page, {
      catalog: [{ id: productId, kind: 'PRODUCT', name: 'Zapatilla', description: '', price: 40000, currency: 'CLP', active: true }],
      inventory: [{ id: 's-alert-var', catalogItemId: productId, sku: 'ZAP', trackingEnabled: true, onHand: 5, reserved: 0, available: 5, reorderThreshold: 1, lowStock: false }],
      alerts: [{
        id: 'alert-var',
        catalogItemId: productId,
        variantId,
        type: 'OUT_OF_STOCK',
        subjectName: 'Zapatilla · Negro / 42',
        sku: 'ZAP-BLK-42',
        available: 0,
        reorderThreshold: 1,
        acknowledged: false
      }]
    });

    const variant = {
      id: variantId,
      catalogItemId: productId,
      name: 'Negro / 42',
      optionValuesJson: '{"color":"Negro","talla":"42"}',
      sku: 'ZAP-BLK-42',
      trackingEnabled: true,
      onHand: 0,
      reserved: 0,
      available: 0,
      reorderThreshold: 1,
      lowStock: true,
      active: true
    };
    await page.route(`**/api/v1/inventory/${productId}/variants`, route => route.fulfill(json([variant])));

    await page.goto('/app/inventory');
    const alert = page.getByTestId('inventory-alert-alert-var');
    await alert.getByRole('button', { name: 'Reponer stock' }).click();

    const variantsDialog = page.getByRole('dialog', { name: 'Variantes · Zapatilla' });
    await expect(variantsDialog).toBeVisible();
    await expect(variantsDialog.getByRole('heading', { name: 'Ajustar · Negro / 42' })).toBeVisible();
    await expect(variantsDialog.getByText('Disponible ahora:')).toContainText('0');
  });
});
