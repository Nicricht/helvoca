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

  let restockSubscriptions = [{
    id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    customerId: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
    catalogItemId: '11111111-1111-1111-1111-111111111111',
    variantId: null,
    preferredChannel: 'WHATSAPP',
    contact: '+56911112222',
    consentGranted: true,
    consentGrantedAt: '2026-09-26T17:55:00Z',
    consentSource: 'VOICE',
    status: 'ACTIVE',
    notifiedAt: null,
    cancelledAt: null,
    createdAt: '2026-09-26T17:55:00Z'
  }];
  let pendingRestockNotifications = [];

  let alerts = [{
    id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
    catalogItemId: '11111111-1111-1111-1111-111111111111',
    variantId: null,
    type: 'OUT_OF_STOCK',
    subjectName: 'Shampoo',
    sku: 'SH-01',
    available: 0,
    reorderThreshold: 2,
    acknowledged: false,
    acknowledgedAt: null,
    createdAt: '2026-09-26T18:00:00Z'
  }];

  await page.route('**/api/v1/inventory/alerts', async route => {
    if (route.request().method() === 'GET') {
      return route.fulfill(json(alerts));
    }
    return route.continue();
  });

  await page.route('**/api/v1/inventory/alerts/cccccccc-cccc-cccc-cccc-cccccccccccc/acknowledge', async route => {
    expect(route.request().method()).toBe('POST');
    alerts = alerts.map(alert => ({
      ...alert,
      acknowledged: true,
      acknowledgedAt: '2026-09-26T18:10:00Z'
    }));
    await route.fulfill(json(alerts[0]));
  });

  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => {
    return route.fulfill(json(pendingRestockNotifications));
  });
  await page.route('**/api/v1/inventory/restock-subscriptions', route => {
    return route.fulfill(json(restockSubscriptions));
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
    restockSubscriptions = [];
    pendingRestockNotifications = [{
      id: '99999999-9999-9999-9999-999999999999',
      subscriptionId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
      customerId: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
      catalogItemId: '11111111-1111-1111-1111-111111111111',
      variantId: null,
      preferredChannel: 'WHATSAPP',
      contact: '+56911112222',
      subjectName: 'Shampoo',
      sku: 'SH-01',
      available: 9,
      status: 'PENDING',
      idempotencyKey: 'inventory-restock:eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
      createdAt: '2026-09-26T18:12:00Z'
    }];
    await route.fulfill(json(inventory[0]));
  });

  await page.goto('/inventory.html');

  await expect(page.locator('#inventoryBrand')).toHaveText('BARBERÍA NORTE');
  await expect(page.locator('#inventoryProductsCount')).toHaveText('2');
  await expect(page.locator('#inventoryConfiguredCount')).toHaveText('1 con stock configurado');
  await expect(page.locator('#inventoryAvailableTotal')).toHaveText('6');
  await expect(page.locator('#inventoryAlertsCount')).toHaveText('1 pendiente');
  await expect(page.locator('#inventoryRestockCount')).toHaveText('1 esperando');
  await expect(page.locator('#inventoryPendingNotificationCount')).toHaveText('0 avisos listos');
  await expect(page.locator('[data-restock-subscription-id="eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"]'))
    .toContainText('+56911112222');
  const stockAlert = page.locator('[data-inventory-alert-id="cccccccc-cccc-cccc-cccc-cccccccccccc"]');
  await expect(stockAlert).toContainText('Agotado');
  await expect(stockAlert).toContainText('Shampoo');
  await expect(stockAlert.getByRole('button', { name: 'Reponer stock' })).toBeVisible();
  await page.locator('#inventoryFilter').selectOption('OUT');
  await expect(page.locator('[data-inventory-product-id="11111111-1111-1111-1111-111111111111"]')).toBeVisible();
  await expect(page.locator('[data-inventory-product-id="22222222-2222-2222-2222-222222222222"]')).toHaveCount(0);
  await page.locator('#inventoryFilter').selectOption('ALL');
  await stockAlert.getByRole('button', { name: 'Marcar atendida' }).click();
  await expect(page.locator('#inventoryAlertsCount')).toHaveText('0 pendientes');
  await expect(stockAlert).toContainText('Atendida');

  const shampoo = page.locator('[data-inventory-product-id="11111111-1111-1111-1111-111111111111"]');
  await expect(shampoo).toContainText('SH-01');
  await expect(shampoo).toContainText('6');

  const wax = page.locator('[data-inventory-product-id="22222222-2222-2222-2222-222222222222"]');
  await expect(wax).toContainText('Sin configurar');
  await expect(wax.getByRole('button', { name: 'Configurar stock' })).toBeVisible();

  await shampoo.getByRole('button', { name: 'Ajustar' }).click();
  await page.locator('#inventoryAdjustForm input[name="delta"]').fill('3');
  await page.locator('#inventoryAdjustForm input[name="note"]').fill('Reposición');
  await page.getByRole('button', { name: 'Aplicar ajuste' }).click();

  await expect(shampoo).toContainText('9');
  await expect(page.locator('#inventoryAvailableTotal')).toHaveText('9');
  await expect(page.locator('#inventoryRestockCount')).toHaveText('0 esperando');
  await expect(page.locator('#inventoryPendingNotificationCount')).toHaveText('1 aviso listo');
  await expect(page.locator('[data-restock-notification-id="99999999-9999-9999-9999-999999999999"]'))
    .toContainText('Pendiente de envío');

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

  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));

  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([{
    id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
    catalogItemId: '11111111-1111-1111-1111-111111111111',
    variantId: null,
    type: 'LOW_STOCK',
    subjectName: 'Producto',
    sku: 'P-1',
    available: 1,
    reorderThreshold: 1,
    acknowledged: false,
    acknowledgedAt: null,
    createdAt: '2026-09-26T18:00:00Z'
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
  await expect(page.locator('#inventoryAlertsCount')).toHaveText('1 pendiente');
  await expect(page.locator('[data-inventory-alert-id="dddddddd-dddd-dddd-dddd-dddddddddddd"]'))
    .toContainText('Stock bajo');
  await expect(page.getByRole('button', { name: 'Marcar atendida' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Variantes' }).click();
  await expect(page.locator('[data-inventory-variant-id="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]'))
    .toContainText('Azul / M');
  await expect(page.locator('.variant-edit-btn')).toHaveCount(0);
  await expect(page.locator('.variant-adjust-btn')).toHaveCount(0);
});


test('inventory distinguishes unconfigured stock from zero stock and keeps empty automation compact', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'inventory-token'));

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({ name: 'Restaurant Demo' })));
  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: '44444444-4444-4444-4444-444444444444',
    kind: 'PRODUCT',
    name: 'Hamburguesa clásica',
    description: 'Pan, carne y queso',
    price: 7990,
    currency: 'CLP',
    active: true
  }])));
  await page.route('**/api/v1/inventory', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));

  await page.goto('/inventory.html');

  await expect(page.locator('#inventoryProductsCount')).toHaveText('1');
  await expect(page.locator('#inventoryConfiguredCount')).toHaveText('0 con stock configurado');
  await expect(page.locator('#inventoryAvailableTotal')).toHaveText('—');
  await expect(page.locator('#inventoryReservedTotal')).toHaveText('—');
  await expect(page.locator('#inventorySetupNotice')).toBeVisible();
  await expect(page.locator('#inventorySetupNotice')).toContainText('1 producto sin stock configurado');

  const product = page.locator('[data-inventory-product-id="44444444-4444-4444-4444-444444444444"]');
  await expect(product).toContainText('Sin configurar');
  await expect(product.getByRole('button', { name: 'Configurar stock' })).toBeVisible();

  await expect(page.locator('#inventoryAlertsPanel')).toHaveClass(/is-empty/);
  await expect(page.locator('#inventoryRestockPanel')).toHaveClass(/is-empty/);

  const workspaceBeforeAlerts = await page.evaluate(() => {
    const workspace = document.querySelector('#inventoryWorkspace');
    const alerts = document.querySelector('#inventoryAlertsPanel');
    return Boolean(workspace && alerts && (workspace.compareDocumentPosition(alerts) & Node.DOCUMENT_POSITION_FOLLOWING));
  });
  expect(workspaceBeforeAlerts).toBe(true);
});
