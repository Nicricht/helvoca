const { test, expect } = require('@playwright/test');

const CUSTOMER_ANA = '11111111-1111-1111-1111-111111111111';
const CUSTOMER_BRUNO = '22222222-2222-2222-2222-222222222222';

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

const customers = [
  {
    id: CUSTOMER_ANA,
    name: 'Ana Reserva',
    phone: '+56922222222',
    email: 'ana@example.cl',
    notes: null,
    createdAt: '2026-09-17T10:00:00Z',
    updatedAt: '2026-09-26T13:17:00Z'
  },
  {
    id: CUSTOMER_BRUNO,
    name: 'Bruno Masaje',
    phone: '+56955555555',
    email: 'bruno@example.cl',
    notes: null,
    createdAt: '2026-09-17T11:00:00Z',
    updatedAt: '2026-09-26T12:00:00Z'
  }
];

function profile(customerId = CUSTOMER_ANA) {
  return {
    customer: {
      ...customers.find(customer => customer.id === customerId),
      notes: customerId === CUSTOMER_ANA ? 'Prefiere horario de tarde' : null
    },
    bookings: customerId === CUSTOMER_ANA
      ? [
          {
            id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
            customerId: CUSTOMER_ANA,
            serviceId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
            startAt: '2026-10-03T18:00:00Z',
            endAt: '2026-10-03T18:30:00Z',
            status: 'CONFIRMED',
            source: 'AI_CALL',
            notes: null,
            createdAt: '2026-09-26T13:00:00Z',
            updatedAt: '2026-09-26T13:00:00Z'
          }
        ]
      : []
  };
}

function timeline(customerId = CUSTOMER_ANA) {
  return customerId === CUSTOMER_ANA
    ? {
        summary: {
          commercialStage: 'PAID',
          selectedProduct: 'Zapatilla Urban',
          selectedVariantId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          selectedVariant: 'Negro / 42',
          orderStatus: 'CONFIRMED',
          paymentStatus: 'SUCCEEDED',
          inventoryStatus: 'CONSUMED',
          lastChannel: 'WHATSAPP'
        },
        events: [
          {
            at: '2026-09-26T13:17:00Z',
            type: 'OUTBOUND',
            channel: 'WHATSAPP',
            title: 'Confirmación de pago',
            detail: 'Mensaje preparado',
            status: 'PREPARED',
            operationId: 'dddddddd-dddd-dddd-dddd-dddddddddddd'
          },
          {
            at: '2026-09-26T13:12:00Z',
            type: 'ORDER',
            channel: 'WHATSAPP',
            title: 'Pedido',
            detail: 'Zapatilla Urban',
            status: 'CONFIRMED',
            operationId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'
          },
          {
            at: '2026-09-26T13:05:00Z',
            type: 'WHATSAPP',
            channel: 'WHATSAPP',
            title: 'Conversación WhatsApp',
            detail: 'Continuó la atención por WhatsApp',
            status: 'ACTIVE',
            operationId: null
          },
          {
            at: '2026-09-26T13:00:00Z',
            type: 'CALL',
            channel: 'VOICE',
            title: 'Llamada',
            detail: 'Consulta de producto',
            status: 'COMPLETED',
            operationId: null
          }
        ]
      }
    : { summary: {}, events: [] };
}

async function bootCustomers(page, options = {}) {
  await page.addInitScript(token => {
    sessionStorage.setItem('helvoca_access_token', token);
  }, options.token || 'customers-react-e2e');

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) {
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'expired' })
      });
    }
    return route.fulfill(json({
      email: 'admin@negocio.cl',
      roles: options.roles || ['BUSINESS_ADMIN']
    }));
  });

  await page.route('**/api/v1/customers', async route => {
    if (route.request().method() === 'GET') {
      if (options.expired) {
        return route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'expired' })
        });
      }
      if (options.listError) {
        return route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'customers unavailable' })
        });
      }
      return route.fulfill(json(options.empty ? [] : customers));
    }

    if (route.request().method() === 'POST') {
      if (options.mutationError) {
        return route.fulfill({
          status: options.mutationError,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'mutation failed' })
        });
      }
      const payload = route.request().postDataJSON();
      return route.fulfill({
        ...json({
          id: '33333333-3333-3333-3333-333333333333',
          ...payload,
          createdAt: '2026-10-02T16:00:00Z',
          updatedAt: '2026-10-02T16:00:00Z'
        }),
        status: 201
      });
    }

    return route.fallback();
  });

  await page.route('**/api/v1/customers/*/profile', route => {
    if (options.profileError) {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'profile unavailable' })
      });
    }
    const customerId = new URL(route.request().url()).pathname.split('/').at(-2);
    return route.fulfill(json(profile(customerId)));
  });

  await page.route('**/api/v1/customers/*/commercial-timeline', route => {
    if (options.timelineForbidden) {
      return route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'forbidden' })
      });
    }
    if (options.timelineError) {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'timeline unavailable' })
      });
    }
    const customerId = new URL(route.request().url()).pathname.split('/').at(-2);
    return route.fulfill(json(timeline(customerId)));
  });

  await page.route('**/api/v1/customers/export?format=*', route => route.fulfill({
    status: 200,
    contentType: 'text/csv;charset=UTF-8',
    headers: { 'Content-Disposition': 'attachment; filename="helvoca-clientes-e2e.csv"' },
    body: '\uFEFFID,Nombre\r\n"1","Ana Reserva"\r\n'
  }));
}

