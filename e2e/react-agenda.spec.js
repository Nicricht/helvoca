const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootAgenda(page, options = {}) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    options.token || 'agenda-react-e2e'
  );

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) {
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'expired' })
      });
    }
    return route.fulfill(json({
      email: 'owner@negocio.cl',
      roles: options.roles || ['BUSINESS_ADMIN']
    }));
  });

  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Clínica Agenda',
    timezone: 'America/Santiago'
  })));

  await page.route('**/api/v1/services', route => route.fulfill(json([
    { id: 'svc1', name: 'Evaluación dental', durationMinutes: 30, price: 25000, active: true },
    { id: 'svc2', name: 'Limpieza dental', durationMinutes: 60, price: 35000, active: true }
  ])));

  await page.route('**/api/v1/customers', route => route.fulfill(json([
    { id: 'cust1', name: 'Ana Reserva', phone: '+56922222222', email: 'ana@example.cl' },
    { id: 'cust2', name: 'Bruno Masaje', phone: '+56955555555', email: 'bruno@example.cl' }
  ])));

  let bookings = [
    {
      id: 'b1',
      customerId: 'cust1',
      serviceId: 'svc1',
      startAt: '2026-10-05T15:00:00Z',
      endAt: '2026-10-05T15:30:00Z',
      status: 'CONFIRMED',
      source: 'AI_CALL',
      notes: 'Primera evaluación'
    },
    {
      id: 'b2',
      customerId: 'cust2',
      serviceId: 'svc2',
      startAt: '2026-10-06T16:00:00Z',
      endAt: '2026-10-06T17:00:00Z',
      status: 'CANCELLED',
      source: 'AI_WHATSAPP',
      notes: null
    }
  ];

  await page.route('**/api/v1/bookings', async route => {
    const request = route.request();
    if (request.method() === 'POST') {
      const payload = request.postDataJSON();
      options.onCreate?.(payload);
      const created = {
        id: 'b3',
        customerId: payload.customerId,
        serviceId: payload.serviceId,
        startAt: payload.startAt,
        endAt: new Date(new Date(payload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
        status: 'CONFIRMED',
        source: payload.source,
        notes: payload.notes ?? null
      };
      bookings = [created, ...bookings];
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created)
      });
    }
    return route.fulfill(json(bookings));
  });

  await page.route('**/api/v1/bookings/availability?**', route => {
    const url = new URL(route.request().url());
    options.onAvailability?.({
      serviceId: url.searchParams.get('serviceId'),
      startAt: url.searchParams.get('startAt'),
      excludeBookingId: url.searchParams.get('excludeBookingId')
    });
    return route.fulfill(json({
      serviceId: url.searchParams.get('serviceId'),
      startAt: url.searchParams.get('startAt'),
      available: options.availability === false ? false : true
    }));
  });

  await page.route('**/api/v1/bookings/b1/context', route => {
    if (options.contextError) {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'context unavailable' })
      });
    }
    return route.fulfill(json({
      channel: 'VOICE',
      sourceReferenceId: 'call-1',
      call: {
        call: {
          id: 'call-1',
          callerNumber: '+56922222222',
          status: 'COMPLETED',
          resolution: 'BOOKING_CREATED',
          startedAt: '2026-10-04T18:00:00Z'
        },
        summary: 'Ana llamó para reservar una evaluación dental.',
        transcript: [
          { id: 't1', speaker: 'USER', content: 'Quiero reservar una evaluación.', createdAt: '2026-10-04T18:00:05Z' },
          { id: 't2', speaker: 'ASSISTANT', content: 'Tengo una hora disponible el lunes.', createdAt: '2026-10-04T18:00:08Z' }
        ],
        actions: [
          { id: 'a1', actionType: 'AVAILABILITY_CHECKED', success: true, createdAt: '2026-10-04T18:00:09Z' },
          { id: 'a2', actionType: 'BOOKING_CREATED', success: true, createdAt: '2026-10-04T18:00:12Z' }
        ]
      },
      whatsapp: null,
      events: [
        { id: 'e1', eventType: 'BOOKING_CREATED', channel: 'VOICE', createdAt: '2026-10-04T18:00:12Z' }
      ]
    }));
  });

  await page.route('**/api/v1/bookings/b1/activity', route => route.fulfill(json([
    {
      id: 'audit-1',
      action: 'BOOKING_CREATE',
      actorType: 'HUMAN',
      actorName: 'Carolina Soto',
      actorRole: 'OPERATOR',
      beforeState: null,
      afterState: { startAt: '2026-10-05T15:00:00Z', status: 'CONFIRMED' },
      createdAt: '2026-10-04T18:00:12Z'
    }
  ])));

  await page.route('**/api/v1/bookings/b1', async route => {
    const request = route.request();
    if (request.method() === 'PATCH') {
      const payload = request.postDataJSON();
      options.onPatch?.(payload);
      bookings = bookings.map(booking => booking.id === 'b1'
        ? {
            ...booking,
            startAt: payload.startAt,
            endAt: new Date(new Date(payload.startAt).getTime() + 30 * 60 * 1000).toISOString(),
            notes: payload.notes ?? booking.notes
          }
        : booking);
      return route.fulfill(json(bookings.find(booking => booking.id === 'b1')));
    }
    if (request.method() === 'DELETE') {
      options.onDelete?.();
      bookings = bookings.map(booking => booking.id === 'b1'
        ? { ...booking, status: 'CANCELLED' }
        : booking);
      return route.fulfill({ status: 204, body: '' });
    }
    return route.fallback();
  });
}

