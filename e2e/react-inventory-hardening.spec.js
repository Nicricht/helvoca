const { test, expect } = require('./inventory-coverage-fixture');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function boot(page, {
  roles = ['BUSINESS_ADMIN'],
  permissions,
  configured = false,
  configureHandler,
  adjustHandler
} = {}) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'inventory-react-hardening'
  );

  const productId = '12121212-1212-1212-1212-121212121212';
  let inventory = configured ? [{
    id: 'stock-hardening',
    catalogItemId: productId,
    sku: 'CER-01',
    trackingEnabled: true,
    onHand: 4,
    reserved: 1,
    available: 3,
    reorderThreshold: 1,
    lowStock: false
  }] : [];

  const requests = { configure: 0, adjust: 0 };

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'user@demo.cl',
    roles,
    ...(permissions ? { permissions } : {})
  })));

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '13131313-1313-1313-1313-131313131313',
    name: 'Tienda Hardening'
  })));

  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: productId,
    kind: 'PRODUCT',
    name: 'Cera control',
    description: 'Producto de prueba',
    price: 5990,
    currency: 'CLP',
    active: true
  }])));

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));
  await page.route(`**/api/v1/inventory/${productId}/movements`, route => route.fulfill(json([])));

  await page.route(`**/api/v1/inventory/${productId}/adjustments`, async route => {
    requests.adjust += 1;
    if (adjustHandler) return adjustHandler(route, inventory, next => { inventory = next; });
    return route.fulfill(json(inventory[0]));
  });

  await page.route(`**/api/v1/inventory/${productId}`, async route => {
    if (route.request().method() !== 'PUT') return route.continue();
    requests.configure += 1;
    if (configureHandler) return configureHandler(route, inventory, next => { inventory = next; });
    const body = route.request().postDataJSON();
    inventory = [{
      id: 'stock-hardening',
      catalogItemId: productId,
      sku: body.sku,
      trackingEnabled: true,
      onHand: body.onHand,
      reserved: 0,
      available: body.onHand,
      reorderThreshold: body.reorderThreshold,
      lowStock: body.onHand <= body.reorderThreshold
    }];
    return route.fulfill(json(inventory[0]));
  });

  await page.route('**/api/v1/inventory', route => {
    if (route.request().url().endsWith('/api/v1/inventory')) {
      return route.fulfill(json(inventory));
    }
    return route.continue();
  });

  return { productId, requests };
}

test.describe('React Inventory hardening', () => {
  test('warehouse permission can manage base stock without catalog-admin controls', async ({ page }) => {
    const { productId } = await boot(page, {
      roles: ['WAREHOUSE'],
      permissions: ['BUSINESS_READ', 'INVENTORY_READ', 'INVENTORY_MANAGE', 'CATALOG_READ'],
      configured: true
    });

    await page.goto('/app/inventory');

    const row = page.getByTestId(`inventory-row-${productId}`);
    await expect(row.getByRole('button', { name: 'Ajustar stock' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nuevo producto' })).toHaveCount(0);
  });

  test('409 configure conflict stays open and preserves authoritative stock', async ({ page }) => {
    const { productId, requests } = await boot(page, {
      configureHandler: route => route.fulfill(json({
        message: 'That SKU is already assigned to another inventory item'
      }, 409))
    });

    await page.goto('/app/inventory');
    const row = page.getByTestId(`inventory-row-${productId}`);

    await row.getByRole('button', { name: 'Configurar stock' }).click();
    const dialog = page.getByRole('dialog', { name: 'Configurar stock · Cera control' });
    await dialog.getByLabel('SKU').fill('DUP-01');
    await dialog.getByLabel('Stock físico inicial').fill('8');
    await dialog.getByLabel('Umbral de reposición').fill('2');
    await dialog.getByRole('button', { name: 'Guardar configuración' }).click();

    await expect(dialog.getByRole('alert')).toHaveText('Ese SKU ya está en uso. Elige otro SKU.');
    await expect(dialog).toBeVisible();
    await expect(row).toContainText('Sin configurar');
    await expect(page.getByTestId('inventory-available')).toContainText('—');
    expect(requests.configure).toBe(1);
  });

  test('500 adjustment keeps server stock unchanged and shows a recoverable message', async ({ page }) => {
    const { productId, requests } = await boot(page, {
      configured: true,
      adjustHandler: route => route.fulfill(json({ error: 'Internal Server Error' }, 500))
    });

    await page.goto('/app/inventory');
    const row = page.getByTestId(`inventory-row-${productId}`);
    await row.getByRole('button', { name: 'Ajustar stock' }).click();

    const dialog = page.getByRole('dialog', { name: 'Ajustar stock · Cera control' });
    await dialog.getByLabel('Ajuste').fill('5');
    await dialog.getByRole('button', { name: 'Aplicar ajuste' }).click();

    await expect(dialog.getByRole('alert')).toHaveText(
      'El servidor no pudo guardar el cambio. Intenta nuevamente.'
    );
    await expect(dialog).toBeVisible();
    await expect(row).toContainText('3');
    await expect(page.getByTestId('inventory-available')).toContainText('3');
    expect(requests.adjust).toBe(1);
  });

  test('synchronous duplicate submits send only one stock mutation', async ({ page }) => {
    const { productId, requests } = await boot(page, {
      configureHandler: async (route, _inventory, setInventory) => {
        const body = route.request().postDataJSON();
        await new Promise(resolve => setTimeout(resolve, 250));
        const next = [{
          id: 'stock-hardening',
          catalogItemId: productId,
          sku: body.sku,
          trackingEnabled: true,
          onHand: body.onHand,
          reserved: 0,
          available: body.onHand,
          reorderThreshold: body.reorderThreshold,
          lowStock: false
        }];
        setInventory(next);
        return route.fulfill(json(next[0]));
      }
    });

    await page.goto('/app/inventory');
    const row = page.getByTestId(`inventory-row-${productId}`);
    await row.getByRole('button', { name: 'Configurar stock' }).click();

    const dialog = page.getByRole('dialog', { name: 'Configurar stock · Cera control' });
    await dialog.getByLabel('SKU').fill('CER-LOCK');
    await dialog.getByLabel('Stock físico inicial').fill('6');
    await dialog.getByLabel('Umbral de reposición').fill('2');

    await dialog.locator('form').evaluate(form => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });

    await expect.poll(() => requests.configure).toBe(1);
    await expect(row).toContainText('CER-LOCK');
  });

  test('two refresh clicks in one event task start exactly one authoritative data reload', async ({ page }) => {
    await boot(page, { configured: true });
    await page.goto('/app/inventory');
    await expect(page.getByTestId('inventory-available')).toContainText('3');
    let refreshReads = 0;
    await page.route('**/api/v1/inventory', async route => {
      if (new URL(route.request().url()).pathname !== '/api/v1/inventory') return route.fallback();
      refreshReads += 1;
      await new Promise(resolve => setTimeout(resolve, 180));
      return route.fallback();
    });
    const refresh = page.getByRole('button', { name: 'Actualizar', exact: true });
    await refresh.evaluate(button => {
      button.click();
      button.click();
    });
    await expect.poll(() => refreshReads).toBe(1);
    await expect(refresh).toBeEnabled();
    expect(refreshReads).toBe(1);
  });

});
