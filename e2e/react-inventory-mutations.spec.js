const { test, expect } = require('@playwright/test');
const { captureInventoryVisual } = require('./inventory-visual-evidence-helper');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootAdminInventory(page) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'inventory-react-mutations'
  );

  const productId = '22222222-2222-2222-2222-222222222222';
  const createdProductId = '33333333-3333-3333-3333-333333333333';
  let inventory = [];
  let catalog = [{
    id: productId,
    kind: 'PRODUCT',
    name: 'Cera mate',
    description: 'Cera profesional',
    price: 5990,
    currency: 'CLP',
    active: true
  }];
  const requests = {
    configure: [],
    adjust: [],
    createProduct: []
  };

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Tienda Demo'
  })));

  await page.route('**/api/v1/catalog', async route => {
    if (route.request().method() === 'GET') {
      return route.fulfill(json(catalog));
    }
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      requests.createProduct.push(body);
      const created = {
        id: createdProductId,
        ...body
      };
      catalog = [...catalog, created];
      return route.fulfill({
        ...json(created),
        status: 201
      });
    }
    return route.continue();
  });

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));

  await page.route(`**/api/v1/inventory/${productId}/adjustments`, async route => {
    const body = route.request().postDataJSON();
    requests.adjust.push(body);

    const current = inventory[0];
    inventory = [{
      ...current,
      onHand: current.onHand + body.delta,
      available: current.available + body.delta
    }];

    await route.fulfill(json(inventory[0]));
  });

  await page.route(`**/api/v1/inventory/${productId}`, async route => {
    if (route.request().method() !== 'PUT') {
      return route.continue();
    }

    const body = route.request().postDataJSON();
    requests.configure.push(body);
    inventory = [{
      id: 'stock-2',
      catalogItemId: productId,
      sku: body.sku,
      trackingEnabled: body.trackingEnabled,
      onHand: body.onHand,
      reserved: 0,
      available: body.onHand,
      reorderThreshold: body.reorderThreshold,
      lowStock: body.onHand <= body.reorderThreshold
    }];

    await route.fulfill(json(inventory[0]));
  });

  await page.route('**/api/v1/inventory', route => {
    if (route.request().url().endsWith('/api/v1/inventory')) {
      return route.fulfill(json(inventory));
    }
    return route.continue();
  });

  return { productId, createdProductId, requests };
}

