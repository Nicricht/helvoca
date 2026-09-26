const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('inventory UI merges catalog with stock and allows an admin adjustment', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'inventory-token'));

  let inventory = [{
    catalogItemId: '11111111-1111-1111-1111-111111111111',
    productName: 'Shampoo',
    sku: 'SH-01',
    trackingEnabled: true,
    onHand: 8,
    reserved: 2,
    available: 6,
    reorderThreshold: 2,
    lowStock: false
  }];

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Barbería Norte',
    timezone: 'America/Santiago',
    language: 'es'
  })));
  await page.route('**/api/v1/catalog', route => route.fulfill(json([
    {
      id: '11111111-1111-1111-1111-111111111111',
      kind: 'PRODUCT',
      name: 'Shampoo',
      description: 'Shampoo profesional',
      price: 8990,
      currency: 'CLP',
      active: true
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      kind: 'PRODUCT',
      name: 'Cera',
      description: 'Cera mate',
      price: 5990,
      currency: 'CLP',
      active: true
    },
    {
      id: '33333333-3333-3333-3333-333333333333',
      kind: 'SERVICE',
      name: 'Corte',
      active: true
    }
  ])));

  await page.route('**/api/v1/inventory', route => {
    if (route.request().url().endsWith('/api/v1/inventory')) {
      return route.fulfill(json(inventory));
    }
    return route.continue();
  });

  let variants = [{
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    catalogItemId: '11111111-1111-1111-1111-111111111111',
    name: 'Negro / 42',
    optionValuesJson: '{"color":"Negro","talla":"42"}',
    sku: 'SH-BLK-42',
    trackingEnabled: true,
    onHand: 4,
    reserved: 1,
    available: 3,
    reorderThreshold: 1,
    lowStock: false,
    active: true
  }];

  await page.route('**/api/v1/inventory/11111111-1111-1111-1111-111111111111/variants', async route => {
    if (route.request().method() === 'GET') {
      return route.fulfill(json(variants));
    }
    return route.continue();
  });

  await page.route('**/api/v1/inventory/11111111-1111-1111-1111-111111111111/variants/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/adjustments', async route => {
    const body = route.request().postDataJSON();
    expect(body.delta).toBe(2);
    variants = [{
      ...variants[0],
      onHand: 6,
      available: 5
    }];
    await route.fulfill(json(variants[0]));
  });

  await page.route('**/api/v1/inventory/11111111-1111-1111-1111-111111111111/adjustments', async route => {
    const body = route.request().postDataJSON();
    expect(body.delta).toBe(3);
    expect(body.referenceType).toBe('MANUAL');
    inventory = [{
      ...inventory[0],
      onHand: 11,
      available: 9
    }];
    await route.fulfill(json(inventory[0]));
  });

  await page.goto('/inventory.html');

  await expect(page.locator('#inventoryBrand')).toHaveText('BARBERÍA NORTE');
  await expect(page.locator('#inventoryProductsCount')).toHaveText('2');
  await expect(page.locator('#inventoryConfiguredCount')).toHaveText('1 con seguimiento');

  const shampoo = page.locator('[data-inventory-product-id="11111111-1111-1111-1111-111111111111"]');
  await expect(shampoo).toContainText('SH-01');
  await expect(shampoo).toContainText('6');

  const wax = page.locator('[data-inventory-product-id="22222222-2222-2222-2222-222222222222"]');
  await expect(wax).toContainText('Sin seguimiento');
  await expect(wax.getByRole('button', { name: 'Configurar' })).toBeVisible();

  await shampoo.getByRole('button', { name: 'Ajustar' }).click();
  await page.locator('#inventoryAdjustForm input[name="delta"]').fill('3');
  await page.locator('#inventoryAdjustForm input[name="note"]').fill('Reposición');
  await page.getByRole('button', { name: 'Aplicar ajuste' }).click();

  await expect(shampoo).toContainText('9');
  await expect(page.locator('#inventoryOnHandTotal')).toHaveText('11');

  await shampoo.getByRole('button', { name: 'Variantes' }).click();
  const variantCard = page.locator('[data-inventory-variant-id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]');
  await expect(variantCard).toContainText('Negro / 42');
  await expect(variantCard).toContainText('SH-BLK-42');
  await expect(variantCard).toContainText('3');

  await variantCard.getByRole('button', { name: 'Ajustar' }).click();
  await page.locator('#inventoryAdjustForm input[name="delta"]').fill('2');
  await page.locator('#inventoryAdjustForm input[name="note"]').fill('Reposición variante');
  await page.getByRole('button', { name: 'Aplicar ajuste' }).click();

  await expect(variantCard).toContainText('5');
});

test('operator inventory is read only', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'operator-token'));
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'operator@demo.cl',
    roles: ['OPERATOR']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Tienda Demo' })));
  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: '11111111-1111-1111-1111-111111111111',
    kind: 'PRODUCT',
    name: 'Producto',
    active: true
  }])));
  await page.route('**/api/v1/inventory', route => route.fulfill(json([{
    catalogItemId: '11111111-1111-1111-1111-111111111111',
    productName: 'Producto',
    sku: 'P-1',
    trackingEnabled: true,
    onHand: 3,
    reserved: 0,
    available: 3,
    reorderThreshold: 1,
    lowStock: false
  }])));

  await page.route('**/api/v1/inventory/11111111-1111-1111-1111-111111111111/variants', route => route.fulfill(json([{
    id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    catalogItemId: '11111111-1111-1111-1111-111111111111',
    name: 'Azul / M',
    optionValuesJson: '{"color":"Azul","talla":"M"}',
    sku: 'P-AZ-M',
    trackingEnabled: true,
    onHand: 3,
    reserved: 0,
    available: 3,
    reorderThreshold: 1,
    lowStock: false,
    active: true
  }])));

  await page.goto('/inventory.html');

  await expect(page.locator('#inventoryRoleBadge')).toHaveText('Solo lectura');
  await expect(page.getByRole('button', { name: 'Ajustar' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Editar' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Variantes' }).click();
  await expect(page.locator('[data-inventory-variant-id="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]'))
    .toContainText('Azul / M');
  await expect(page.locator('.variant-edit-btn')).toHaveCount(0);
  await expect(page.locator('.variant-adjust-btn')).toHaveCount(0);
});
