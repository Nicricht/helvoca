const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function seed(page, roles, permissions) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'legacy-capability-e2e'));
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'owner@negocio.cl',
    roles,
    permissions
  })));
}

test.describe('React replacement for legacy Home capabilities', () => {
  test('Agenda creates a customer inline before creating a booking', async ({ page }) => {
    await seed(page, ['BUSINESS_ADMIN'], ['BOOKINGS_MANAGE', 'CUSTOMERS_READ', 'CUSTOMERS_MANAGE']);
    let customers = [{ id: 'cust-1', name: 'Cliente existente', phone: '+56911111111', email: null, notes: null }];
    let createdPayload = null;

    await page.route('**/api/v1/customers', async route => {
      if (route.request().method() === 'POST') {
        createdPayload = route.request().postDataJSON();
        const created = { id: 'cust-2', ...createdPayload };
        customers = [created, ...customers];
        return route.fulfill(json(created, 201));
      }
      return route.fulfill(json(customers));
    });
    await page.route('**/api/v1/bookings', route => route.fulfill(json([])));
    await page.route('**/api/v1/services', route => route.fulfill(json([
      { id: 'svc-1', name: 'Servicio demo', durationMinutes: 30, active: true }
    ])));

    await page.goto('/app/agenda');
    await page.getByRole('button', { name: /nueva cita/i }).click();

    const dialog = page.getByRole('dialog', { name: 'Nueva cita' });
    await dialog.getByRole('button', { name: /nuevo cliente/i }).click();
    await dialog.getByLabel('Nombre del cliente').fill('Camila Nueva');
    await dialog.getByLabel('Teléfono del cliente').fill('+56922223333');
    await dialog.getByRole('button', { name: /crear cliente/i }).click();

    await expect.poll(() => createdPayload).toEqual({
      name: 'Camila Nueva',
      phone: '+56922223333',
      email: null,
      notes: null
    });
    const customerSelect = dialog.locator('select[aria-label="Cliente"]');
    await expect(customerSelect).toHaveValue('cust-2');
    await expect(customerSelect.locator('option:checked')).toHaveText('Camila Nueva');
  });

  test('Operations carries Requests with authoritative status updates', async ({ page }) => {
    await seed(page, ['BUSINESS_ADMIN'], ['ORDERS_READ', 'REQUESTS_READ', 'REQUESTS_MANAGE']);
    let requestStatus = 'OPEN';
    let statusPayload = null;

    await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([])));
    await page.route('**/api/v1/commercial/deliveries', route => route.fulfill(json([])));
    await page.route('**/api/v1/requests', route => route.fulfill(json([
      {
        id: 'req-1',
        requestType: 'CALLBACK',
        title: 'Devolver llamada',
        description: 'Cliente necesita confirmación',
        contactName: 'Ana Cliente',
        contactPhone: '+56933334444',
        priority: 'HIGH',
        status: requestStatus,
        source: 'AI_CALL',
        createdAt: '2026-10-02T14:00:00Z'
      }
    ])));
    await page.route('**/api/v1/requests/req-1/status', route => {
      statusPayload = route.request().postDataJSON();
      requestStatus = statusPayload.status;
      return route.fulfill(json({ id: 'req-1', status: requestStatus }));
    });

    await page.goto('/app/orders');
    await page.getByRole('button', { name: 'Solicitudes' }).click();

    await expect(page.getByText('Devolver llamada')).toBeVisible();
    await expect(page.getByText('Ana Cliente')).toBeVisible();
    await page.getByRole('button', { name: /marcar en progreso/i }).click();
    await expect.poll(() => statusPayload).toEqual({ status: 'IN_PROGRESS' });
  });

  test('Operations keeps customer export available inside the contextual customer view', async ({ page }) => {
    await seed(page, ['BUSINESS_ADMIN'], ['ORDERS_READ', 'CUSTOMERS_READ', 'CUSTOMERS_EXPORT']);
    let exportCalls = 0;

    await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([])));
    await page.route('**/api/v1/commercial/deliveries', route => route.fulfill(json([])));
    await page.route('**/api/v1/customers/export?format=csv', route => {
      exportCalls += 1;
      return route.fulfill({
        status: 200,
        contentType: 'text/csv',
        headers: { 'content-disposition': 'attachment; filename="helvoca-clientes.csv"' },
        body: 'nombre,telefono\nAna,+56911111111'
      });
    });

    await page.goto('/app/orders');
    await page.getByRole('button', { name: 'Clientes' }).click();
    await page.getByRole('button', { name: /exportar csv/i }).click();

    await expect.poll(() => exportCalls).toBe(1);
  });

  test('business admin can inspect filter and export Audit inside Operations', async ({ page }) => {
    await seed(page, ['BUSINESS_ADMIN'], ['ORDERS_READ', 'AUDIT_READ']);
    let auditUrl = '';
    let exportCalls = 0;

    await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([])));
    await page.route('**/api/v1/commercial/deliveries', route => route.fulfill(json([])));
    await page.route('**/api/v1/audit**', route => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith('/export')) {
        exportCalls += 1;
        return route.fulfill({
          status: 200,
          contentType: 'text/csv',
          headers: { 'content-disposition': 'attachment; filename="auditoria.csv"' },
          body: 'action,resource\nBOOKING_UPDATE,BOOKING'
        });
      }

      auditUrl = route.request().url();
      return route.fulfill(json([
        {
          id: 'audit-1',
          action: 'BOOKING_UPDATE',
          resourceType: 'BOOKING',
          resourceId: 'book-1',
          result: 'SUCCESS',
          actorType: 'HUMAN',
          actorName: 'Carolina Soto',
          actorEmail: 'carolina@example.cl',
          actorRole: 'BUSINESS_ADMIN',
          beforeState: { status: 'CONFIRMED' },
          afterState: { status: 'CANCELLED' },
          createdAt: '2026-10-02T15:00:00Z'
        }
      ]));
    });

    await page.goto('/app/orders');
    await page.getByRole('button', { name: 'Auditoría' }).click();
    await expect(page.getByText('Carolina Soto')).toBeVisible();

    await page.getByLabel('Actor de auditoría').fill('Carolina');
    await page.getByRole('button', { name: /aplicar filtros/i }).click();
    await expect.poll(() => auditUrl).toContain('actor=Carolina');

    await page.getByRole('button', { name: /exportar auditoría csv/i }).click();
    await expect.poll(() => exportCalls).toBe(1);
  });

  test('operator sees Requests but not the admin-only Audit surface', async ({ page }) => {
    await seed(page, ['OPERATOR'], ['ORDERS_READ', 'REQUESTS_READ']);
    await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([])));
    await page.route('**/api/v1/commercial/deliveries', route => route.fulfill(json([])));
    await page.route('**/api/v1/requests', route => route.fulfill(json([])));

    await page.goto('/app/orders');

    await expect(page.getByRole('button', { name: 'Solicitudes' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Auditoría' })).toHaveCount(0);
  });
});
