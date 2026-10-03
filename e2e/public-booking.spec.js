const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootPublicBooking(page, options = {}) {
  const key = options.key || 'pub_demo_booking_key';

  await page.route(`**/api/v1/public/booking-pages/${key}`, route => {
    if (options.invalidKey) {
      return route.fulfill(json({ message: 'booking page not found' }, 404));
    }
    return route.fulfill(json({
      name: 'Clínica Agenda',
      timezone: 'America/Santiago',
      description: 'Reserva tu hora en línea',
      address: 'Av. Demo 123, Santiago',
      services: [
        {
          id: 'svc1',
          name: 'Evaluación dental',
          description: 'Primera evaluación',
          durationMinutes: 30,
          price: 25000,
          currency: 'CLP'
        },
        {
          id: 'svc2',
          name: 'Limpieza dental',
          description: 'Limpieza completa',
          durationMinutes: 60,
          price: 35000,
          currency: 'CLP'
        }
      ]
    }));
  });

  await page.route(`**/api/v1/public/booking-pages/${key}/availability?**`, route => {
    const url = new URL(route.request().url());
    options.onAvailability?.({
      serviceId: url.searchParams.get('serviceId'),
      date: url.searchParams.get('date'),
      businessId: url.searchParams.get('businessId')
    });
    return route.fulfill(json({
      date: url.searchParams.get('date'),
      timezone: 'America/Santiago',
      slots: options.noAvailability ? [] : [
        { startAt: '2026-10-07T13:00:00Z', endAt: '2026-10-07T13:30:00Z' },
        { startAt: '2026-10-07T13:30:00Z', endAt: '2026-10-07T14:00:00Z' }
      ]
    }));
  });

  let creates = 0;
  await page.route(`**/api/v1/public/booking-pages/${key}/bookings`, async route => {
    creates += 1;
    const request = route.request();
    const payload = request.postDataJSON();
    options.onCreate?.({
      payload,
      idempotencyKey: request.headers()['idempotency-key'] || null,
      authorization: request.headers().authorization || null
    });

    if (options.conflict) {
      return route.fulfill(json({
        code: 'SLOT_UNAVAILABLE',
        message: 'El horario ya no está disponible.'
      }, 409));
    }

    return route.fulfill(json({
      id: 'public-booking-confirmation',
      status: 'CONFIRMED',
      serviceName: 'Evaluación dental',
      startAt: payload.startAt,
      timezone: 'America/Santiago',
      customerName: payload.customer?.name
    }, 201));
  });

  return { key, getCreateCount: () => creates };
}

