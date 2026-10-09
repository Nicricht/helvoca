const { test, expect } = require('./inventory-coverage-fixture');
const { captureInventoryVisual } = require('./inventory-visual-evidence-helper');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootVariantInventory(page, roles = ['BUSINESS_ADMIN'], permissions = undefined) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'inventory-react-variants'
  );

  const productId = '44444444-4444-4444-4444-444444444444';
  const initialVariantId = '55555555-5555-5555-5555-555555555555';
  const createdVariantId = '66666666-6666-6666-6666-666666666666';

  let variants = [{
    id: initialVariantId,
    catalogItemId: productId,
    name: 'Azul / M',
    optionValuesJson: '{"color":"Azul","talla":"M"}',
    sku: 'CER-AZ-M',
    trackingEnabled: true,
    onHand: 4,
    reserved: 1,
    available: 3,
    reorderThreshold: 1,
    lowStock: false,
    active: true
  }];

  const requests = {
    create: [],
    update: [],
    adjust: [],
    deactivate: 0,
    history: 0
  };

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: roles.includes('BUSINESS_ADMIN') ? 'admin@demo.cl' : 'operator@demo.cl',
    roles,
    ...(Array.isArray(permissions) ? { permissions } : {})
  })));

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Tienda Variantes'
  })));

  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: productId,
    kind: 'PRODUCT',
    name: 'Cera premium',
    description: 'Cera por color y tamaño',
    price: 6990,
    currency: 'CLP',
    active: true
  }])));

  await page.route('**/api/v1/inventory', route => {
    if (!route.request().url().endsWith('/api/v1/inventory')) return route.continue();
    return route.fulfill(json([{
      id: 'stock-variant-product',
      catalogItemId: productId,
      sku: 'CER-BASE',
      trackingEnabled: true,
      onHand: 10,
      reserved: 1,
      available: 9,
      reorderThreshold: 2,
      lowStock: false
    }]));
  });

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));

  await page.route(`**/api/v1/inventory/${productId}/variants/${createdVariantId}/movements`, route => {
    requests.history += 1;
    return route.fulfill(json([{
      id: 'move-variant-1',
      type: 'ADJUSTMENT',
      quantityDelta: 2,
      reservedDelta: 0,
      onHandAfter: 7,
      reservedAfter: 0,
      note: 'Reposición variante',
      createdAt: '2026-10-02T14:00:00Z'
    }]));
  });

  await page.route(`**/api/v1/inventory/${productId}/variants/${createdVariantId}/deactivate`, async route => {
    requests.deactivate += 1;
    variants = variants.map(variant =>
      variant.id === createdVariantId ? { ...variant, active: false } : variant
    );
    return route.fulfill(json(variants.find(variant => variant.id === createdVariantId)));
  });

  await page.route(`**/api/v1/inventory/${productId}/variants/${createdVariantId}/adjustments`, async route => {
    const body = route.request().postDataJSON();
    requests.adjust.push(body);
    variants = variants.map(variant =>
      variant.id === createdVariantId
        ? { ...variant, onHand: variant.onHand + body.delta, available: variant.available + body.delta }
        : variant
    );
    return route.fulfill(json(variants.find(variant => variant.id === createdVariantId)));
  });

  await page.route(`**/api/v1/inventory/${productId}/variants/${createdVariantId}`, async route => {
    if (route.request().method() !== 'PUT') return route.continue();
    const body = route.request().postDataJSON();
    requests.update.push(body);
    variants = variants.map(variant =>
      variant.id === createdVariantId
        ? {
            ...variant,
            ...body,
            reserved: variant.reserved,
            available: body.onHand - variant.reserved,
            lowStock: body.onHand - variant.reserved <= body.reorderThreshold
          }
        : variant
    );
    return route.fulfill(json(variants.find(variant => variant.id === createdVariantId)));
  });

  await page.route(`**/api/v1/inventory/${productId}/variants`, async route => {
    if (route.request().method() === 'GET') {
      return route.fulfill(json(variants));
    }

    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      requests.create.push(body);
      const created = {
        id: createdVariantId,
        catalogItemId: productId,
        ...body,
        reserved: 0,
        available: body.onHand,
        lowStock: body.onHand <= body.reorderThreshold
      };
      variants = [...variants, created];
      return route.fulfill({ ...json(created), status: 201 });
    }

    return route.continue();
  });

  await page.route(`**/api/v1/inventory/${productId}/movements`, route => route.fulfill(json([])));

  return { productId, initialVariantId, createdVariantId, requests };
}

