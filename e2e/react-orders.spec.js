const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

const defaultOrders = [
  {
    id: 'order-1',
    operationId: 'op-1',
    sourceReferenceId: 'wa-order-1',
    status: 'CONFIRMED',
    fulfillmentType: 'DELIVERY',
    contactName: 'Juan Pedido',
    contactPhone: '+56933333333',
    deliveryAddress: 'Av. Demo 123, Santiago',
    subtotal: 15990,
    deliveryFee: 3000,
    total: 18990,
    currency: 'CLP',
    source: 'WHATSAPP',
    createdAt: '2026-10-02T14:30:00Z',
    updatedAt: '2026-10-02T14:30:00Z',
    lines: [
      {
        catalogItemId: 'prod-1',
        name: 'Producto demo',
        quantity: 1,
        unitPrice: 15990,
        lineTotal: 15990,
        notes: null
      }
    ]
  },
  {
    id: 'order-2',
    operationId: 'op-2',
    sourceReferenceId: null,
    status: 'COMPLETED',
    fulfillmentType: 'PICKUP',
    contactName: 'Ana Retiro',
    contactPhone: '+56922222222',
    deliveryAddress: null,
    subtotal: 8990,
    deliveryFee: 0,
    total: 8990,
    currency: 'CLP',
    source: 'MANUAL',
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T11:00:00Z',
    lines: [
      {
        catalogItemId: 'prod-2',
        name: 'Producto retiro',
        quantity: 2,
        unitPrice: 4495,
        lineTotal: 8990,
        notes: null
      }
    ]
  }
];

async function bootOrders(page, options = {}) {
  const permissions = options.permissions || [
    'ORDERS_READ',
    'ORDERS_MANAGE',
    'ORDERS_PREPARE',
    'DELIVERIES_READ',
    'CONVERSATIONS_READ'
  ];
  const roles = options.roles || ['BUSINESS_ADMIN'];

  let currentStatus = options.initialStatus || 'CONFIRMED';
  let listCalls = 0;
  let statusCalls = 0;
  let preparationCalls = 0;
  let statusPayload = null;
  let releaseMutation;
  const mutationGate = new Promise(resolve => { releaseMutation = resolve; });

  await page.addInitScript(token => {
    sessionStorage.setItem('helvoca_access_token', token);
  }, 'orders-react-e2e');

  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'owner@negocio.cl',
    roles,
    permissions
  })));

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Negocio E2E'
  })));

  await page.route('**/api/v1/commercial/orders', async route => {
    const url = new URL(route.request().url());
    expect(url.searchParams.has('businessId')).toBe(false);
    listCalls += 1;
    if (options.ordersError) {
      return route.fulfill(json({ message: 'orders unavailable' }, 503));
    }
    const orders = (options.orders || defaultOrders).map(order =>
      order.id === 'order-1' ? { ...order, status: currentStatus } : order
    );
    return route.fulfill(json(orders));
  });

  await page.route('**/api/v1/commercial/deliveries', async route => {
    const url = new URL(route.request().url());
    expect(url.searchParams.has('businessId')).toBe(false);
    return route.fulfill(json([
      {
        id: 'delivery-1',
        operationId: 'delivery-op-1',
        orderId: 'order-1',
        status: 'CONFIRMED',
        contactName: 'Juan Pedido',
        contactPhone: '+56933333333',
        deliveryAddress: 'Av. Demo 123, Santiago',
        fee: 3000,
        currency: 'CLP',
        source: 'WHATSAPP',
        notes: null,
        createdAt: '2026-10-02T14:31:00Z',
        updatedAt: '2026-10-02T14:31:00Z'
      }
    ]));
  });

  await page.route('**/api/v1/operation-events?operationId=op-1', route => {
    if (options.contextError) {
      return route.fulfill(json({ message: 'history unavailable' }, 503));
    }
    return route.fulfill(json([
      {
        id: 'evt-2',
        eventType: 'ORDER_CONFIRMED',
        actorType: 'AI',
        channel: 'WHATSAPP',
        createdAt: '2026-10-02T14:30:00Z'
      },
      {
        id: 'evt-1',
        eventType: 'ORDER_QUOTED',
        actorType: 'AI',
        channel: 'WHATSAPP',
        createdAt: '2026-10-02T14:20:00Z'
      }
    ]));
  });

  await page.route('**/api/v1/operation-events?operationId=op-2', route =>
    route.fulfill(json([]))
  );

  await page.route('**/api/v1/messaging/conversations/wa-order-1', route => {
    if (options.contextError) {
      return route.fulfill(json({ message: 'conversation unavailable' }, 503));
    }
    return route.fulfill(json({
      conversation: {
        id: 'wa-order-1',
        sender: '+56933333333',
        openedAt: '2026-10-02T14:15:00Z',
        lastMessageAt: '2026-10-02T14:30:00Z'
      },
      messages: [
        {
          id: 'msg-1',
          direction: 'INBOUND',
          role: 'USER',
          content: 'Quiero un Producto demo.',
          createdAt: '2026-10-02T14:20:10Z'
        },
        {
          id: 'msg-2',
          direction: 'OUTBOUND',
          role: 'ASSISTANT',
          content: 'Pedido confirmado.',
          createdAt: '2026-10-02T14:20:20Z'
        }
      ]
    }));
  });

  await page.route('**/api/v1/commercial/orders/order-1/status', async route => {
    statusCalls += 1;
    const body = route.request().postDataJSON();
    expect(body).not.toHaveProperty('businessId');
    statusPayload = body;
    if (options.mutationError) {
      return route.fulfill(json({ message: 'stale order' }, options.mutationError));
    }
    if (options.delayMutation) await mutationGate;
    currentStatus = body.status;
    return route.fulfill(json({ ...defaultOrders[0], status: currentStatus }));
  });

  await page.route('**/api/v1/commercial/orders/order-1/preparation-status', async route => {
    preparationCalls += 1;
    const body = route.request().postDataJSON();
    expect(body).not.toHaveProperty('businessId');
    statusPayload = body;
    currentStatus = body.status;
    return route.fulfill(json({ ...defaultOrders[0], status: currentStatus }));
  });

  return {
    releaseMutation,
    statusPayload: () => statusPayload,
    listCalls: () => listCalls,
    statusCalls: () => statusCalls,
    preparationCalls: () => preparationCalls
  };
}

