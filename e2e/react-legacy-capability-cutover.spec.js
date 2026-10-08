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
  // Home is now the canonical place for request attention and history.
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    readyForCalls: true, businessProfileConfigured: true, servicesConfigured: true,
    scheduleConfigured: true, phoneConfigured: true, nextStep: 'DONE'
  })));
  await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
    businessName: 'Negocio E2E', timezone: 'America/Santiago',
    localNow: '2026-10-08T13:30:00-03:00', callsToday: 0,
    callDurationSecondsToday: 0, bookingsToday: 0, newCustomersToday: 0,
    openRequests: 1, unansweredQuestions: 0, callFailuresToday: 0,
    estimatedCallCostTodayUsd: 0, recentCalls: [], recentRequests: [], unanswered: []
  })));
  await page.route('**/api/v1/commercial/analytics**', route => route.fulfill(json({
    days: 7, timezone: 'America/Santiago', primaryCurrency: 'CLP',
    totalRevenue: 0, paidOrders: 0, unitsSold: 0, averageTicket: 0,
    currencyTotals: [], salesOverTime: [], recepVozOrders: 0,
    recepVozRevenue: 0, bookingCurrency: 'CLP', paidBookings: 0,
    bookingRevenue: 0, recepVozPaidBookings: 0, recepVozBookingRevenue: 0,
    bookingCurrencyTotals: []
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

  test('Home carries actionable Requests with authoritative status updates', async ({ page }) => {
    await seed(page, ['BUSINESS_ADMIN'], ['ORDERS_READ', 'REQUESTS_READ', 'REQUESTS_MANAGE']);
    let requestStatus = 'OPEN';
    let statusPayload = null;

    await page.route('**/api/v1/operations/attention', route => route.fulfill(json(
      requestStatus === 'RESOLVED' ? [] : [{
        kind: 'REQUEST', id: 'req-1', operationId: 'op-1',
        title: 'Devolver llamada', priority: 'HIGH',
        status: requestStatus, createdAt: '2026-10-02T14:00:00Z'
      }]
    )));
    await page.route('**/api/v1/requests', route => route.fulfill(json([
      {
        id: 'req-1', requestType: 'CALLBACK', title: 'Devolver llamada',
        description: 'Cliente necesita confirmación', contactName: 'Ana Cliente',
        contactPhone: '+56933334444', priority: 'HIGH', status: requestStatus,
        source: 'AI_CALL', createdAt: '2026-10-02T14:00:00Z'
      }
    ])));
    await page.route('**/api/v1/requests/req-1/status', route => {
      statusPayload = route.request().postDataJSON();
      requestStatus = statusPayload.status;
      return route.fulfill(json({ id: 'req-1', status: requestStatus }));
    });

    await page.goto('/app');
    const attention = page.getByRole('region', { name: 'Necesita tu atención' });
    await expect(attention).toContainText('Devolver llamada');
    await attention.getByRole('button', { name: 'Empezar gestión' }).click();
    await expect.poll(() => statusPayload).toEqual({ status: 'IN_PROGRESS' });
    await expect(attention).toContainText('En progreso');

    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/i }).click();
    await expect(page.getByText('Ana Cliente')).toBeVisible();

    await page.goto('/app/orders');
    await expect(page.getByRole('heading', { name: 'Seguimiento operativo' })).toHaveCount(0);
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

  test('business admin can inspect filter and export Audit from optional Home history', async ({ page }) => {
    await seed(page, ['BUSINESS_ADMIN'], ['ORDERS_READ', 'AUDIT_READ']);
    let auditUrl = '';
    let exportCalls = 0;

    await page.route('**/api/v1/commercial/orders', route => route.fulfill(json([])));
    await page.route('**/api/v1/commercial/deliveries', route => route.fulfill(json([])));
    await page.route(/\/api\/v1\/audit(?:\/export)?(?:\?.*)?$/, route => {
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

    await page.route('**/api/v1/operations/attention', route => route.fulfill(json([])));
    await page.route('**/api/v1/requests', route => route.fulfill(json([])));
    await page.goto('/app');
    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/i }).click();
    await page.getByRole('button', { name: 'Auditoría', exact: true }).click();
    await expect(page.getByText('Carolina Soto')).toBeVisible();

    await page.getByLabel('Actor de auditoría').fill('Carolina');
    await page.getByRole('button', { name: /aplicar filtros/i }).click();
    await expect.poll(() => auditUrl).toContain('actor=Carolina');

    await page.getByRole('button', { name: /exportar auditoría csv/i }).click();
    await expect.poll(() => exportCalls).toBe(1);
  });

  test('operator can see requests in Home but cannot open admin-only Audit', async ({ page }) => {
    await seed(page, ['OPERATOR'], ['ORDERS_READ', 'REQUESTS_READ']);
    await page.route('**/api/v1/operations/attention', route => route.fulfill(json([{
      kind: 'REQUEST', id: 'req-operator', operationId: 'op-operator',
      title: 'Cliente necesita seguimiento', priority: 'NORMAL',
      status: 'OPEN', createdAt: '2026-10-02T14:00:00Z'
    }])));
    await page.route('**/api/v1/requests', route => route.fulfill(json([])));

    await page.goto('/app');

    await expect(page.getByRole('region', { name: 'Necesita tu atención' }))
      .toContainText('Cliente necesita seguimiento');
    await page.getByRole('button', { name: /Consultar historial de solicitudes y auditoría/i }).click();
    await expect(page.getByRole('button', { name: 'Solicitudes', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Auditoría', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Empezar gestión' })).toHaveCount(0);
  });

});