test.describe('React Inventory mutations', () => {
  test('admin creates a product in the catalog and sees it ready for stock configuration', async ({ page }) => {
    const { createdProductId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');

    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await expect(dialog).toBeVisible();

    await dialog.getByLabel('Nombre').fill('Pomada premium');
    await dialog.getByLabel('Descripción').fill('Fijación fuerte');
    await dialog.getByLabel('Precio').fill('7490');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.getByRole('button', { name: 'Crear producto' }).click();

    await expect.poll(() => requests.createProduct.length).toBe(1);
    expect(requests.createProduct[0]).toEqual({
      kind: 'PRODUCT',
      name: 'Pomada premium',
      description: 'Fijación fuerte',
      price: 7490,
      currency: 'CLP',
      durationMinutes: null,
      metadataJson: null,
      active: true
    });

    const row = page.getByTestId(`inventory-row-${createdProductId}`);
    await expect(row).toContainText('Pomada premium');
    await expect(row).toContainText('Sin configurar');
    await expect(row.getByRole('button', { name: 'Configurar stock' })).toBeVisible();
    await expect(page.getByTestId('inventory-products')).toContainText('2');
  });

  test('guided creation saves catalog once, then independently configures stock without duplicates', async ({ page }) => {
    const { createdProductId, requests } = await bootAdminInventory(page);
    let createdStock = null;
    await page.route(`**/api/v1/inventory/${createdProductId}`, async route => {
      if (route.request().method() !== 'PUT') return route.continue();
      const body = route.request().postDataJSON();
      requests.configure.push(body);
      createdStock = {
        id: 'stock-created', catalogItemId: createdProductId, sku: body.sku,
        trackingEnabled: body.trackingEnabled, onHand: body.onHand,
        reserved: 0, available: body.onHand, reorderThreshold: body.reorderThreshold,
        lowStock: body.onHand <= body.reorderThreshold
      };
      await route.fulfill(json(createdStock));
    });
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const productDialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await productDialog.getByLabel('Nombre').fill('Pomada con inventario');
    await productDialog.getByLabel('Precio').fill('9500');
    await productDialog.getByLabel('Moneda').fill('CLP');
    await productDialog.getByRole('button', { name: 'Crear y configurar stock' }).click();

    await expect.poll(() => requests.createProduct.length).toBe(1);
    const stockDialog = page.getByRole('dialog', { name: 'Configurar stock · Pomada con inventario' });
    await expect(stockDialog).toBeVisible();
    await expect(stockDialog).toContainText('Paso 2');
    await expect(page.getByTestId(`inventory-row-${createdProductId}`)).toContainText('Sin configurar');
    await stockDialog.getByLabel('SKU').fill('POM-9500');
    await stockDialog.getByLabel('Stock físico inicial').fill('7');
    await stockDialog.getByLabel('Umbral de reposición').fill('2');
    await stockDialog.getByRole('button', { name: 'Guardar configuración' }).click();

    await expect.poll(() => requests.configure.length).toBe(1);
    expect(requests.configure[0]).toEqual({
      sku: 'POM-9500', trackingEnabled: true,
      onHand: 7, reorderThreshold: 2, note: null
    });
    expect(requests.createProduct).toHaveLength(1);
    await expect(stockDialog).toHaveCount(0);
  });

  test('stock setup failure keeps the already-created catalog record and allows a stock-only retry', async ({ page }) => {
    const { createdProductId, requests } = await bootAdminInventory(page);
    let attempts = 0;
    await page.route(`**/api/v1/inventory/${createdProductId}`, route => {
      if (route.request().method() !== 'PUT') return route.fallback();
      attempts++;
      if (attempts === 1) {
        return route.fulfill({ status: 409, contentType: 'application/json',
          body: JSON.stringify({ message: 'stale inventory version' }) });
      }
      const body = route.request().postDataJSON();
      return route.fulfill(json({
        id: 'stock-created', catalogItemId: createdProductId,
        sku: body.sku, trackingEnabled: body.trackingEnabled,
        onHand: body.onHand, reserved: 0, available: body.onHand,
        reorderThreshold: body.reorderThreshold, lowStock: false
      }));
    });

    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const productDialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await productDialog.getByLabel('Nombre').fill('Producto con reintento seguro');
    await productDialog.getByLabel('Precio').fill('2000');
    await productDialog.getByRole('button', { name: 'Crear y configurar stock' }).click();

    const stockDialog = page.getByRole('dialog', { name: 'Configurar stock · Producto con reintento seguro' });
    await expect(stockDialog).toBeVisible();
    await stockDialog.getByLabel('SKU').fill('RETRY-STOCK');
    await stockDialog.getByLabel('Stock físico inicial').fill('3');
    await stockDialog.getByLabel('Umbral de reposición').fill('1');
    await stockDialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(stockDialog.getByRole('alert')).toContainText('conflicto');
    expect(requests.createProduct).toHaveLength(1);
    await stockDialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(stockDialog).toHaveCount(0);
    expect(attempts).toBe(2);
    expect(requests.createProduct).toHaveLength(1);
  });

  test('failed catalog creation never opens the stock step or silently retries', async ({ page }) => {
    const { requests } = await bootAdminInventory(page);
    let failedPosts = 0;
    await page.route('**/api/v1/catalog', async route => {
      if (route.request().method() !== 'POST') return route.fallback();
      failedPosts++;
      return route.fulfill({ status: 500, contentType: 'application/json',
        body: JSON.stringify({ message: 'catalog unavailable' }) });
    });
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Producto con error');
    await dialog.getByLabel('Precio').fill('1000');
    await dialog.getByRole('button', { name: 'Crear y configurar stock' }).click();
    await expect(dialog.getByRole('alert')).toContainText('El servidor no pudo guardar');
    await expect(page.getByRole('dialog', { name: /Configurar stock/ })).toHaveCount(0);
    expect(failedPosts).toBe(1);
    expect(requests.createProduct).toHaveLength(0);
  });

  test('admin configures an existing product and then adjusts authoritative base stock', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');

    const row = page.getByTestId(`inventory-row-${productId}`);
    await expect(row).toContainText('Sin configurar');

    await row.getByRole('button', { name: 'Configurar stock' }).click();
    const configureDialog = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await expect(configureDialog).toBeVisible();

    await configureDialog.getByLabel('SKU').fill('CER-MATE');
    await configureDialog.getByLabel('Stock físico inicial').fill('4');
    await configureDialog.getByLabel('Umbral de reposición').fill('2');
    await configureDialog.getByLabel('Nota').fill('Carga inicial');
    await configureDialog.getByRole('button', { name: 'Guardar configuración' }).click();

    await expect.poll(() => requests.configure.length).toBe(1);
    expect(requests.configure[0]).toEqual({
      sku: 'CER-MATE',
      trackingEnabled: true,
      onHand: 4,
      reorderThreshold: 2,
      note: 'Carga inicial'
    });

    await expect(row).toContainText('CER-MATE');
    await expect(row).toContainText('4');
    await expect(page.getByTestId('inventory-available')).toContainText('4');

    await row.getByRole('button', { name: 'Ajustar stock' }).click();
    const adjustDialog = page.getByRole('dialog', { name: 'Ajustar stock · Cera mate' });
    await expect(adjustDialog).toBeVisible();

    await adjustDialog.getByLabel('Ajuste').fill('3');
    await adjustDialog.getByLabel('Nota').fill('Reposición');
    await adjustDialog.getByRole('button', { name: 'Aplicar ajuste' }).click();

    await expect.poll(() => requests.adjust.length).toBe(1);
    expect(requests.adjust[0]).toEqual({
      delta: 3,
      referenceType: 'MANUAL',
      referenceId: null,
      note: 'Reposición'
    });

    await expect(row).toContainText('7');
    await expect(page.getByTestId('inventory-available')).toContainText('7');
  });

  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 390, height: 844 }
  ]) {
    test('captures exact-head product creation, stock setup and 409 error at ' + viewport.width + 'px', async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.emulateMedia({ reducedMotion: 'reduce' });
      const { createdProductId, requests } = await bootAdminInventory(page);
      const rejectStock = route => {
        if (route.request().method() !== 'PUT') return route.fallback();
        return route.fulfill({
          status: 409, contentType: 'application/json',
          body: JSON.stringify({ message: 'stale inventory version' })
        });
      };
      await page.route('**/api/v1/inventory/' + createdProductId, rejectStock);
      await page.goto('/app/inventory');
      await page.getByRole('button', { name: 'Nuevo producto' }).click();
      const productDialog = page.getByRole('dialog', { name: 'Nuevo producto' });
      await expect(productDialog).toBeVisible();
      await captureInventoryVisual(page, 'create-product');

      await productDialog.getByLabel('Nombre').fill('Pomada con evidencia visual');
      await productDialog.getByLabel('Precio').fill('9500');
      await productDialog.getByLabel('Moneda').fill('CLP');
      await productDialog.getByRole('button', { name: 'Crear y configurar stock' }).click();
      await expect.poll(() => requests.createProduct.length).toBe(1);
      const stockDialog = page.getByRole('dialog', { name: 'Configurar stock · Pomada con evidencia visual' });
      await expect(stockDialog).toContainText('Paso 2');
      await captureInventoryVisual(page, 'configure-stock');

      await stockDialog.getByLabel('SKU').fill('POM-VIS-1');
      await stockDialog.getByLabel('Stock físico inicial').fill('4');
      await stockDialog.getByLabel('Umbral de reposición').fill('2');
      await stockDialog.getByRole('button', { name: 'Guardar configuración' }).click();
      await expect(stockDialog.getByRole('alert')).toContainText('conflicto');
      expect(requests.createProduct).toHaveLength(1);
      await captureInventoryVisual(page, 'stock-conflict-409');
    });
  }


  test('editing an existing catalog product validates currency before one authoritative PUT', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    let catalog = [{
      id: productId, kind: 'PRODUCT', name: 'Cera mate',
      description: 'Cera profesional', price: 5990, currency: 'CLP',
      durationMinutes: null, metadataJson: null, active: true
    }];
    const updates = [];
    await page.route('**/api/v1/catalog', route => {
      if (route.request().method() === 'GET') return route.fulfill(json(catalog));
      return route.fallback();
    });
    await page.route('**/api/v1/catalog/' + productId, route => {
      if (route.request().method() !== 'PUT') return route.fallback();
      const update = route.request().postDataJSON();
      updates.push(update);
      catalog = [{ ...catalog[0], ...update }];
      return route.fulfill(json(catalog[0]));
    });
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Editar producto Cera mate' }).click();
    const dialog = page.getByRole('dialog', { name: 'Editar producto · Cera mate' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Crear y configurar stock' })).toHaveCount(0);
    await dialog.getByLabel('Precio').fill('7290');
    await dialog.getByLabel('Descripción').fill('Producto editado por QA');
    await dialog.getByLabel('Moneda').fill('US');
    await dialog.getByRole('button', { name: 'Guardar producto' }).click();
    await expect(dialog.getByRole('alert')).toContainText('tres letras');
    expect(updates).toHaveLength(0);
    expect(requests.createProduct).toHaveLength(0);

    await dialog.getByLabel('Moneda').fill('USD');
    await dialog.getByRole('button', { name: 'Guardar producto' }).click();
    await expect.poll(() => updates.length).toBe(1);
    expect(updates[0]).toEqual({
      kind: 'PRODUCT', name: 'Cera mate',
      description: 'Producto editado por QA', price: 7290, currency: 'USD',
      durationMinutes: null, metadataJson: null, active: true
    });
    await expect(dialog).toHaveCount(0);
    await expect(page.getByTestId('inventory-row-' + productId)).toContainText('Producto editado por QA');
    expect(requests.createProduct).toHaveLength(0);
  });



  test('cancelled catalog and stock dialogs never write or create implicit inventory', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const createDialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await createDialog.getByLabel('Nombre').fill('Unsubmitted product');
    await createDialog.getByRole('button', { name: 'Cancelar' }).click();
    await expect(createDialog).toHaveCount(0);

    await page.getByTestId('inventory-row-' + productId)
      .getByRole('button', { name: 'Configurar stock' }).click();
    const stockDialog = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await stockDialog.getByLabel('SKU').fill('NEVER-SAVED');
    await stockDialog.getByRole('button', { name: 'Cancelar' }).click();
    await expect(stockDialog).toHaveCount(0);
    await expect(page.getByTestId('inventory-row-' + productId)).toContainText('Sin configurar');
    expect(requests.createProduct).toHaveLength(0);
    expect(requests.configure).toHaveLength(0);
    expect(requests.adjust).toHaveLength(0);
  });

  test('catalog edit 409 stays in edit dialog, preserves draft and never duplicates write', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    const writes = [];
    await page.route('**/api/v1/catalog/' + productId, route => {
      if (route.request().method() !== 'PUT') return route.fallback();
      writes.push(route.request().postDataJSON());
      return route.fulfill({
        status: 409, contentType: 'application/json',
        body: JSON.stringify({ message: 'catalog version conflict' })
      });
    });
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Editar producto Cera mate' }).click();
    const dialog = page.getByRole('dialog', { name: 'Editar producto · Cera mate' });
    await dialog.getByLabel('Precio').fill('8500');
    await dialog.getByRole('button', { name: 'Guardar producto' }).click();
    await expect.poll(() => writes.length).toBe(1);
    await expect(dialog.getByRole('alert')).toContainText('conflicto');
    await expect(dialog.getByLabel('Precio')).toHaveValue('8500');
    await expect(dialog).toBeVisible();
    expect(requests.createProduct).toHaveLength(0);
    await dialog.getByRole('button', { name: 'Cancelar' }).click();
    await expect(dialog).toHaveCount(0);
    expect(writes).toHaveLength(1);
    await expect(page.getByTestId('inventory-row-' + productId)).toContainText('Cera profesional');
  });


});