test.describe('React Agenda migration', () => {
  test('opens Agenda in week mode and preserves the operational List view', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    await expect(page.getByRole('heading', { level: 1, name: 'Agenda' })).toBeVisible();
    await expect(page.getByText('Gestiona tus citas y reservas')).toBeVisible();

    const switcher = page.getByRole('tablist', { name: 'Vista de Agenda' });
    await expect(switcher.getByRole('tab', { name: 'Semana' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByTestId('agenda-calendar-foundation')).toHaveAttribute('data-agenda-view', 'week');

    const weekBooking = page.getByTestId('agenda-row-b1');
    await expect(weekBooking).toContainText('Ana Reserva');
    await expect(weekBooking).toContainText('Evaluación dental');
    await expect(weekBooking).toContainText('Voz');
    await expect(weekBooking).toContainText('Confirmada');

    await switcher.getByRole('tab', { name: 'Lista' }).click();
    await expect(switcher.getByRole('tab', { name: 'Lista' })).toHaveAttribute('aria-selected', 'true');

    const table = page.getByRole('table', { name: 'Reservas' });
    await expect(table).toBeVisible();
    await expect(table.getByRole('columnheader')).toContainText([
      'Fecha y hora',
      'Cliente',
      'Servicio',
      'Origen',
      'Estado'
    ]);
    await expect(page.getByTestId('agenda-row-b1')).toContainText('Ana Reserva');
  });

  test('switches Day, Week and Month without losing filtered booking context', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    const switcher = page.getByRole('tablist', { name: 'Vista de Agenda' });

    await switcher.getByRole('tab', { name: 'Día' }).click();
    await expect(page.getByTestId('agenda-calendar-foundation')).toHaveAttribute('data-agenda-view', 'day');

    await switcher.getByRole('tab', { name: 'Mes' }).click();
    await expect(page.getByTestId('agenda-calendar-foundation')).toHaveAttribute('data-agenda-view', 'month');

    await switcher.getByRole('tab', { name: 'Semana' }).click();
    await page.getByRole('searchbox', { name: 'Buscar reservas' }).fill('Ana');
    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();
    await expect(page.getByTestId('agenda-row-b2')).toHaveCount(0);

    await switcher.getByRole('tab', { name: 'Lista' }).click();
    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();
    await expect(page.getByTestId('agenda-row-b2')).toHaveCount(0);
    await expect(page.getByRole('searchbox', { name: 'Buscar reservas' })).toHaveValue('Ana');
  });

  test('supports keyboard navigation across Agenda views', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    const switcher = page.getByRole('tablist', { name: 'Vista de Agenda' });
    const week = switcher.getByRole('tab', { name: 'Semana' });
    const month = switcher.getByRole('tab', { name: 'Mes' });

    await week.focus();
    await page.keyboard.press('ArrowRight');

    await expect(month).toHaveAttribute('aria-selected', 'true');
    await expect(month).toBeFocused();
    await expect(page.getByTestId('agenda-calendar-foundation')).toHaveAttribute('data-agenda-view', 'month');

    await page.keyboard.press('ArrowLeft');
    await expect(week).toHaveAttribute('aria-selected', 'true');
    await expect(week).toBeFocused();
  });

  test('positions bookings on the real time grid and scales block height by duration', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    const calendar = page.getByTestId('agenda-calendar-foundation');
    await expect(calendar).toHaveAttribute('data-agenda-view', 'week');

    const shortBooking = page.getByTestId('agenda-row-b1');
    const longBooking = page.getByTestId('agenda-row-b2');

    await expect(shortBooking).toHaveAttribute('data-duration-minutes', '30');
    await expect(longBooking).toHaveAttribute('data-duration-minutes', '60');

    expect(await shortBooking.evaluate(element => getComputedStyle(element).position)).toBe('absolute');
    expect(await longBooking.evaluate(element => getComputedStyle(element).position)).toBe('absolute');

    const shortBox = await shortBooking.boundingBox();
    const longBox = await longBooking.boundingBox();
    expect(shortBox).not.toBeNull();
    expect(longBox).not.toBeNull();
    expect(longBox.height).toBeGreaterThan(shortBox.height);

    await expect(page.getByText('07:00', { exact: true })).toBeVisible();
    await expect(page.getByTestId('agenda-now-line')).toBeVisible();
  });

  test('navigates weeks and returns to today without losing the selected view', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();

    await page.getByRole('button', { name: 'Semana siguiente' }).click();
    await expect(page.getByTestId('agenda-row-b1')).toHaveCount(0);

    await page.getByRole('button', { name: 'Hoy', exact: true }).click();
    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Semana' })).toHaveAttribute('aria-selected', 'true');
  });

  test('clicks an empty calendar slot and prefills a new appointment without mutating first', async ({ page }) => {
    let created = null;
    await bootAgenda(page, { onCreate: value => { created = value; } });
    await page.goto('/app/agenda');

    await page.getByTestId('agenda-slot-2026-10-07-1000').click();

    const dialog = page.getByRole('dialog', { name: 'Nueva cita' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByLabel('Fecha')).toHaveValue('2026-10-07');
    await expect(dialog.getByLabel('Hora')).toHaveValue('10:00');
    expect(created).toBeNull();
  });

  test('dragging a booking prepares a safe reschedule and never patches before confirmation', async ({ page }) => {
    let patch = null;
    await bootAgenda(page, { onPatch: value => { patch = value; } });
    await page.goto('/app/agenda');

    const booking = page.getByTestId('agenda-row-b1');
    const target = page.getByTestId('agenda-slot-2026-10-07-1130');
    const dataTransfer = await page.evaluateHandle(() => new DataTransfer());

    await booking.dispatchEvent('dragstart', { dataTransfer });
    await target.dispatchEvent('dragover', { dataTransfer });
    await target.dispatchEvent('drop', { dataTransfer });
    await booking.dispatchEvent('dragend', { dataTransfer });

    const dialog = page.getByRole('dialog', { name: 'Reserva · Ana Reserva' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('heading', { name: 'Reprogramar' })).toBeVisible();
    await expect(dialog.getByLabel('Fecha')).toHaveValue('2026-10-07');
    await expect(dialog.getByLabel('Hora')).toHaveValue('11:30');
    expect(patch).toBeNull();
  });

  test('uses Day as the mobile default and keeps detail navigation contained', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await bootAgenda(page);
    await page.goto('/app/agenda');

    await expect(page.getByRole('tab', { name: 'Día' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByTestId('agenda-calendar-foundation')).toHaveAttribute('data-agenda-view', 'day');

    await page.getByRole('tab', { name: 'Semana' }).click();
    await page.getByTestId('agenda-row-b1').click();
    const dialog = page.getByRole('dialog', { name: 'Reserva · Ana Reserva' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: '← Volver a Agenda' })).toBeVisible();

    await dialog.getByRole('button', { name: '← Volver a Agenda' }).click();
    await expect(page.getByTestId('agenda-calendar-foundation')).toBeVisible();
  });

  test('renders WhatsApp booking source and authoritative status in React Agenda', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    const row = page.getByTestId('agenda-row-b2');
    await expect(row).toBeVisible();
    await expect(row).toContainText('Bruno Masaje');
    await expect(row).toContainText('WhatsApp');
    await expect(row).toContainText('Cancelada');
  });

  test('filters reservations by search, service and status without leaving Agenda', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    await page.getByRole('searchbox', { name: 'Buscar reservas' }).fill('Ana');
    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();
    await expect(page.getByTestId('agenda-row-b2')).toHaveCount(0);

    await page.getByRole('searchbox', { name: 'Buscar reservas' }).fill('');
    await page.getByLabel('Servicio').selectOption('svc2');
    await expect(page.getByTestId('agenda-row-b2')).toBeVisible();
    await expect(page.getByTestId('agenda-row-b1')).toHaveCount(0);

    await page.getByLabel('Servicio').selectOption('all');
    await page.getByLabel('Estado').selectOption('CONFIRMED');
    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();
    await expect(page.getByTestId('agenda-row-b2')).toHaveCount(0);
  });

  test('opens contextual reservation detail with conversation and activity', async ({ page }) => {
    await bootAgenda(page);
    await page.goto('/app/agenda');

    await page.getByTestId('agenda-row-b1').click();

    const dialog = page.getByRole('dialog', { name: 'Reserva · Ana Reserva' });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('Primera evaluación');

    await dialog.getByRole('tab', { name: 'Conversación' }).click();
    await expect(dialog).toContainText('Ana llamó para reservar una evaluación dental.');
    await expect(dialog).toContainText('Quiero reservar una evaluación.');
    await expect(dialog).toContainText('Tengo una hora disponible el lunes.');

    await dialog.getByRole('tab', { name: 'Actividad' }).click();
    await expect(dialog).toContainText('Carolina Soto');
    await expect(dialog).toContainText('Reserva creada');
  });


  test('opens reservation conversation read-only without browser tenant identity', async ({ page }) => {
    const tenantHints = [];
    const mutations = [];

    page.on('request', request => {
      const url = new URL(request.url());
      if (!url.pathname.startsWith('/api/v1/')) return;

      const body = request.postData() || '';
      if (url.searchParams.has('businessId') || /["']?businessId["']?\\s*[:=]/i.test(body)) {
        tenantHints.push({ method: request.method(), url: request.url(), body });
      }

      if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
        mutations.push({ method: request.method(), url: request.url(), body });
      }
    });

    await bootAgenda(page);
    await page.goto('/app/agenda');

    await page.getByTestId('agenda-row-b1').click();
    const dialog = page.getByRole('dialog', { name: 'Reserva · Ana Reserva' });
    await dialog.getByRole('tab', { name: 'Conversación' }).click();

    await expect(dialog).toContainText('Quiero reservar una evaluación.');
    await expect(dialog).toContainText('Reserva creada');
    expect(tenantHints).toEqual([]);
    expect(mutations).toEqual([]);
  });

  test('keeps the schedule usable if contextual conversation fails', async ({ page }) => {
    await bootAgenda(page, { contextError: true });
    await page.goto('/app/agenda');

    await page.getByTestId('agenda-row-b1').click();
    const dialog = page.getByRole('dialog', { name: 'Reserva · Ana Reserva' });
    await dialog.getByRole('tab', { name: 'Conversación' }).click();

    await expect(dialog.getByRole('alert')).toContainText('No pudimos cargar');
    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();
  });

  test('creates a reservation only after availability and never sends businessId', async ({ page }) => {
    let availability = null;
    let created = null;
    await bootAgenda(page, {
      onAvailability: value => { availability = value; },
      onCreate: value => { created = value; }
    });
    await page.goto('/app/agenda');

    await page.getByRole('button', { name: 'Nueva cita' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nueva cita' });
    await dialog.locator('select[aria-label="Cliente"]').selectOption('cust1');
    await dialog.locator('select[aria-label="Servicio"]').selectOption('svc1');
    await dialog.getByLabel('Fecha').fill('2026-10-07');
    await dialog.getByLabel('Hora').fill('10:00');

    await dialog.getByRole('button', { name: 'Comprobar disponibilidad' }).click();
    await expect(dialog).toContainText('Horario disponible');
    await dialog.getByRole('button', { name: 'Crear reserva' }).click();

    await expect.poll(() => created).not.toBeNull();
    expect(availability.serviceId).toBe('svc1');
    expect(availability.excludeBookingId).toBeNull();
    expect(created.customerId).toBe('cust1');
    expect(created.serviceId).toBe('svc1');
    expect(created.source).toBe('ADMIN');
    expect(created).not.toHaveProperty('businessId');
    await expect(page.getByTestId('agenda-row-b3')).toBeVisible();
  });

  test('reprograms only after availability and scopes availability to the selected booking', async ({ page }) => {
    const availabilityCalls = [];
    let patch = null;
    await bootAgenda(page, {
      onAvailability: value => availabilityCalls.push(value),
      onPatch: value => { patch = value; }
    });
    await page.goto('/app/agenda');

    await page.getByTestId('agenda-row-b1').click();
    const dialog = page.getByRole('dialog', { name: 'Reserva · Ana Reserva' });
    await dialog.getByRole('button', { name: 'Reprogramar' }).click();
    await dialog.getByLabel('Fecha').fill('2026-10-08');
    await dialog.getByLabel('Hora').fill('11:30');
    await dialog.getByRole('button', { name: 'Comprobar disponibilidad' }).click();

    await expect(dialog).toContainText('Horario disponible');
    await dialog.getByRole('button', { name: 'Confirmar cambio' }).click();

    await expect.poll(() => patch).not.toBeNull();
    expect(availabilityCalls.at(-1).serviceId).toBe('svc1');
    expect(availabilityCalls.at(-1).excludeBookingId).toBe('b1');
    expect(patch).not.toHaveProperty('businessId');
  });

  test('cancels the selected reservation and reflects authoritative state', async ({ page }) => {
    let deletes = 0;
    await bootAgenda(page, { onDelete: () => { deletes += 1; } });
    await page.goto('/app/agenda');

    await page.getByTestId('agenda-row-b1').click();
    const dialog = page.getByRole('dialog', { name: 'Reserva · Ana Reserva' });

    page.once('dialog', nativeDialog => nativeDialog.accept());
    await dialog.getByRole('button', { name: 'Cancelar reserva' }).click();

    await expect.poll(() => deletes).toBe(1);
    await expect(page.getByTestId('agenda-row-b1')).toContainText('Cancelada');
  });

  test('keeps Agenda available to an operator without exposing admin-only surfaces', async ({ page }) => {
    await bootAgenda(page, { roles: ['OPERATOR'] });
    await page.goto('/app/agenda');

    await expect(page.getByRole('heading', { level: 1, name: 'Agenda' })).toBeVisible();
    await expect(page.getByTestId('agenda-row-b1')).toBeVisible();
    await expect(page.getByText(/auditoría/i)).toHaveCount(0);
    await expect(page.getByText(/exportar/i)).toHaveCount(0);
  });

  test('redirects to auth when the session is expired', async ({ page }) => {
    await bootAgenda(page, { expired: true });
    await page.goto('/app/agenda');

    await expect(page).toHaveURL(/\/app\/auth\/?$/);
  });

  test('stays contained at desktop, tablet and mobile widths', async ({ page }) => {
    await bootAgenda(page);

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/agenda');
      await expect(page.getByRole('heading', { level: 1, name: 'Agenda' })).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth
      )).toBe(true);
    }
  });