test.describe('React Customers migration', () => {
  test('renders the operational customer list and never sends a browser businessId', async ({ page }) => {
    const customerRequests = [];
    page.on('request', request => {
      if (new URL(request.url()).pathname.startsWith('/api/v1/customers')) {
        customerRequests.push(request.url());
      }
    });

    await bootCustomers(page);
    await page.goto('/app/customers');

    await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();
    await expect(page.getByRole('table', { name: 'Clientes' })).toBeVisible();
    await expect(page.getByTestId(`customer-row-${CUSTOMER_ANA}`)).toContainText('Ana Reserva');
    await expect(page.getByTestId(`customer-row-${CUSTOMER_ANA}`)).toContainText('+56922222222');
    await expect(page.getByTestId(`customer-row-${CUSTOMER_ANA}`)).toContainText('ana@example.cl');

    expect(customerRequests.length).toBeGreaterThan(0);
    expect(customerRequests.every(url => !/[?&]businessId=/i.test(url))).toBe(true);
  });

  test('searches locally by customer identity and contact data', async ({ page }) => {
    await bootCustomers(page);
    await page.goto('/app/customers');

    const search = page.getByRole('searchbox', { name: 'Buscar clientes' });
    await search.fill('Bruno');

    await expect(page.getByTestId(`customer-row-${CUSTOMER_BRUNO}`)).toBeVisible();
    await expect(page.getByTestId(`customer-row-${CUSTOMER_ANA}`)).toHaveCount(0);

    await search.fill('ana@example.cl');
    await expect(page.getByTestId(`customer-row-${CUSTOMER_ANA}`)).toBeVisible();
    await expect(page.getByTestId(`customer-row-${CUSTOMER_BRUNO}`)).toHaveCount(0);
  });

  test('opens customer context with profile bookings and commercial timeline', async ({ page }) => {
    await bootCustomers(page);
    await page.goto('/app/customers');

    await page.getByTestId(`customer-row-${CUSTOMER_ANA}`).click();

    const inspector = page.getByRole('dialog', { name: /Ana Reserva/ });
    await expect(inspector).toBeVisible();
    await expect(inspector).toContainText('+56922222222');
    await expect(inspector).toContainText('ana@example.cl');
    await expect(inspector).toContainText('Prefiere horario de tarde');
    await expect(inspector).toContainText('Reservas');
    await expect(inspector).toContainText('Confirmada');
    await expect(inspector).toContainText('Historial comercial');
    await expect(inspector).toContainText('Zapatilla Urban');
    await expect(inspector).toContainText('Pedido');
    await expect(inspector).toContainText('Conversación WhatsApp');
    await expect(inspector).toContainText('Llamada');
    await expect(inspector).toContainText('Pagado');
  });

  test('keeps the customer list usable when profile or timeline panels fail independently', async ({ page }) => {
    await bootCustomers(page, { profileError: true, timelineError: true });
    await page.goto('/app/customers');

    await page.getByTestId(`customer-row-${CUSTOMER_ANA}`).click();

    await expect(page.getByRole('table', { name: 'Clientes' })).toBeVisible();
    await expect(page.getByTestId(`customer-row-${CUSTOMER_ANA}`)).toContainText('Ana Reserva');
    const inspector = page.getByRole('dialog', { name: /Ana Reserva/ });
    await expect(inspector).toBeVisible();
    await expect(inspector).toContainText(/perfil.*no.*cargar|no.*cargar.*perfil/i);
    await expect(inspector).toContainText(/historial.*no.*cargar|no.*cargar.*historial/i);
    await expect(inspector.getByRole('button', { name: /reintentar/i }).first()).toBeVisible();
  });

  test('shows list loading empty and recoverable failure states without fabricating customers', async ({ page }) => {
    await bootCustomers(page, { empty: true });
    await page.goto('/app/customers');
    await expect(page.getByText(/no hay clientes/i)).toBeVisible();

    await page.unroute('**/api/v1/customers');
    await page.route('**/api/v1/customers', route => route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'customers unavailable' })
    }));
    await page.reload();

    await expect(page.getByRole('alert')).toContainText(/no pudimos cargar|no se pudo cargar/i);
    await expect(page.getByRole('button', { name: /reintentar/i })).toBeVisible();
    await expect(page.locator('[data-testid^="customer-row-"]')).toHaveCount(0);
  });

  test('limits create edit and export actions to roles that can manage customers', async ({ page }) => {
    await bootCustomers(page, { roles: ['OPERATOR'] });
    await page.goto('/app/customers');

    await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();
    await expect(page.getByRole('button', { name: /nuevo cliente/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /editar cliente/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /exportar/i })).toHaveCount(0);
  });

  test('redirects to auth when the session expires', async ({ page }) => {
    await bootCustomers(page, { expired: true });
    await page.goto('/app/customers');

    await expect(page).toHaveURL('http://127.0.0.1:4173/');
  });

  test('stays contained at desktop tablet and mobile widths', async ({ page }) => {
    await bootCustomers(page);

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/customers');
      await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth
      )).toBe(true);
    }
  });
});
