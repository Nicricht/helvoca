const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootVariantInventory(page, roles = ['BUSINESS_ADMIN']) {
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
    roles
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
    await variantsDialog.getByLabel('Característica 1').fill('color');
    await variantsDialog.getByLabel('Característica 2').fill('talla');
    await variantsDialog.getByLabel('Valor').nth(0).fill('Rojo');
    await variantsDialog.getByLabel('Valor').nth(1).fill('L');
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
    await dialog.getByLabel('Característica 1').fill('color');
    await dialog.getByLabel('Valor').nth(0).fill('Azul');
    await dialog.getByLabel('Característica 2').fill('color');
    await dialog.getByLabel('Valor').nth(1).fill('Rojo');
    await dialog.getByRole('button', { name: 'Crear variante' }).click();
    await expect(dialog.getByRole('alert')).toContainText('repetida');
    expect(requests.create).toHaveLength(0);
    await dialog.getByLabel('Característica 2').fill('talla');
    await dialog.getByLabel('Valor').nth(1).fill('');
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
});
