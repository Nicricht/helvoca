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
  let quoteCalls = 0;
  let quoteStatusCalls = 0;
  let quoteStatus = 'REQUESTED';
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
    if (options.ordersGate) await options.ordersGate;
    if (options.ordersError || (options.refetchError && listCalls > 1)) {
      return route.fulfill(json({ message: 'orders unavailable' }, 503));
    }
    const orders = (options.orders || defaultOrders).map(order =>
      order.id === 'order-1' ? { ...order, status: currentStatus } : order
    );
    return route.fulfill(json(orders));
  });

  await page.route('**/api/v1/commercial/quotes', async route => {
    expect(route.request().method()).toBe('GET');
    expect(new URL(route.request().url()).searchParams.has('businessId')).toBe(false);
    quoteCalls += 1;
    if (options.quotesError) return route.fulfill(json({ message: 'quotes unavailable' }, 503));
    return route.fulfill(json([{
      id: 'quote-1',
      title: 'Cotización de herramientas',
      description: 'Pedido de herramientas profesionales',
      amount: 55000,
      currency: 'CLP',
      status: quoteStatus,
      contactName: 'Camila Ferretería',
      contactPhone: '+56911111111',
      source: 'WHATSAPP',
      createdAt: '2026-10-07T12:00:00Z'
    }]));
  });

  await page.route('**/api/v1/commercial/quotes/quote-1/status', async route => {
    quoteStatusCalls += 1;
    expect(route.request().method()).toBe('PATCH');
    const body = route.request().postDataJSON();
    expect(body).toEqual({ status: 'READY' });
    if (options.quoteMutationError) return route.fulfill(json({ message: 'conflict' }, options.quoteMutationError));
    quoteStatus = body.status;
    return route.fulfill(json({ id: 'quote-1', status: quoteStatus }));
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
    return route.fulfill(json(options.events ?? [
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
    preparationCalls: () => preparationCalls,
    quoteCalls: () => quoteCalls,
    quoteStatusCalls: () => quoteStatusCalls
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

    await page.getByLabel('Origen').selectOption('ALL');
    await page.getByLabel('Orden').selectOption('OLDEST');
    const rows = page.locator('[data-testid^="orders-row-"]');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toHaveAttribute('data-testid', 'orders-row-order-2');
    await expect(rows.nth(1)).toHaveAttribute('data-testid', 'orders-row-order-1');
  });

  test('keeps a visible loading state while the authoritative orders request is pending', async ({ page }) => {
    let releaseOrders;
    const ordersGate = new Promise(resolve => { releaseOrders = resolve; });
    await bootOrders(page, { ordersGate });

    await page.goto('/app/orders');
    await expect(page.getByRole('status')).toContainText('Cargando pedidos');

    releaseOrders();
    await expect(page.getByRole('heading', { level: 1, name: 'Pedidos' })).toBeVisible();
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
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

    await expect(page.getByRole('dialog', { name: /Pedido .*Juan Pedido/i }).getByRole('button', { name: /prepar|cancelar|despachar|completar/i })).toHaveCount(0);
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

  test('prioritizes the order workspace over a giant hero and refreshes only on request', async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 });
    const api = await bootOrders(page);
    await page.goto('/app/orders');

    await expect(page.getByRole('region', { name: 'Centro de operaciones RecepVoz' })).toHaveCount(0);
    const summary = page.getByRole('region', { name: 'Resumen de pedidos' });
    const workspace = page.getByRole('heading', { name: 'Flujo operativo' });
    await expect(summary).toBeVisible();
    await expect(workspace).toBeVisible();
    const summaryBounds = await summary.boundingBox();
    const workspaceBounds = await workspace.boundingBox();
    expect(summaryBounds).not.toBeNull();
    expect(workspaceBounds).not.toBeNull();
    expect(summaryBounds.y).toBeLessThan(workspaceBounds.y);
    expect(workspaceBounds.y).toBeLessThan(620);

    await expect(page.getByText('FLUJO EN TIEMPO REAL')).toHaveCount(0);
    await page.getByRole('button', { name: 'Actualizar pedidos' }).click();
    await expect.poll(api.listCalls).toBe(2);
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
  });

  test('makes the three summary metrics actionable without introducing false order states', async ({ page }) => {
    await bootOrders(page);
    await page.goto('/app/orders');

    await page.getByRole('button', { name: 'Mostrar pedidos activos' }).click();
    await expect(page.getByRole('button', { name: 'Mostrar pedidos activos' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
    await expect(page.getByTestId('orders-row-order-2')).toHaveCount(0);

    await page.getByRole('button', { name: 'Mostrar pedidos preparando' }).click();
    await expect(page.getByTestId('orders-row-order-1')).toHaveCount(0);
    await page.getByLabel('Estado').selectOption('ALL');
    await expect(page.getByTestId('orders-row-order-2')).toBeVisible();

    await page.getByRole('button', { name: 'Clientes', exact: true }).click();
    await expect(page.getByTestId('orders-customer-view')).toBeVisible();
    await page.getByRole('button', { name: 'Mostrar pedidos listos' }).click();
    await expect(page.getByRole('button', { name: 'Mostrar pedidos listos' })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('orders-customer-view')).toHaveCount(0);
  });

  test('keeps previously loaded orders visible when manual refresh fails', async ({ page }) => {
    const api = await bootOrders(page, { refetchError: true });
    await page.goto('/app/orders');
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();

    await page.getByRole('button', { name: 'Actualizar pedidos' }).click();
    await expect.poll(api.listCalls).toBe(2);
    await expect(page.getByRole('alert')).toContainText('No pudimos actualizar los pedidos');
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Actualizar pedidos' })).toBeEnabled();
  });

  test('renders a compact visual reference while retaining the full authoritative UUID', async ({ page }) => {
    const id = '4fe28d31-98f4-4c81-8579-bad049c062ea';
    await bootOrders(page, {
      orders: [{ ...defaultOrders[0], id, operationId: null, sourceReferenceId: null }]
    });
    await page.goto('/app/orders');

    const row = page.getByTestId(`orders-row-${id}`);
    await expect(row).toContainText('Ref. 4fe28d31…c062ea');
    await expect(row).not.toContainText(id);
    await expect(row.getByText('Ref. 4fe28d31…c062ea')).toHaveAttribute('title', id);

    await row.click();
    const detail = page.getByRole('dialog', { name: new RegExp(id) });
    await expect(detail).toBeVisible();
    await expect(detail).toContainText('Ref. 4fe28d31…c062ea');
    await expect(detail).toContainText('18.990');
  });

  test('offers authorized commerce-specific quotes without mixing them with orders', async ({ page }) => {
    const api = await bootOrders(page, {
      permissions: ['ORDERS_READ', 'ORDERS_MANAGE', 'QUOTES_READ', 'QUOTES_MANAGE']
    });
    await page.goto('/app/orders');
    await expect(page.getByRole('button', { name: 'Cotizaciones', exact: true })).toBeVisible();
    expect(api.quoteCalls()).toBe(0);

    await page.getByRole('button', { name: 'Cotizaciones', exact: true }).click();
    const quotes = page.getByTestId('operations-quotes');
    await expect(quotes).toContainText('Cotización de herramientas');
    await expect(quotes).toContainText('Camila Ferretería');
    await expect(quotes).toContainText('55.000');
    await expect(quotes).toContainText('Solicitada');
    await expect.poll(api.quoteCalls).toBe(1);

    await quotes.getByRole('button', { name: 'Marcar lista' }).click();
    await expect.poll(api.quoteStatusCalls).toBe(1);
    await expect(quotes).toContainText('Lista');
    await page.getByRole('button', { name: 'Pedidos', exact: true }).click();
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
  });

  test('quotes are read-only for a receptionist lacking QUOTES_MANAGE', async ({ page }) => {
    const api = await bootOrders(page, {
      roles: ['RECEPTION'],
      permissions: ['ORDERS_READ', 'QUOTES_READ']
    });
    await page.goto('/app/orders');
    await page.getByRole('button', { name: 'Cotizaciones', exact: true }).click();
    await expect(page.getByTestId('operations-quotes')).toContainText('Cotización de herramientas');
    await expect(page.getByRole('button', { name: 'Marcar lista' })).toHaveCount(0);
    expect(api.quoteStatusCalls()).toBe(0);
  });

  test('hides and never fetches quotes without QUOTES_READ, and does not fetch orders without ORDERS_READ', async ({ page }) => {
    const api = await bootOrders(page, {
      roles: ['STAFF'],
      permissions: ['ORDERS_READ']
    });
    await page.goto('/app/orders');
    await expect(page.getByRole('button', { name: 'Cotizaciones', exact: true })).toHaveCount(0);
    expect(api.quoteCalls()).toBe(0);

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    const unauthorized = await bootOrders(page, {
      roles: ['PROFESSIONAL'],
      permissions: ['BOOKINGS_READ']
    });
    await page.goto('/app/orders');
    await expect(page.getByRole('alert')).toContainText('No tienes permiso para consultar pedidos');
    expect(unauthorized.listCalls()).toBe(0);
    expect(unauthorized.quoteCalls()).toBe(0);
  });

  test('supports a quote-only business user without querying unauthorized orders', async ({ page }) => {
    const api = await bootOrders(page, {
      roles: ['SALES'],
      permissions: ['QUOTES_READ', 'QUOTES_MANAGE']
    });
    await page.goto('/app/orders');
    await expect(page.getByRole('heading', { level: 1, name: 'Cotizaciones' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Pedidos', exact: true })).toHaveCount(0);
    await expect(page.getByTestId('operations-quotes')).toContainText('Cotización de herramientas');
    expect(api.listCalls()).toBe(0);
  });

  test('keeps quote data visible and reports a failed status transition', async ({ page }) => {
    const api = await bootOrders(page, {
      roles: ['SALES'],
      permissions: ['ORDERS_READ', 'QUOTES_READ', 'QUOTES_MANAGE'],
      quoteMutationError: 409
    });
    await page.goto('/app/orders');
    await page.getByRole('button', { name: 'Cotizaciones', exact: true }).click();
    const quotes = page.getByTestId('operations-quotes');
    await quotes.getByRole('button', { name: 'Marcar lista' }).click();
    await expect(quotes.getByRole('alert')).toContainText('No pudimos actualizar la cotización');
    await expect(quotes).toContainText('Solicitada');
    expect(api.quoteStatusCalls()).toBe(1);
  });

  test('periodically synchronizes visible orders from the authoritative API without manual interaction', async ({ page }) => {
    await page.clock.install();
    const api = await bootOrders(page);
    await page.goto('/app/orders');
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
    await expect(page.getByText(/Sincronización periódica cada 60 s/)).toBeVisible();
    expect(api.listCalls()).toBe(1);

    await page.clock.fastForward(60_000);
    await expect.poll(api.listCalls).toBeGreaterThanOrEqual(2);
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
  });

  test('periodically synchronizes authorized quotes while their workspace is visible', async ({ page }) => {
    await page.clock.install();
    const api = await bootOrders(page, {
      roles: ['SALES'],
      permissions: ['ORDERS_READ', 'QUOTES_READ']
    });
    await page.goto('/app/orders');
    await page.getByRole('button', { name: 'Cotizaciones', exact: true }).click();
    await expect(page.getByTestId('operations-quotes')).toContainText('Cotización de herramientas');
    expect(api.quoteCalls()).toBe(1);

    await page.clock.fastForward(60_000);
    await expect.poll(api.quoteCalls).toBeGreaterThanOrEqual(2);
  });

  test('reports offline status without fabricating an active connection', async ({ page }) => {
    await bootOrders(page);
    await page.goto('/app/orders');
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();

    await page.evaluate(() => {
      Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false });
      window.dispatchEvent(new Event('offline'));
    });
    await expect(page.getByText('Sin conexión', { exact: true })).toBeVisible();
    await expect(page.getByText(/Se conservan los últimos datos consultados/)).toBeVisible();
    await expect(page.getByTestId('orders-row-order-1')).toBeVisible();
  });

  test('identifies AI history only from actual AI-attributed operation events', async ({ page }) => {
    await bootOrders(page);
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();
    const dialog = page.getByRole('dialog', { name: /Pedido .*Juan Pedido/i });
    const ai = dialog.getByRole('region', { name: 'Actividad de IA registrada' });
    await expect(ai).toContainText('ORDER_CONFIRMED');
    await expect(ai).toContainText('WhatsApp');
    await expect(ai).toContainText('Historial registrado');
    await expect(ai).not.toContainText('Trabajando ahora');
  });

  test('never invents AI activity when only a human event exists or the history fails', async ({ page }) => {
    await bootOrders(page, {
      events: [{ id: 'human-1', eventType: 'ORDER_PREPARING', actorType: 'HUMAN',
        channel: 'MANUAL', createdAt: '2026-10-07T13:00:00Z' }]
    });
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();
    const dialog = page.getByRole('dialog', { name: /Pedido .*Juan Pedido/i });
    await expect(dialog).toContainText('ORDER_PREPARING');
    await expect(dialog.getByRole('region', { name: 'Actividad de IA registrada' })).toHaveCount(0);

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await bootOrders(page, { contextError: true });
    await page.goto('/app/orders');
    await page.getByTestId('orders-row-order-1').click();
    const failed = page.getByRole('dialog', { name: /Pedido .*Juan Pedido/i });
    await expect(failed.getByRole('status')).toContainText(/contexto|historial/i);
    await expect(failed.getByRole('region', { name: 'Actividad de IA registrada' })).toHaveCount(0);
  });

});