test.describe('React Inventory variants', () => {
  test('admin completes the variant lifecycle from the React inventory', async ({ page }) => {
    const { productId, createdVariantId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');

    const productRow = page.getByTestId(`inventory-row-${productId}`);
    await productRow.getByRole('button', { name: 'Variantes' }).click();

    const variantsDialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await expect(variantsDialog).toBeVisible();

    const initialVariant = variantsDialog.getByTestId('inventory-variant-55555555-5555-5555-5555-555555555555');
    await expect(initialVariant).toContainText('Azul / M');
    await expect(initialVariant).toContainText('CER-AZ-M');
    await expect(initialVariant).toContainText('Disponible: 3');

    await variantsDialog.getByRole('button', { name: 'Nueva variante' }).click();
    await variantsDialog.getByLabel('Nombre de variante').fill('Rojo / L');
    await variantsDialog.getByRole('button', { name: 'Agregar característica' }).click();
    await variantsDialog.getByRole('button', { name: 'Agregar característica' }).click();
    await variantsDialog.getByRole('textbox', { name: 'Característica 1', exact: true }).fill('color');
    await variantsDialog.getByRole('textbox', { name: 'Característica 2', exact: true }).fill('talla');
    await variantsDialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(0).fill('Rojo');
    await variantsDialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(1).fill('L');
    await expect(variantsDialog.getByLabel('Opciones JSON')).toHaveCount(0);
    await variantsDialog.getByLabel('SKU de variante').fill('CER-ROJ-L');
    await variantsDialog.getByLabel('Stock físico inicial').fill('5');
    await variantsDialog.getByLabel('Umbral de reposición').fill('2');
    await variantsDialog.getByLabel('Nota').fill('Alta inicial');
    await variantsDialog.getByRole('button', { name: 'Crear variante' }).click();

    await expect.poll(() => requests.create.length).toBe(1);
    expect(requests.create[0]).toEqual({
      name: 'Rojo / L',
      optionValuesJson: '{"color":"Rojo","talla":"L"}',
      sku: 'CER-ROJ-L',
      trackingEnabled: true,
      onHand: 5,
      reorderThreshold: 2,
      active: true,
      note: 'Alta inicial'
    });

    const created = variantsDialog.getByTestId(`inventory-variant-${createdVariantId}`);
    await expect(created).toContainText('Rojo / L');
    await expect(created).toContainText('Disponible: 5');

    await created.getByRole('button', { name: 'Editar' }).click();
    await variantsDialog.getByLabel('Nombre de variante').fill('Rojo / XL');
    await variantsDialog.getByLabel('Nota').fill('Corrección de talla');
    await variantsDialog.getByRole('button', { name: 'Guardar variante' }).click();

    await expect.poll(() => requests.update.length).toBe(1);
    expect(requests.update[0]).toEqual({
      name: 'Rojo / XL',
      optionValuesJson: '{"color":"Rojo","talla":"L"}',
      sku: 'CER-ROJ-L',
      trackingEnabled: true,
      onHand: 5,
      reorderThreshold: 2,
      active: true,
      note: 'Corrección de talla'
    });
    await expect(created).toContainText('Rojo / XL');

    await created.getByRole('button', { name: 'Ajustar' }).click();
    await variantsDialog.getByLabel('Ajuste de variante').fill('2');
    await variantsDialog.getByLabel('Nota de ajuste').fill('Reposición variante');
    await variantsDialog.getByRole('button', { name: 'Aplicar ajuste de variante' }).click();

    await expect.poll(() => requests.adjust.length).toBe(1);
    expect(requests.adjust[0]).toEqual({
      delta: 2,
      note: 'Reposición variante'
    });
    await expect(created).toContainText('Disponible: 7');

    await created.getByRole('button', { name: 'Historial' }).click();
    const historyDialog = page.getByRole('dialog', { name: 'Historial variante · Rojo / XL' });
    await expect(historyDialog).toContainText('Ajuste manual');
    await expect(historyDialog).toContainText('Físico +2');
    await expect(historyDialog).toContainText('Reposición variante');
    expect(requests.history).toBe(1);
    await historyDialog.getByRole('button', { name: 'Cerrar historial' }).click();

    await created.getByRole('button', { name: 'Desactivar' }).click();
    await expect.poll(() => requests.deactivate).toBe(1);
    await expect(created).toContainText('Inactiva');
  });

  test('duplicate and incomplete characteristic rows never trigger a variant write', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId(`inventory-row-${productId}`).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('Nueva presentación');
    await dialog.getByLabel('SKU de variante').fill('PRES-DUP');
    await dialog.getByRole('button', { name: 'Agregar característica' }).click();
    await dialog.getByRole('button', { name: 'Agregar característica' }).click();
    await dialog.getByRole('textbox', { name: 'Característica 1', exact: true }).fill('color');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(0).fill('Azul');
    await dialog.getByRole('textbox', { name: 'Característica 2', exact: true }).fill('color');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(1).fill('Rojo');
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('repetida');
    expect(requests.create).toHaveLength(0);
    await dialog.getByRole('textbox', { name: 'Característica 2', exact: true }).fill('talla');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(1).fill('');
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('nombre y un valor');
    expect(requests.create).toHaveLength(0);
  });

  test('editing preserves advanced legacy nested options without showing raw JSON', async ({ page }) => {
    const { productId, initialVariantId } = await bootVariantInventory(page);
    const advanced = {
      id: initialVariantId,
      catalogItemId: productId,
      name: 'Azul / M',
      optionValuesJson: '{"color":"Azul","embalaje":{"tipo":"Caja"},"cantidad":2}',
      sku: 'CER-AZ-M', trackingEnabled: true, onHand: 4, reserved: 1,
      available: 3, reorderThreshold: 1, lowStock: false, active: true
    };
    await page.route(`**/api/v1/inventory/${productId}/variants`, route => {
      if (route.request().method() === 'GET') return route.fulfill(json([advanced]));
      return route.fallback();
    });
    const updates = [];
    await page.route(`**/api/v1/inventory/${productId}/variants/${initialVariantId}`, route => {
      if (route.request().method() !== 'PUT') return route.fallback();
      updates.push(route.request().postDataJSON());
      return route.fulfill(json({ ...advanced, ...updates[updates.length - 1] }));
    });
    await page.goto('/app/inventory');
    await page.getByTestId(`inventory-row-${productId}`).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByTestId(`inventory-variant-${initialVariantId}`)
      .getByRole('button', { name: 'Editar' }).click();
    await expect(dialog.getByText('Valor avanzado guardado: se conservará sin cambios.')).toBeVisible();
    await expect(dialog.getByLabel('Opciones JSON')).toHaveCount(0);
    await dialog.getByLabel('Nombre de variante').fill('Azul / M en caja');
    await dialog.getByRole('button', { name: 'Guardar variante' }).click();
    await expect.poll(() => updates.length).toBe(1);
    expect(JSON.parse(updates[0].optionValuesJson)).toEqual({
      color: 'Azul', embalaje: { tipo: 'Caja' }, cantidad: 2
    });
  });

  test('operator can inspect variants and their history without mutation controls', async ({ page }) => {
    const { productId } = await bootVariantInventory(page, ['OPERATOR']);
    await page.goto('/app/inventory');

    const productRow = page.getByTestId(`inventory-row-${productId}`);
    await productRow.getByRole('button', { name: 'Variantes' }).click();

    const variantsDialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await expect(variantsDialog.getByText('Azul / M')).toBeVisible();
    await expect(variantsDialog.getByRole('button', { name: 'Nueva variante' })).toHaveCount(0);
    await expect(variantsDialog.getByRole('button', { name: 'Editar' })).toHaveCount(0);
    await expect(variantsDialog.getByRole('button', { name: 'Ajustar' })).toHaveCount(0);
    await expect(variantsDialog.getByRole('button', { name: 'Desactivar' })).toHaveCount(0);
    await expect(variantsDialog.getByRole('button', { name: 'Historial' })).toBeVisible();
  });

  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 390, height: 844 }
  ]) {
    test('captures exact-head variant list, editor and duplicate validation at ' + viewport.width + 'px', async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.emulateMedia({ reducedMotion: 'reduce' });
      const { productId, requests } = await bootVariantInventory(page);
      await page.goto('/app/inventory');
      await page.getByTestId('inventory-row-' + productId)
        .getByRole('button', { name: 'Variantes' }).click();
      const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
      await expect(dialog.getByText('Azul / M')).toBeVisible();
      await captureInventoryVisual(page, 'variant-list');

      await dialog.getByRole('button', { name: 'Nueva variante' }).click();
      await dialog.getByRole('button', { name: 'Agregar característica' }).click();
      await dialog.getByRole('button', { name: 'Agregar característica' }).click();
      await expect(dialog.getByRole('textbox', { name: 'Característica 1', exact: true })).toBeVisible();
      await captureInventoryVisual(page, 'variant-editor');

      await dialog.getByLabel('Nombre de variante').fill('Nueva presentación');
      await dialog.getByLabel('SKU de variante').fill('PRES-DUP');
      await dialog.getByRole('textbox', { name: 'Característica 1', exact: true }).fill('color');
      await dialog.getByRole('textbox', { name: 'Característica 2', exact: true }).fill('color');
      await dialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(0).fill('Azul');
      await dialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(1).fill('Rojo');
      await dialog.getByRole('button', { name: 'Crear variante' }).click();
      await expect(dialog.getByRole('alert')).toContainText('repetida');
      expect(requests.create).toHaveLength(0);
      await captureInventoryVisual(page, 'variant-duplicate-error');
    });
  }


  test('malformed legacy variant characteristics prevent unsafe writes without exposing raw JSON', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    const damaged = {
      id: initialVariantId, catalogItemId: productId, name: 'Azul / M',
      optionValuesJson: '{"incomplete":', sku: 'CER-AZ-M',
      trackingEnabled: true, onHand: 4, reserved: 1,
      available: 3, reorderThreshold: 1, lowStock: false, active: true
    };
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() === 'GET') return route.fulfill(json([damaged]));
      return route.fallback();
    });
    const writes = [];
    await page.route('**/api/v1/inventory/' + productId + '/variants/' + initialVariantId, route => {
      if (route.request().method() === 'PUT') writes.push(route.request().postDataJSON());
      return route.fulfill(json(damaged));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByTestId('inventory-variant-' + initialVariantId).getByRole('button', { name: 'Editar' }).click();
    await expect(dialog.getByRole('alert').filter({ hasText: 'formato no compatible' })).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Agregar característica' })).toBeDisabled();
    await expect(dialog.getByLabel('Opciones JSON')).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Guardar variante' }).click();
    await expect(dialog.getByRole('alert').filter({ hasText: 'No se guardaron cambios' })).toBeVisible();
    expect(writes).toHaveLength(0);
    expect(requests.update).toHaveLength(0);
  });

  test('zero variant adjustment is rejected and cancellation does not call variant write endpoints', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByTestId('inventory-variant-' + initialVariantId).getByRole('button', { name: 'Ajustar' }).click();
    await dialog.getByLabel('Ajuste de variante').fill('0');
    await dialog.getByRole('button', { name: 'Aplicar ajuste de variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('entero distinto de cero');
    expect(requests.adjust).toHaveLength(0);
    await dialog.getByRole('button', { name: 'Cancelar' }).click();
    await expect(dialog.getByRole('heading', { name: 'Ajustar · Azul / M' })).toHaveCount(0);
    expect(requests.adjust).toHaveLength(0);

    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByRole('button', { name: 'Agregar característica' }).click();
    await dialog.getByRole('textbox', { name: 'Característica 1', exact: true }).fill('color');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).fill('Azul');
    await dialog.getByRole('button', { name: 'Quitar característica color' }).click();
    await expect(dialog.getByRole('textbox', { name: 'Característica 1', exact: true })).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Cancelar' }).click();
    expect(requests.create).toHaveLength(0);
    expect(requests.update).toHaveLength(0);
    expect(requests.adjust).toHaveLength(0);
  });



  test('admin without explicit inventory grant can read variants but cannot mutate them', async ({ page }) => {
    const { productId, initialVariantId, requests } =
      await bootVariantInventory(page, ['BUSINESS_ADMIN'], []);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId)
      .getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await expect(dialog.getByTestId('inventory-variant-' + initialVariantId)).toContainText('Azul / M');
    await expect(dialog.getByRole('button', { name: 'Nueva variante' })).toHaveCount(0);
    await expect(dialog.getByRole('button', { name: 'Editar' })).toHaveCount(0);
    await expect(dialog.getByRole('button', { name: 'Ajustar' })).toHaveCount(0);
    await expect(dialog.getByRole('button', { name: 'Desactivar' })).toHaveCount(0);
    await expect(dialog.getByRole('button', { name: 'Historial' })).toBeVisible();
    expect(requests.create).toHaveLength(0);
    expect(requests.update).toHaveLength(0);
    expect(requests.adjust).toHaveLength(0);
    expect(requests.deactivate).toBe(0);
  });

  test('variant GET failure is reported and reopening after recovery shows authoritative variants', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    let fail = true;
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() !== 'GET') return route.fallback();
      if (fail) return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'variants temporarily unavailable' })
      });
      return route.fallback();
    });
    await page.goto('/app/inventory');
    const row = page.getByTestId('inventory-row-' + productId);
    await row.getByRole('button', { name: 'Variantes' }).click();
    let dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await expect(dialog.getByRole('alert')).toBeVisible();
    await expect(dialog.getByTestId('inventory-variant-' + initialVariantId)).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Cerrar', exact: true }).click();
    await expect(dialog).toHaveCount(0);

    fail = false;
    await row.getByRole('button', { name: 'Variantes' }).click();
    dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await expect(dialog.getByTestId('inventory-variant-' + initialVariantId)).toContainText('Azul / M');
    await expect(dialog.getByRole('alert')).toHaveCount(0);
    expect(requests.create).toHaveLength(0);
    expect(requests.update).toHaveLength(0);
  });



  for (const malformed of ['[]', 'null', '"just a string"']) {
    test('legacy non-object characteristic data ' + malformed + ' cannot cause variant writes', async ({ page }) => {
      const { productId, initialVariantId, requests } = await bootVariantInventory(page);
      const badVariant = {
        id: initialVariantId, catalogItemId: productId, name: 'Azul / M',
        optionValuesJson: malformed, sku: 'CER-AZ-M',
        trackingEnabled: true, onHand: 4, reserved: 1,
        available: 3, reorderThreshold: 1, lowStock: false, active: true
      };
      await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
        if (route.request().method() === 'GET') return route.fulfill(json([badVariant]));
        return route.fallback();
      });
      const writes = [];
      await page.route('**/api/v1/inventory/' + productId + '/variants/' + initialVariantId, route => {
        if (route.request().method() !== 'PUT') return route.fallback();
        writes.push(route.request().postDataJSON());
        return route.fulfill(json(badVariant));
      });
      await page.goto('/app/inventory');
      await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
      const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
      await dialog.getByTestId('inventory-variant-' + initialVariantId).getByRole('button', { name: 'Editar' }).click();
      await expect(dialog.getByRole('alert').filter({ hasText: 'formato no compatible' })).toBeVisible();
      await expect(dialog.getByRole('button', { name: 'Agregar característica' })).toBeDisabled();
      await dialog.getByRole('button', { name: 'Guardar variante' }).click();
      await expect(dialog.getByRole('alert').filter({ hasText: 'No se guardaron cambios' })).toBeVisible();
      expect(writes).toHaveLength(0);
      expect(requests.update).toHaveLength(0);
    });
  }

  test('variant create conflict keeps draft available and does not send duplicate writes', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    const rejected = [];
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() !== 'POST') return route.fallback();
      rejected.push(route.request().postDataJSON());
      return route.fulfill({
        status: 409, contentType: 'application/json',
        body: JSON.stringify({ message: 'variant version conflict' })
      });
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('Conflicto QA');
    await dialog.getByLabel('SKU de variante').fill('CONFLICT-ONE');
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect.poll(() => rejected.length).toBe(1);
    await expect(dialog.getByRole('alert')).toContainText('conflicto');
    await expect(dialog.getByLabel('SKU de variante')).toHaveValue('CONFLICT-ONE');
    expect(requests.create).toHaveLength(0);
    await dialog.getByRole('button', { name: 'Cancelar' }).click();
    expect(rejected).toHaveLength(1);
  });



  test('protected null and nested legacy characteristics remain unchanged when editable fields are changed', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    const legacy = {
      id: initialVariantId, catalogItemId: productId, name: 'Azul / M',
      optionValuesJson: '{"color":"Azul","lote":null,"medidas":["M","L"],"cantidad":2}',
      sku: 'CER-AZ-M', trackingEnabled: true, onHand: 4, reserved: 1,
      available: 3, reorderThreshold: 1, lowStock: false, active: true
    };
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() === 'GET') return route.fulfill(json([legacy]));
      return route.fallback();
    });
    const updates = [];
    await page.route('**/api/v1/inventory/' + productId + '/variants/' + initialVariantId, route => {
      if (route.request().method() !== 'PUT') return route.fallback();
      const body = route.request().postDataJSON();
      updates.push(body);
      return route.fulfill(json({ ...legacy, ...body }));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByTestId('inventory-variant-' + initialVariantId).getByRole('button', { name: 'Editar' }).click();
    await expect(dialog.getByText('Valor avanzado guardado: se conservará sin cambios.')).toHaveCount(2);
    await dialog.getByRole('textbox', { name: 'Característica 1', exact: true }).fill('color');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).nth(0).fill('Turquesa');
    await dialog.getByRole('button', { name: 'Agregar característica' }).click();
    await dialog.getByRole('textbox', { name: 'Característica 5', exact: true }).fill('material');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).last().fill('Acero');
    await dialog.getByRole('button', { name: 'Guardar variante' }).click();
    await expect.poll(() => updates.length).toBe(1);
    expect(JSON.parse(updates[0].optionValuesJson)).toEqual({
      color: 'Turquesa', lote: null, medidas: ['M', 'L'],
      cantidad: 2, material: 'Acero'
    });
    expect(requests.create).toHaveLength(0);
  });

  test('empty attribute row is omitted from serialized options without failing a valid variant creation', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('Edición sin características');
    await dialog.getByLabel('SKU de variante').fill('EMPTY-KEYS');
    await dialog.getByRole('button', { name: 'Agregar característica' }).click();
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect.poll(() => requests.create.length).toBe(1);
    expect(JSON.parse(requests.create[0].optionValuesJson)).toEqual({});
    expect(requests.create[0].sku).toBe('EMPTY-KEYS');
  });

  test('duplicate protected characteristic name cannot overwrite legacy data', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    const protectedRecord = {
      id: initialVariantId, catalogItemId: productId, name: 'Azul / M',
      optionValuesJson: '{"paquete":{"niveles":[1,2]}}',
      sku: 'CER-AZ-M', trackingEnabled: true, onHand: 4, reserved: 1,
      available: 3, reorderThreshold: 1, lowStock: false, active: true
    };
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() === 'GET') return route.fulfill(json([protectedRecord]));
      return route.fallback();
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByTestId('inventory-variant-' + initialVariantId)
      .getByRole('button', { name: 'Editar' }).click();
    await dialog.getByRole('button', { name: 'Agregar característica' }).click();
    await dialog.getByRole('textbox', { name: 'Característica 2', exact: true }).fill('paquete');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).fill('sobrescribir');
    await dialog.getByRole('button', { name: 'Guardar variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('repetida o reservada');
    expect(requests.update).toHaveLength(0);
  });



  test('message-less variant read outages remain visible and recover without inventory writes', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.evaluate(variantId => {
      const originalFetch = window.fetch.bind(window);
      let firstVariantFetch = true;
      window.fetch = (input, init) => {
        const url = typeof input === 'string' ? input : input.url;
        if (url.endsWith('/variants') && firstVariantFetch) {
          firstVariantFetch = false;
          return Promise.reject(new Error(''));
        }
        if (url.endsWith('/variants/' + variantId + '/movements')) {
          return Promise.reject(new Error(''));
        }
        return originalFetch(input, init);
      };
    }, initialVariantId);
    const row = page.getByTestId('inventory-row-' + productId);
    await row.getByRole('button', { name: 'Variantes' }).click();
    let variants = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await expect(variants.getByRole('alert')).toContainText('No pudimos cargar las variantes.');
    await variants.getByRole('button', { name: 'Cerrar', exact: true }).click();
    await row.getByRole('button', { name: 'Variantes' }).click();
    variants = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    const item = variants.getByTestId('inventory-variant-' + initialVariantId);
    await expect(item).toBeVisible();
    await item.getByRole('button', { name: 'Historial' }).click();
    const history = page.getByRole('dialog', { name: 'Historial variante · Azul / M' });
    await expect(history.getByRole('alert')).toContainText('No pudimos cargar el historial de la variante.');
    await history.getByRole('button', { name: 'Cerrar historial' }).click();
    expect(requests.create).toHaveLength(0);
    expect(requests.update).toHaveLength(0);
    expect(requests.adjust).toHaveLength(0);
  });

  test('variant history 503 error and later empty-state recovery never mutate stock', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    let fail = true;
    let reads = 0;
    await page.route('**/api/v1/inventory/' + productId + '/variants/' + initialVariantId + '/movements', route => {
      reads += 1;
      return fail
        ? route.fulfill({ status: 503, contentType: 'application/json',
            body: JSON.stringify({ message: 'history unavailable' }) })
        : route.fulfill(json([]));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    const item = dialog.getByTestId('inventory-variant-' + initialVariantId);
    await item.getByRole('button', { name: 'Historial' }).click();
    let history = page.getByRole('dialog', { name: 'Historial variante · Azul / M' });
    await expect(history.getByRole('alert')).toBeVisible();
    await history.getByRole('button', { name: 'Cerrar historial' }).click();
    await expect(history).toHaveCount(0);

    fail = false;
    await item.getByRole('button', { name: 'Historial' }).click();
    history = page.getByRole('dialog', { name: 'Historial variante · Azul / M' });
    await expect(history).toContainText('todavía no tiene movimientos');
    await expect(history.getByRole('alert')).toHaveCount(0);
    expect(reads).toBe(2);
    expect(requests.create).toHaveLength(0);
    expect(requests.update).toHaveLength(0);
    expect(requests.adjust).toHaveLength(0);
  });

  test('variant adjustment 503 preserves entered delta and allows one explicit controlled retry', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    const submissions = [];
    let fail = true;
    await page.route('**/api/v1/inventory/' + productId + '/variants/' + initialVariantId + '/adjustments', route => {
      submissions.push(route.request().postDataJSON());
      return fail
        ? route.fulfill({ status: 503, contentType: 'application/json',
            body: JSON.stringify({ message: 'temporary failure' }) })
        : route.fulfill(json({ id: initialVariantId }));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByTestId('inventory-variant-' + initialVariantId).getByRole('button', { name: 'Ajustar' }).click();
    await dialog.getByLabel('Ajuste de variante').fill('3');
    await dialog.getByLabel('Nota de ajuste').fill('Reintento explícito');
    await dialog.getByRole('button', { name: 'Aplicar ajuste de variante' }).click();
    await expect.poll(() => submissions.length).toBe(1);
    await expect(dialog.getByRole('alert')).toContainText('El servidor no pudo guardar');
    await expect(dialog.getByLabel('Ajuste de variante')).toHaveValue('3');
    expect(requests.adjust).toHaveLength(0);

    fail = false;
    await dialog.getByRole('button', { name: 'Aplicar ajuste de variante' }).click();
    await expect.poll(() => submissions.length).toBe(2);
    expect(submissions).toEqual([
      { delta: 3, note: 'Reintento explícito' },
      { delta: 3, note: 'Reintento explícito' }
    ]);
    await expect(dialog.getByRole('heading', { name: 'Ajustar · Azul / M' })).toHaveCount(0);
    expect(requests.adjust).toHaveLength(0);
  });

  test('tampered hidden legacy JSON or missing attribute value prevents variant writes', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('QA validación adversarial');
    await dialog.getByLabel('SKU de variante').fill('QA-ADV');
    const baseline = dialog.locator('input[name="variantOptionsBaseline"]');
    await baseline.evaluate(element => { element.value = '['; });
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('No pudimos interpretar');
    expect(requests.create).toHaveLength(0);

    await baseline.evaluate(element => { element.value = '{}'; });
    await dialog.getByRole('button', { name: 'Agregar característica' }).click();
    await dialog.getByRole('textbox', { name: 'Característica 1', exact: true }).fill('color');
    await dialog.getByRole('textbox', { name: 'Valor', exact: true }).fill('Azul');
    await dialog.locator('input[name="variantOptionValue"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Revisa las características');
    expect(requests.create).toHaveLength(0);
  });



  test('empty legacy options are editable, and an absent hidden baseline serializes to an empty object', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    const emptyLegacy = {
      id: initialVariantId, catalogItemId: productId,
      name: 'Azul / M', optionValuesJson: '', sku: 'CER-AZ-M',
      trackingEnabled: true, onHand: 4, reserved: 1,
      available: 3, reorderThreshold: 1, lowStock: false, active: true
    };
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() === 'GET') return route.fulfill(json([emptyLegacy]));
      return route.fallback();
    });
    const updates = [];
    await page.route('**/api/v1/inventory/' + productId + '/variants/' + initialVariantId, route => {
      if (route.request().method() !== 'PUT') return route.fallback();
      updates.push(route.request().postDataJSON());
      return route.fulfill(json({ ...emptyLegacy, ...updates[updates.length - 1] }));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByTestId('inventory-variant-' + initialVariantId)
      .getByRole('button', { name: 'Editar' }).click();
    await expect(dialog.getByRole('alert')).toHaveCount(0);
    await expect(dialog.getByRole('button', { name: 'Agregar característica' })).toBeEnabled();
    await dialog.locator('input[name="variantOptionsBaseline"]').evaluate(input => input.remove());
    await dialog.getByLabel('Nombre de variante').fill('Sin opciones antiguas');
    await dialog.getByRole('button', { name: 'Guardar variante' }).click();
    await expect.poll(() => updates.length).toBe(1);
    expect(JSON.parse(updates[0].optionValuesJson)).toEqual({});
    expect(requests.update).toHaveLength(0);
  });

  test('non-object tampering in a variant baseline is refused before any write', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('Control de integridad');
    await dialog.getByLabel('SKU de variante').fill('INTEGRITY-1');
    const baseline = dialog.locator('input[name="variantOptionsBaseline"]');
    for (const tampering of ['null', '[]', '42']) {
      await baseline.evaluate((el, value) => { el.value = value; }, tampering);
      await dialog.getByRole('button', { name: 'Crear variante' }).click();
      await expect(dialog.getByRole('alert')).toContainText('No pudimos interpretar');
      expect(requests.create).toHaveLength(0);
    }
  });

  test('SKU-specific variant conflict reports duplicate SKU and preserves the draft without replay', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    const attempts = [];
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() !== 'POST') return route.fallback();
      attempts.push(route.request().postDataJSON());
      return route.fulfill({
        status: 409, contentType: 'application/json',
        body: JSON.stringify({ message: 'SKU DUPLICATE' })
      });
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('SKU existente');
    await dialog.getByLabel('SKU de variante').fill('CER-AZ-M');
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect.poll(() => attempts.length).toBe(1);
    await expect(dialog.getByRole('alert')).toContainText('Ese SKU ya está en uso');
    await expect(dialog.getByLabel('SKU de variante')).toHaveValue('CER-AZ-M');
    await expect(dialog).toBeVisible();
    expect(requests.create).toHaveLength(0);
  });

  test('deactivation failure preserves active variant and needs an explicit successful retry', async ({ page }) => {
    const { productId, initialVariantId, requests } = await bootVariantInventory(page);
    let active = true;
    let errorNext = true;
    const attempts = [];
    const variant = () => ({
      id: initialVariantId, catalogItemId: productId, name: 'Azul / M',
      optionValuesJson: '{"color":"Azul","talla":"M"}',
      sku: 'CER-AZ-M', trackingEnabled: true,
      onHand: 4, reserved: 1, available: 3,
      reorderThreshold: 1, lowStock: false, active
    });
    await page.route('**/api/v1/inventory/' + productId + '/variants', route => {
      if (route.request().method() === 'GET') return route.fulfill(json([variant()]));
      return route.fallback();
    });
    await page.route('**/api/v1/inventory/' + productId + '/variants/' + initialVariantId + '/deactivate', route => {
      attempts.push(route.request().method());
      if (errorNext) return route.fulfill({
        status: 503, contentType: 'application/json',
        body: JSON.stringify({ message: 'deactivation unavailable' })
      });
      active = false;
      return route.fulfill(json(variant()));
    });
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    const item = dialog.getByTestId('inventory-variant-' + initialVariantId);
    await item.getByRole('button', { name: 'Desactivar' }).click();
    await expect.poll(() => attempts.length).toBe(1);
    await expect(dialog.getByRole('alert')).toContainText('El servidor no pudo guardar');
    await expect(item).toContainText('Activa');
    errorNext = false;
    await item.getByRole('button', { name: 'Desactivar' }).click();
    await expect.poll(() => attempts.length).toBe(2);
    await expect(item).toContainText('Inactiva');
    await expect(item.getByRole('button', { name: 'Desactivar' })).toHaveCount(0);
    expect(requests.deactivate).toBe(0);
  });



  test('tampered variant stock fields reject negative quantities without posting variant data', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('Manipulada');
    await dialog.getByLabel('SKU de variante').fill('MAL-01');
    await dialog.locator('form').evaluate(form => {
      form.noValidate = true;
      form.querySelector('[name="variantOnHand"]').type = 'text';
    });
    await dialog.getByLabel('Stock físico inicial').fill('-3');
    await dialog.getByLabel('Umbral de reposición').fill('1');
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('enteros iguales o mayores que cero');
    expect(requests.create).toHaveLength(0);
  });

  test('missing required variant quantity cannot silently become zero via Number(null)', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('Sin cantidad');
    await dialog.getByLabel('SKU de variante').fill('MAL-02');
    await dialog.locator('[name="variantOnHand"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('enteros iguales o mayores que cero');
    await expect(dialog).toBeVisible();
    expect(requests.create).toHaveLength(0);
  });


  test('missing variant threshold is rejected before creating any variant', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId).getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('Nombre de variante').fill('Sin umbral');
    await dialog.getByLabel('SKU de variante').fill('NO-THRESHOLD');
    await dialog.getByLabel('Stock físico inicial').fill('4');
    await dialog.locator('[name="variantReorderThreshold"]').evaluate(element => element.remove());
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('enteros iguales o mayores que cero');
    await expect(dialog).toBeVisible();
    expect(requests.create).toHaveLength(0);
  });


  test('removing required variant identity fields is rejected without submitting phantom variants', async ({ page }) => {
    const { productId, requests } = await bootVariantInventory(page);
    await page.goto('/app/inventory');
    await page.getByTestId('inventory-row-' + productId)
      .getByRole('button', { name: 'Variantes' }).click();
    const dialog = page.getByRole('dialog', { name: 'Variantes · Cera premium' });
    await dialog.getByRole('button', { name: 'Nueva variante' }).click();
    await dialog.getByLabel('SKU de variante').fill('NO-IDENTITY');
    const form = dialog.locator('form');
    await form.evaluate(element => {
      element.noValidate = true;
      element.querySelector('[name="variantName"]').remove();
    });
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Escribe un nombre para la variante');
    expect(requests.create).toHaveLength(0);

    await form.evaluate(element => {
      const name = document.createElement('input');
      name.name = 'variantName';
      name.value = 'Variante recuperada';
      element.append(name);
      element.querySelector('[name="variantSku"]').remove();
    });
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Escribe un SKU para la variante');
    await expect(dialog).toBeVisible();
    expect(requests.create).toHaveLength(0);
  });

});
