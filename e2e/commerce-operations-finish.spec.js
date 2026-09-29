const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mockCommerceApi(page) {
  const products = [
    {
      id: '11111111-1111-1111-1111-111111111111',
      kind: 'PRODUCT',
      name: 'Taladro percutor',
      description: '750W · mandril 13 mm',
      price: 54990,
      currency: 'CLP',
      active: true
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      kind: 'PRODUCT',
      name: 'Disco de corte',
      description: '115 mm',
      price: 2490,
      currency: 'CLP',
      active: true
    }
  ];

  let inventory = [
    {
      catalogItemId: '11111111-1111-1111-1111-111111111111',
      productName: 'Taladro percutor',
      sku: 'TAL-750',
      trackingEnabled: true,
      onHand: 7,
      reserved: 2,
      available: 5,
      reorderThreshold: 2,
      lowStock: false
    },
    {
      catalogItemId: '22222222-2222-2222-2222-222222222222',
      productName: 'Disco de corte',
      sku: 'DIS-115',
      trackingEnabled: true,
      onHand: 2,
      reserved: 1,
      available: 1,
      reorderThreshold: 2,
      lowStock: true
    }
  ];

  const orders = [
    {
      id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      operationId: 'op-a',
      sourceReferenceId: null,
      status: 'READY',
      fulfillmentType: 'DELIVERY',
      contactName: 'Camila Soto',
      contactPhone: '+56911112222',
      deliveryAddress: 'Av. Demo 123, Santiago',
      subtotal: 54990,
      deliveryFee: 3990,
      total: 58980,
      currency: 'CLP',
      source: 'WHATSAPP',
      createdAt: '2026-09-28T17:30:00Z',
      lines: [{ name: 'Taladro percutor', quantity: 1, unitPrice: 54990, lineTotal: 54990 }]
    },
    {
      id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      operationId: 'op-b',
      sourceReferenceId: null,
      status: 'CONFIRMED',
      fulfillmentType: 'PICKUP',
      contactName: 'Diego Pérez',
      contactPhone: '+56933334444',
      subtotal: 4980,
      deliveryFee: 0,
      total: 4980,
      currency: 'CLP',
      source: 'VOICE',
      createdAt: '2026-09-28T16:00:00Z',
      lines: [{ name: 'Disco de corte', quantity: 2, unitPrice: 2490, lineTotal: 4980 }]
    }
  ];

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === '/api/v1/auth/me') return route.fulfill(json({ email: 'admin@demo.cl', roles: ['BUSINESS_ADMIN'] }));
    if (path === '/api/v1/business') return route.fulfill(json({ name: 'Ferretería Norte', timezone: 'America/Santiago', language: 'es' }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({
      businessProfileConfigured: true,
      servicesConfigured: true,
      scheduleConfigured: true,
      knowledgeConfigured: true,
      humanTransferConfigured: false,
      phoneConfigured: true,
      readyForCalls: true,
      nextStep: 'OPTIONAL_HUMAN_TRANSFER'
    }));
    if (path === '/api/v1/onboarding/guide') return route.fulfill(json({
      readyForPilot: true,
      completed: 7,
      total: 7,
      progressPercent: 100,
      nextStep: null,
      steps: []
    }));
    if (path === '/api/v1/services') return route.fulfill(json([]));
    if (path === '/api/v1/business/hours') return route.fulfill(json([]));
    if (path === '/api/v1/knowledge') return route.fulfill(json([]));
    if (path === '/api/v1/ai-agent') return route.fulfill(json({ configured: true, active: true, capabilities: [] }));
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([]));
    if (path === '/api/v1/phone-numbers/provisioning/status') return route.fulfill(json({
      enabled: false, configured: false, purchaseAvailable: false, provider: 'TWILIO', message: 'E2E'
    }));
    if (path === '/api/v1/billing/status') return route.fulfill(json({
      billingEnabled: true, checkoutConfigured: true, currentPlanCode: 'PRO',
      currentPlanName: 'Pro', subscriptionStatus: 'ACTIVE', awaitingProviderVerification: false
    }));
    if (path === '/api/v1/subscription') return route.fulfill(json({
      plan: 'PRO', status: 'ACTIVE', serviceAllowed: true, includedMinutes: 500,
      usedMinutes: 0, overageMinutes: 0, billingProviderConnected: true, legacyFallback: false
    }));
    if (path === '/api/v1/public/pricing') return route.fulfill(json([]));
    if (path === '/api/v1/bookings') return route.fulfill(json([]));
    if (path === '/api/v1/customers') return route.fulfill(json([]));
    if (path === '/api/v1/commercial/orders') return route.fulfill(json(orders));
    if (path === '/api/v1/commercial/pipeline') return route.fulfill(json({ total: 0, active: 0, paid: 0, needsAction: 0, items: [] }));
    if (path === '/api/v1/operations/dashboard') return route.fulfill(json({ businessName: 'Ferretería Norte', timezone: 'America/Santiago', recentRequests: [] }));
    if (path === '/api/v1/audit') return route.fulfill(json([]));

    if (path === '/api/v1/catalog' && method === 'GET') return route.fulfill(json(products));
    if (path === '/api/v1/catalog' && method === 'POST') {
      const payload = request.postDataJSON();
      const created = {
        id: '33333333-3333-3333-3333-333333333333',
        ...payload,
        active: payload.active !== false
      };
      products.push(created);
      return route.fulfill({ status: 201, ...json(created) });
    }
    if (path.startsWith('/api/v1/catalog/') && method === 'PUT') {
      const id = path.split('/').pop();
      const payload = request.postDataJSON();
      const index = products.findIndex(item => item.id === id);
      products[index] = { ...products[index], ...payload };
      return route.fulfill(json(products[index]));
    }

    if (path === '/api/v1/inventory') return route.fulfill(json(inventory));
    if (path === '/api/v1/inventory/alerts') return route.fulfill(json([
      {
        id: 'alert-low',
        catalogItemId: '22222222-2222-2222-2222-222222222222',
        variantId: null,
        type: 'LOW_STOCK',
        subjectName: 'Disco de corte',
        sku: 'DIS-115',
        available: 1,
        reorderThreshold: 2,
        acknowledged: false,
        createdAt: '2026-09-28T18:00:00Z'
      }
    ]));
    if (path === '/api/v1/inventory/restock-subscriptions') return route.fulfill(json([]));
    if (path === '/api/v1/inventory/restock-subscriptions/notifications') return route.fulfill(json([]));
    if (/^\/api\/v1\/inventory\/[^/]+\/variants$/.test(path)) return route.fulfill(json([]));
    if (/^\/api\/v1\/inventory\/[^/]+\/movements$/.test(path)) return route.fulfill(json([]));

    return route.fulfill(json([]));
  });
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'commerce-e2e-token'));
  await mockCommerceApi(page);
});

