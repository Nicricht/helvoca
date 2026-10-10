const { test, expect } = require('./inventory-coverage-fixture');
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



  test('tampered catalog form rejects blank names and nonnumeric prices without sending a create request', async ({ page }) => {
    const { requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.locator('form').evaluate(form => { form.noValidate = true; });
    await dialog.getByLabel('Nombre').fill('   ');
    await dialog.getByLabel('Precio').fill('1200');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect(dialog.getByRole('alert')).toContainText('Escribe un nombre');
    expect(requests.createProduct).toHaveLength(0);

    await dialog.getByLabel('Nombre').fill('Artículo verificado');
    await dialog.locator('[name="price"]').evaluate(input => { input.type = 'text'; });
    await dialog.getByLabel('Precio').fill('not-a-price');
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect(dialog.getByRole('alert')).toContainText('El precio debe ser un número');
    await expect(dialog).toBeVisible();
    expect(requests.createProduct).toHaveLength(0);
    await dialog.getByRole('button', { name: 'Cancelar' }).click();
  });

  test('tampered physical stock form rejects negative and fractional quantities before any PUT', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-' + productId);
    await row.getByRole('button', { name: 'Configurar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await dialog.locator('form').evaluate(form => {
      form.noValidate = true;
      form.querySelector('[name="onHand"]').type = 'text';
      form.querySelector('[name="reorderThreshold"]').type = 'text';
    });
    await dialog.getByLabel('Stock físico inicial').fill('-5');
    await dialog.getByLabel('Umbral de reposición').fill('2');
    await dialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(dialog.getByRole('alert')).toContainText('enteros iguales o mayores que cero');
    expect(requests.configure).toHaveLength(0);

    await dialog.getByLabel('Stock físico inicial').fill('5');
    await dialog.getByLabel('Umbral de reposición').fill('1.25');
    await dialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(dialog.getByRole('alert')).toContainText('enteros iguales o mayores que cero');
    await expect(dialog).toBeVisible();
    await expect(row).toContainText('Sin configurar');
    expect(requests.configure).toHaveLength(0);
  });


  test('missing required price input must never be treated as a zero-cost product', async ({ page }) => {
    const { requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Unpriced product');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.locator('[name="price"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect(dialog.getByRole('alert')).toContainText('El precio debe ser un número');
    await expect(dialog).toBeVisible();
    expect(requests.createProduct).toHaveLength(0);
  });

  test('missing required physical stock input must not turn unknown stock into zero', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-' + productId);
    await row.getByRole('button', { name: 'Configurar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await dialog.getByLabel('SKU').fill('CERA-1');
    await dialog.getByLabel('Umbral de reposición').fill('2');
    await dialog.locator('[name="onHand"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(dialog.getByRole('alert')).toContainText('enteros iguales o mayores que cero');
    await expect(dialog).toBeVisible();
    await expect(row).toContainText('Sin configurar');
    expect(requests.configure).toHaveLength(0);
  });


  test('catalog is saved only once and warns when authoritative list reload fails after creation', async ({ page }) => {
    const { createdProductId, requests } = await bootAdminInventory(page);
    let catalogWriteObserved = false;
    let injectedReadFailure = false;
    await page.route('**/api/v1/catalog', route => {
      if (route.request().method() === 'POST') catalogWriteObserved = true;
      if (route.request().method() === 'GET' && catalogWriteObserved && !injectedReadFailure) {
        injectedReadFailure = true;
        return route.fulfill({ status: 503, contentType: 'application/json',
          body: JSON.stringify({ message: 'list refresh temporarily unavailable' }) });
      }
      return route.fallback();
    });
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Artículo ya guardado');
    await dialog.getByLabel('Precio').fill('7500');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect.poll(() => requests.createProduct.length).toBe(1);
    await expect(page.getByRole('alert')).toContainText(
      'El producto se guardó, pero no se pudo actualizar la lista');
    await expect(dialog).toHaveCount(0);
    await page.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByTestId('inventory-row-' + createdProductId)).toContainText('Artículo ya guardado');
    expect(requests.createProduct).toHaveLength(1);
    expect(injectedReadFailure).toBe(true);
  });


  test('stock reload outage after successful catalog write preserves one write and explains the partial success', async ({ page }) => {
    const { createdProductId, requests } = await bootAdminInventory(page);
    let catalogWriteObserved = false;
    let failedInventoryRead = false;
    await page.route('**/api/v1/catalog', route => {
      if (route.request().method() === 'POST') catalogWriteObserved = true;
      return route.fallback();
    });
    await page.route('**/api/v1/inventory', route => {
      if (route.request().method() === 'GET' && catalogWriteObserved && !failedInventoryRead) {
        failedInventoryRead = true;
        return route.fulfill({ status: 503, contentType: 'application/json',
          body: JSON.stringify({ message: 'inventory read unavailable' }) });
      }
      return route.fallback();
    });
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Catálogo guardado');
    await dialog.getByLabel('Precio').fill('2600');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect.poll(() => requests.createProduct.length).toBe(1);
    await expect(page.getByRole('alert')).toContainText('El producto se guardó, pero no se pudo actualizar la lista');
    await expect(dialog).toHaveCount(0);
    await page.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByTestId('inventory-row-' + createdProductId)).toContainText('Catálogo guardado');
    expect(failedInventoryRead).toBe(true);
    expect(requests.createProduct).toHaveLength(1);
  });

  test('a blank but present price is unknown, never silently zero through numeric coercion', async ({ page }) => {
    const { requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Precio sin definir');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.locator('form').evaluate(form => {
      form.noValidate = true;
      form.querySelector('[name="price"]').type = 'text';
    });
    await dialog.getByLabel('Precio').fill('   ');
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect(dialog.getByRole('alert')).toContainText('El precio debe ser un número');
    await expect(dialog).toBeVisible();
    expect(requests.createProduct).toHaveLength(0);
  });

  test('missing required stock threshold is rejected rather than normalized to zero', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Configurar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await dialog.getByLabel('Stock físico inicial').fill('7');
    await dialog.locator('[name="reorderThreshold"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(dialog.getByRole('alert')).toContainText('enteros iguales o mayores que cero');
    expect(requests.configure).toHaveLength(0);
  });

  
  test('missing product identity and currency controls fail closed even when native validation is bypassed', async ({ page }) => {
    const { requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    for (const [field, expected] of [
      ['name', 'Escribe un nombre'],
      ['currency', 'La moneda debe tener tres letras']
    ]) {
      await page.getByRole('button', { name: 'Nuevo producto' }).click();
      const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
      await dialog.getByLabel('Nombre').fill('Producto íntegro');
      await dialog.getByLabel('Precio').fill('4400');
      await dialog.getByLabel('Moneda').fill('CLP');
      await dialog.locator('[name="' + field + '"]').evaluate(element => element.remove());
      await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
      await expect(dialog.getByRole('alert')).toContainText(expected);
      expect(requests.createProduct).toHaveLength(0);
      await dialog.getByRole('button', { name: 'Cancelar' }).click();
    }
  });

  test('absent optional catalog description stays null, never inferred from another field', async ({ page }) => {
    const { requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Producto sin descripción');
    await dialog.getByLabel('Precio').fill('2600');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.locator('[name="description"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect.poll(() => requests.createProduct.length).toBe(1);
    expect(requests.createProduct[0].description).toBeNull();
    await expect(dialog).toHaveCount(0);
  });

  test('missing optional SKU and stock note never generate fabricated values in a write', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId)
      .getByRole('button', { name: 'Configurar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await dialog.getByLabel('Stock físico inicial').fill('8');
    await dialog.getByLabel('Umbral de reposición').fill('2');
    await dialog.locator('[name="sku"]').evaluate(element => element.remove());
    await dialog.locator('[name="note"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect.poll(() => requests.configure.length).toBe(1);
    expect(requests.configure[0]).toEqual({
      sku: null, trackingEnabled: true, onHand: 8, reorderThreshold: 2, note: null
    });
    await expect(dialog).toHaveCount(0);
  });

  test('sparse successful catalog response carries entered name and currency into the independent stock step', async ({ page }) => {
    const { createdProductId, productId, requests } = await bootAdminInventory(page);
    let catalog = [{
      id: productId, kind: 'PRODUCT', name: 'Cera mate',
      description: 'Cera profesional', price: 5990, currency: 'CLP', active: true
    }];
    await page.route('**/api/v1/catalog', route => {
      if (route.request().method() === 'GET') return route.fulfill(json(catalog));
      if (route.request().method() !== 'POST') return route.fallback();
      const input = route.request().postDataJSON();
      requests.createProduct.push(input);
      catalog = [...catalog, { ...input, id: createdProductId }];
      return route.fulfill({ ...json({ id: createdProductId }), status: 201 });
    });
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Bodega sin detalles');
    await dialog.getByLabel('Precio').fill('4200');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.getByRole('button', { name: 'Crear y configurar stock' }).click();
    const stockDialog = page.getByRole('dialog', { name: 'Configurar stock · Bodega sin detalles' });
    await expect(stockDialog).toBeVisible();
    await expect(page.getByTestId('inventory-row-' + createdProductId)).toContainText('Bodega sin detalles');
    await stockDialog.getByRole('button', { name: 'Cancelar' }).click();
    expect(requests.createProduct).toHaveLength(1);
    expect(requests.configure).toHaveLength(0);
  });

  test('catalog save cannot write after server revokes access while its editor stays open', async ({ page }) => {
    const { requests } = await bootAdminInventory(page);
    let canWrite = true;
    await page.route('**/api/v1/auth/me', route => route.fulfill(json({
      email: 'admin@demo.cl', roles: canWrite ? ['BUSINESS_ADMIN'] : ['OPERATOR']
    })));
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('No guardar sin permiso');
    await dialog.getByLabel('Precio').fill('2700');
    await dialog.getByLabel('Moneda').fill('CLP');
    canWrite = false;
    await page.getByRole('button', { name: 'Actualizar', exact: true })
      .evaluate(button => button.click());
    await expect(page.getByText('Solo lectura')).toBeVisible();
    await dialog.getByRole('button', { name: 'Crear producto', exact: true }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('alert')).toContainText('Tus permisos cambiaron');
    expect(requests.createProduct).toHaveLength(0);
  });

  test('stock configuration cannot write after server revokes inventory permission mid-edit', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    let canWrite = true;
    await page.route('**/api/v1/auth/me', route => route.fulfill(json({
      email: 'admin@demo.cl', roles: canWrite ? ['BUSINESS_ADMIN'] : ['OPERATOR']
    })));
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-' + productId);
    await row.getByRole('button', { name: 'Configurar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await dialog.getByLabel('Stock físico inicial').fill('12');
    await dialog.getByLabel('Umbral de reposición').fill('3');
    canWrite = false;
    await page.getByRole('button', { name: 'Actualizar', exact: true })
      .evaluate(button => button.click());
    await expect(page.getByText('Solo lectura')).toBeVisible();
    await dialog.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('alert')).toContainText('Tus permisos cambiaron');
    expect(requests.configure).toHaveLength(0);
    await expect(row).toContainText('Sin configurar');
  });



  test('a synchronous double submit creates one catalog item only', async ({ page }) => {
    const { createdProductId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await dialog.getByLabel('Nombre').fill('Artículo sin duplicados');
    await dialog.getByLabel('Precio').fill('5300');
    await dialog.getByLabel('Moneda').fill('CLP');
    await dialog.locator('form').evaluate(form => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    await expect.poll(() => requests.createProduct.length).toBe(1);
    await expect(page.getByTestId('inventory-row-' + createdProductId))
      .toContainText('Artículo sin duplicados');
    expect(requests.createProduct).toHaveLength(1);
  });

  test('simultaneous base-stock adjustments remain single-write when the optional note field is absent', async ({ page }) => {
    const { productId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-' + productId);
    await row.getByRole('button', { name: 'Configurar stock' }).click();
    const configure = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await configure.getByLabel('Stock físico inicial').fill('9');
    await configure.getByLabel('Umbral de reposición').fill('2');
    await configure.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(configure).toHaveCount(0);

    await row.getByRole('button', { name: 'Ajustar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Cera mate' });
    await dialog.getByLabel('Ajuste').fill('3');
    await dialog.locator('[name="note"]').evaluate(input => input.remove());
    await dialog.locator('form').evaluate(form => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    await expect.poll(() => requests.adjust.length).toBe(1);
    expect(requests.adjust[0]).toEqual({
      delta: 3, referenceType: 'MANUAL', referenceId: null, note: null
    });
    await expect(dialog).toHaveCount(0);
    expect(requests.adjust).toHaveLength(1);
  });


  test('reentrant formdata handlers cannot duplicate catalog creation or base stock writes', async ({ page }) => {
    const { productId, createdProductId, requests } = await bootAdminInventory(page);
    await page.goto('/app/inventory');

    // A formdata listener can synchronously trigger another submit in the
    // middle of FormData(form). The mutation lock must still permit only one.
    const submitDuringSerialization = async form => form.evaluate(element => {
      let reentered = false;
      element.addEventListener('formdata', () => {
        if (reentered) return;
        reentered = true;
        element.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      });
      element.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });

    await page.getByRole('button', { name: 'Nuevo producto' }).click();
    const productDialog = page.getByRole('dialog', { name: 'Nuevo producto' });
    await productDialog.getByLabel('Nombre').fill('Un solo registro');
    await productDialog.getByLabel('Precio').fill('6490');
    await productDialog.getByLabel('Moneda').fill('CLP');
    await submitDuringSerialization(productDialog.locator('form'));
    await expect.poll(() => requests.createProduct.length).toBe(1);
    await expect(page.getByTestId('inventory-row-' + createdProductId)).toContainText('Un solo registro');
    expect(requests.createProduct).toHaveLength(1);

    const row = page.getByTestId('inventory-row-' + productId);
    await row.getByRole('button', { name: 'Configurar stock' }).click();
    const configure = page.getByRole('dialog', { name: 'Configurar stock · Cera mate' });
    await configure.getByLabel('SKU').fill('REENTRANT-1');
    await configure.getByLabel('Stock físico inicial').fill('7');
    await configure.getByLabel('Umbral de reposición').fill('2');
    await submitDuringSerialization(configure.locator('form'));
    await expect.poll(() => requests.configure.length).toBe(1);
    await expect(configure).toHaveCount(0);
    expect(requests.configure).toHaveLength(1);

    await row.getByRole('button', { name: 'Ajustar stock' }).click();
    const adjust = page.getByRole('dialog', { name: 'Ajustar stock · Cera mate' });
    await adjust.getByLabel('Ajuste').fill('2');
    await submitDuringSerialization(adjust.locator('form'));
    await expect.poll(() => requests.adjust.length).toBe(1);
    await expect(adjust).toHaveCount(0);
    expect(requests.adjust).toHaveLength(1);
    expect(requests.adjust[0].delta).toBe(2);
  });

});