test('lets an administrator activate the public booking link while operators cannot publish it', async ({ page }) => {
  let updatePayload = null;
  await bootAgenda(page);

  await page.route('**/api/v1/business/public-booking', async route => {
    const request = route.request();
    if (request.method() === 'PUT') {
      updatePayload = request.postDataJSON();
      return route.fulfill(json({
        enabled: true,
        key: '11111111-2222-4333-8444-555555555555'
      }));
    }
    return route.fulfill(json({
      enabled: false,
      key: '11111111-2222-4333-8444-555555555555'
    }));
  });

  await page.goto('/app/agenda');
  await page.getByRole('button', { name: 'Reservas online' }).click();

  const dialog = page.getByRole('dialog', { name: 'Reservas online' });
  await expect(dialog).toContainText('Desactivadas');
  await dialog.getByRole('button', { name: 'Activar reservas online' }).click();

  await expect.poll(() => updatePayload).toEqual({ enabled: true });
  await expect(dialog.getByRole('textbox', { name: 'Enlace público' }))
    .toHaveValue(/\/reservar\/\?key=11111111-2222-4333-8444-555555555555$/);

  await page.close();
});

test('does not expose public booking publication controls to an operator', async ({ page }) => {
  await bootAgenda(page, { roles: ['OPERATOR'] });
  await page.goto('/app/agenda');

  await expect(page.getByRole('button', { name: 'Reservas online' })).toHaveCount(0);
});

});