test('inventory is an actionable product and stock workspace on desktop and mobile', async ({ page }) => {
  await page.goto('/inventory.html');

  await expect(page.getByRole('heading', { name: 'Inventario', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Nuevo producto' })).toBeVisible();
  await expect(page.locator('#inventorySearch')).toHaveAttribute('placeholder', /producto|sku/i);
  await expect(page.locator('#inventoryFilter')).toBeVisible();
  await expect(page.locator('#inventorySort')).toBeVisible();

  const lowStock = page.locator('[data-inventory-product-id="22222222-2222-2222-2222-222222222222"]');
  await expect(lowStock).toContainText('Disco de corte');
  await expect(lowStock).toContainText('$2.490');
  await expect(lowStock).toContainText('1');
  await expect(lowStock).toContainText('Stock bajo');
  await expect(lowStock.getByRole('button', { name: /reponer/i })).toBeVisible();
  await expect(lowStock.getByRole('button', { name: /editar producto/i })).toBeVisible();

  await page.locator('#inventorySort').selectOption('AVAILABLE_ASC');
  await expect(page.locator('#inventoryRows tr').first()).toContainText('Disco de corte');

  await page.getByRole('button', { name: 'Nuevo producto' }).click();
  const productDialog = page.locator('#inventoryProductDialog');
  await expect(productDialog).toBeVisible();
  await productDialog.locator('[name="name"]').fill('Broca hormigón 8 mm');
  await productDialog.locator('[name="description"]').fill('Broca de widia');
  await productDialog.locator('[name="price"]').fill('3990');
  await productDialog.locator('[name="currency"]').fill('CLP');

  const createRequest = page.waitForRequest(request =>
    request.url().endsWith('/api/v1/catalog') && request.method() === 'POST');
  await productDialog.getByRole('button', { name: 'Crear producto' }).click();
  const request = await createRequest;
  expect(request.postDataJSON()).toMatchObject({
    kind: 'PRODUCT',
    name: 'Broca hormigón 8 mm',
    description: 'Broca de widia',
    price: 3990,
    currency: 'CLP',
    active: true
  });
  await expect(page.locator('[data-inventory-product-id="33333333-3333-3333-3333-333333333333"]')).toContainText('Broca hormigón 8 mm');

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.inventory-table-wrap')).toHaveClass(/inventory-mobile-cards/);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
});

test('orders can be searched, filtered and understood by state and next action', async ({ page }) => {
  await page.goto('/?tab=orders');

  await expect(page.locator('[data-home-panel="orders"]')).toBeVisible();
  await expect(page.locator('#homeOrderSearch')).toBeVisible();
  await expect(page.locator('#homeOrderStatus')).toBeVisible();
  await expect(page.locator('#homeOrderSort')).toBeVisible();
  await expect(page.locator('#homeOrderResult')).toContainText('2');

  const ready = page.locator('[data-home-order-id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]').first();
  await expect(ready).toContainText('Camila Soto');
  await expect(ready).toContainText('Listo');
  await expect(ready).toContainText('Despachar');
  await expect(ready.locator('.home-order-status')).toHaveClass(/is-ready/);

  await page.locator('#homeOrderSearch').fill('Diego');
  await expect(page.locator('#homeOrderResult')).toContainText('1');
  await expect(page.locator('[data-home-order-id="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]').first()).toBeVisible();
  await expect(page.locator('[data-home-order-id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]').first()).toHaveCount(0);

  await page.locator('#homeOrderSearch').fill('');
  await page.locator('#homeOrderStatus').selectOption('READY');
  await expect(page.locator('#homeOrderResult')).toContainText('1');
  await expect(page.locator('[data-home-order-id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]').first()).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
});