test.describe('Public booking portal', () => {
  test('loads a public booking page without authentication and shows only public business data', async ({ page }) => {
    const { key } = await bootPublicBooking(page);

    await page.goto(`/reservar/?key=${key}`);

    await expect(page.getByRole('heading', { level: 1, name: 'Clínica Agenda' })).toBeVisible();
    await expect(page.getByText('Reserva tu hora en línea')).toBeVisible();
    await expect(page.getByRole('button', { name: /Evaluación dental/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Limpieza dental/i })).toBeVisible();

    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeFalsy();
    await expect(page.getByText(/iniciar sesión/i)).toHaveCount(0);
  });

  test('asks for authoritative availability using the public key and never sends businessId', async ({ page }) => {
    let availability = null;
    const { key } = await bootPublicBooking(page, {
      onAvailability: value => { availability = value; }
    });

    await page.goto(`/reservar/?key=${key}`);
    await page.getByRole('button', { name: /Evaluación dental/i }).click();
    await page.getByLabel('Fecha').fill('2026-10-07');

    await expect(page.getByRole('button', { name: '10:00' })).toBeVisible();
    await expect.poll(() => availability).not.toBeNull();

    expect(availability.serviceId).toBe('svc1');
    expect(availability.date).toBe('2026-10-07');
    expect(availability.businessId).toBeNull();
  });

  test('creates a booking only after selecting an authoritative slot and never sends tenant identity', async ({ page }) => {
    let created = null;
    const { key } = await bootPublicBooking(page, {
      onCreate: value => { created = value; }
    });

    await page.goto(`/reservar/?key=${key}`);
    await page.getByRole('button', { name: /Evaluación dental/i }).click();
    await page.getByLabel('Fecha').fill('2026-10-07');
    await page.getByRole('button', { name: '10:00' }).click();

    await page.getByLabel('Nombre').fill('Ana Reserva');
    await page.getByLabel('Teléfono').fill('+56922222222');
    await page.getByLabel('Email').fill('ana@example.cl');
    await page.getByRole('button', { name: 'Confirmar reserva' }).click();

    await expect.poll(() => created).not.toBeNull();

    expect(created.payload.serviceId).toBe('svc1');
    expect(created.payload.startAt).toBe('2026-10-07T13:00:00Z');
    expect(created.payload.customer).toEqual({
      name: 'Ana Reserva',
      phone: '+56922222222',
      email: 'ana@example.cl'
    });
    expect(created.payload).not.toHaveProperty('businessId');
    expect(created.payload).not.toHaveProperty('customerId');
    expect(created.authorization).toBeNull();
    expect(created.idempotencyKey).toBeTruthy();

    await expect(page.getByRole('heading', { name: 'Reserva confirmada' })).toBeVisible();
    await expect(page.getByText('Ana Reserva')).toBeVisible();
    await expect(page.getByText('Evaluación dental')).toBeVisible();
  });

  test('blocks repeated submit so one customer action creates at most one public booking request', async ({ page }) => {
    const state = await bootPublicBooking(page);
    const { key } = state;

    await page.goto(`/reservar/?key=${key}`);
    await page.getByRole('button', { name: /Evaluación dental/i }).click();
    await page.getByLabel('Fecha').fill('2026-10-07');
    await page.getByRole('button', { name: '10:00' }).click();
    await page.getByLabel('Nombre').fill('Ana Reserva');
    await page.getByLabel('Teléfono').fill('+56922222222');

    const submit = page.getByRole('button', { name: 'Confirmar reserva' });
    await Promise.all([
      submit.click(),
      submit.click({ force: true }).catch(() => {})
    ]);

    await expect.poll(() => state.getCreateCount()).toBe(1);
  });

  test('keeps the customer on the booking flow when the selected slot becomes unavailable', async ({ page }) => {
    const { key } = await bootPublicBooking(page, { conflict: true });

    await page.goto(`/reservar/?key=${key}`);
    await page.getByRole('button', { name: /Evaluación dental/i }).click();
    await page.getByLabel('Fecha').fill('2026-10-07');
    await page.getByRole('button', { name: '10:00' }).click();
    await page.getByLabel('Nombre').fill('Ana Reserva');
    await page.getByLabel('Teléfono').fill('+56922222222');
    await page.getByRole('button', { name: 'Confirmar reserva' }).click();

    await expect(page.getByRole('alert')).toContainText('ya no está disponible');
    await expect(page.getByRole('heading', { name: 'Reserva confirmada' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Evaluación dental/i })).toBeVisible();
  });

  test('does not allow booking when the public link is invalid or disabled', async ({ page }) => {
    const { key } = await bootPublicBooking(page, { invalidKey: true });

    await page.goto(`/reservar/?key=${key}`);

    await expect(page.getByRole('heading', { name: 'Enlace no disponible' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Confirmar reserva' })).toHaveCount(0);
  });

  test('uses a mobile-first public flow without horizontal overflow', async ({ page }) => {
    const { key } = await bootPublicBooking(page);
    await page.setViewportSize({ width: 390, height: 844 });

    await page.goto(`/reservar/?key=${key}`);

    await expect(page.getByRole('heading', { level: 1, name: 'Clínica Agenda' })).toBeVisible();
    expect(await page.evaluate(() =>
      document.documentElement.scrollWidth <= document.documentElement.clientWidth
    )).toBe(true);
  });
});