test.describe('React Orders / Operations migration', () => {
  test('renders a table-first orders workspace with search filters sorting and authoritative totals', async ({ page }) => {
    await bootOrders(page);
    await page.goto('/app/orders');

    await expect(page.getByRole('heading', { level: 1, name: 'Pedidos' })).toBeVisible();
    await expect(page.getByTestId('orders-row-order-1')).toContainText('Juan Pedido');
    await expect(page.getByTestId('orders-row-order-1')).toContainText('18.990');
    await expect(page.getByTestId('orders-row-order-1')).toContainText('Delivery');
    await expect(page.getByTestId('orders-row-order-1')).toContainText('WhatsApp');
    await expect(page.getByTestId('orders-row-order-1')).toContainText('Confirmado');

    await page.getByRole('searchbox', { name: 'Buscar pedidos' }).fill('Ana Retiro');
    await expect(page.getByTestId('orders-row-order-2')).toBeVisible();
    await expect(page.getByTestId('orders-row-order-1')).toHaveCount(0);

    await page.getByRole('searchbox', { name: 'Buscar pedidos' }).fill('');
    await page.getByLabel('Estado').selectOption('CONFIRMED');
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
    await expect(page.getByTestId('orders-row-order-2')).toHaveCount(0);

    await page.getByLabel('Estado').selectOption('ALL');
    await page.getByLabel('Origen').selectOption('MANUAL');
    await expect(page.getByTestId('orders-row-order-2')).toBeVisible();
    await expect(page.getByTestId('orders-row-order-1')).toHaveCount(0);
  });

  test('opens detail with customer lines totals fulfillment history and contextual conversation', async ({ page }) => {
    await bootOrders(page);

    const externalWrites = [];
    page.on('request', request => {
      const url = new URL(request.url());
      if (
        (url.pathname.startsWith('/api/v1/messaging/') || url.pathname.startsWith('/api/v1/calls/'))
        && request.method() !== 'GET'
      ) {
        externalWrites.push({ method: request.method(), path: url.pathname });
      }
    });

    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();

    const detail = page.getByRole('dialog', { name: /Pedido .*Juan Pedido/i });
    await expect(detail).toBeVisible();
    await expect(detail).toContainText('Juan Pedido');
    await expect(detail).toContainText('+56933333333');
    await expect(detail).toContainText('Producto demo');
    await expect(detail).toContainText('15.990');
    await expect(detail).toContainText('3.000');
    await expect(detail).toContainText('18.990');
    await expect(detail).toContainText('Av. Demo 123, Santiago');
    await expect(detail).toContainText('Delivery');
    await expect(detail).toContainText('WhatsApp');
    await expect(detail).toContainText('Pedido confirmado');
    await expect(detail).toContainText('Quiero un Producto demo.');
    await expect(detail).toContainText('ORDER_CONFIRMED');
    expect(externalWrites).toEqual([]);
  });

  test('keeps authoritative order detail useful when optional history or conversation context fails', async ({ page }) => {
    await bootOrders(page, { contextError: true });
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();

    const detail = page.getByRole('dialog', { name: /Pedido .*Juan Pedido/i });
    await expect(detail).toBeVisible();
    await expect(detail).toContainText('Producto demo');
    await expect(detail).toContainText('18.990');
    await expect(detail.getByRole('status')).toContainText(/contexto|historial|conversación/i);
  });

  test('blocks duplicate status submit and refetches authoritative state after a successful mutation', async ({ page }) => {
    const api = await bootOrders(page, { delayMutation: true });
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();

    const action = page.getByRole('button', { name: 'Empezar preparación' });
    await expect(action).toBeVisible();

    await action.evaluate(button => {
      button.click();
      button.click();
    });

    await expect.poll(api.statusCalls).toBe(1);
    await expect.poll(api.statusPayload).toEqual({ status: 'PREPARING' });
    api.releaseMutation();

    await expect.poll(api.listCalls).toBeGreaterThan(1);
    await expect(page.getByTestId('orders-row-order-1')).toContainText('Preparando');
  });

  test('respects order permissions including preparation-only and read-only roles', async ({ page }) => {
    const kitchen = await bootOrders(page, {
      roles: ['KITCHEN'],
      permissions: ['ORDERS_READ', 'ORDERS_PREPARE', 'CATALOG_READ']
    });
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();
    await page.getByRole('button', { name: 'Empezar preparación' }).click();

    await expect.poll(kitchen.preparationCalls).toBe(1);
    expect(kitchen.statusCalls()).toBe(0);

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    const staff = await bootOrders(page, {
      roles: ['STAFF'],
      permissions: ['ORDERS_READ']
    });
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();

    await expect(page.getByRole('button', { name: /prepar|cancelar|despachar|completar/i })).toHaveCount(0);
    expect(staff.statusCalls()).toBe(0);
    expect(staff.preparationCalls()).toBe(0);
  });

  test('shows bounded empty primary-error and mutation-conflict states', async ({ page }) => {
    await bootOrders(page, { orders: [] });
    await page.goto('/app/orders');
    await expect(page.getByText('Todavía no hay pedidos.')).toBeVisible();

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await bootOrders(page, { ordersError: true });
    await page.goto('/app/orders');
    await expect(page.getByRole('alert')).toContainText(/No pudimos cargar|pedidos/i);
    await expect(page.getByRole('button', { name: /reintentar/i })).toBeVisible();

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await bootOrders(page, { mutationError: 409 });
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();
    await page.getByRole('button', { name: 'Empezar preparación' }).click();
    await expect(page.getByRole('alert')).toContainText(/actualizar|conflicto|cambió/i);
    await expect(page.getByTestId('orders-row-order-1')).toContainText('Confirmado');
  });

  test('uses an adapted mobile list with full-screen detail and no page-level overflow', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await bootOrders(page);
    await page.goto('/app/orders');

    await expect(page.getByTestId('orders-mobile-list')).toBeVisible();
    await page.getByTestId('orders-mobile-order-order-1').click();

    const detail = page.getByRole('dialog', { name: /Pedido .*Juan Pedido/i });
    const box = await detail.boundingBox();
    expect(box).not.toBeNull();
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.width).toBeGreaterThanOrEqual(380);
    expect(box.x + box.width).toBeLessThanOrEqual(390);
    expect(await page.evaluate(() =>
      document.documentElement.scrollWidth <= document.documentElement.clientWidth
    )).toBe(true);
  });
});
