const { test, expect } = require('@playwright/test');

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
  let inventory = [];
  const requests = {
    configure: [],
    adjust: []
  };

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Tienda Demo'
  })));

  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: productId,
    kind: 'PRODUCT',
    name: 'Cera mate',
    description: 'Cera profesional',
    price: 5990,
    currency: 'CLP',
    active: true
  }])));

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

  return { productId, requests };
}

test.describe('React Inventory mutations', () => {
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
});
