const { test, expect } = require('@playwright/test');

const ADMIN_EMAIL = process.env.SYSTEM_E2E_ADMIN_EMAIL || 'admin@helvoca.local';
const ADMIN_PASSWORD = process.env.SYSTEM_E2E_ADMIN_PASSWORD || 'ChangeMe123!';

async function login(page) {
  await page.goto('/');
  await page.getByRole('tab', { name: 'Ingresar', exact: true }).click();
  await page.locator('#loginForm input[name="email"]').fill(ADMIN_EMAIL);
  await page.locator('#loginForm input[name="password"]').fill(ADMIN_PASSWORD);
  await page.locator('#loginForm button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/?$/);
  await expect.poll(async () =>
    page.evaluate(() => Boolean(sessionStorage.getItem('helvoca_access_token')))
  ).toBe(true);
}

async function authenticatedJson(page, path, init = {}) {
  return page.evaluate(async ({ path, init }) => {
    const token = sessionStorage.getItem('helvoca_access_token');
    const response = await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(init.headers || {}),
        Authorization: `Bearer ${token}`
      }
    });
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    return { status: response.status, body };
  }, { path, init });
}

async function findAvailableLocalSlot(page, serviceId) {
  return page.evaluate(async serviceId => {
    const token = sessionStorage.getItem('helvoca_access_token');
    const times = ['11:00', '12:30', '14:00', '16:00'];

    for (let offset = 1; offset <= 21; offset += 1) {
      const day = new Date();
      day.setDate(day.getDate() + offset);
      day.setHours(12, 0, 0, 0);
      if (day.getDay() === 0) continue;

      const date = [
        day.getFullYear(),
        String(day.getMonth() + 1).padStart(2, '0'),
        String(day.getDate()).padStart(2, '0')
      ].join('-');

      for (const time of times) {
        const startAt = new Date(`${date}T${time}:00`).toISOString();
        const params = new URLSearchParams({ serviceId, startAt });
        const response = await fetch('/api/v1/bookings/availability?' + params.toString(), {
          headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' }
        });
        if (!response.ok) continue;
        const body = await response.json();
        if (body.available) return { date, time, startAt };
      }
    }
    return null;
  }, serviceId);
}

function futureDates(count = 21) {
  const dates = [];
  const base = new Date();
  for (let offset = 1; offset <= count; offset += 1) {
    const day = new Date(base);
    day.setUTCDate(base.getUTCDate() + offset);
    dates.push(day.toISOString().slice(0, 10));
  }
  return dates;
}

test.describe('real React + Spring Boot + PostgreSQL system integration', () => {
  test('login, create booking, reload persistence, cancel and reload persistence', async ({ page }) => {
    await login(page);
    await page.goto('/app/agenda');
    await expect(page.getByRole('heading', { level: 1, name: 'Agenda' })).toBeVisible();

    await page.getByRole('button', { name: 'Nueva cita' }).click();
    const dialog = page.getByRole('dialog', { name: 'Nueva cita' });
    await dialog.getByRole('button', { name: '+ Nuevo cliente' }).click();

    const customerName = 'System E2E Cliente';
    await dialog.getByLabel('Nombre del cliente').fill(customerName);
    await dialog.getByLabel('Teléfono del cliente').fill('+56970000001');
    await dialog.getByLabel('Email del cliente').fill('system-e2e@example.invalid');
    await dialog.getByRole('button', { name: 'Crear cliente' }).click();
    await expect(dialog.getByRole('status')).toContainText('Cliente creado y seleccionado.');

    const serviceSelect = dialog.locator('select[aria-label="Servicio"]');
    await serviceSelect.selectOption({ label: 'Corte clásico' });
    const serviceId = await serviceSelect.inputValue();
    expect(serviceId).toBeTruthy();

    const slot = await findAvailableLocalSlot(page, serviceId);
    expect(slot, 'A real available slot must exist in the seeded PostgreSQL schedule').not.toBeNull();

    await dialog.getByLabel('Fecha').fill(slot.date);
    await dialog.getByLabel('Hora').fill(slot.time);
    await dialog.getByRole('button', { name: 'Comprobar disponibilidad' }).click();
    await expect(dialog.getByText('Horario disponible', { exact: true })).toBeVisible();
    await dialog.getByRole('button', { name: 'Crear reserva' }).click();

    const row = page.getByRole('row').filter({ hasText: customerName });
    await expect(row).toBeVisible();
    const testId = await row.getAttribute('data-testid');
    expect(testId).toMatch(/^agenda-row-/);
    const bookingId = testId.replace('agenda-row-', '');

    await page.reload();
    const persisted = page.getByTestId(`agenda-row-${bookingId}`);
    await expect(persisted).toContainText(customerName);
    await expect(persisted).toContainText('Confirmada');

    await persisted.click();
    const detail = page.getByRole('dialog', { name: `Reserva · ${customerName}` });
    page.once('dialog', nativeDialog => nativeDialog.accept());
    await detail.getByRole('button', { name: 'Cancelar reserva' }).click();
    await expect(page.getByTestId(`agenda-row-${bookingId}`)).toContainText('Cancelada');

    await page.reload();
    await expect(page.getByTestId(`agenda-row-${bookingId}`)).toContainText('Cancelada');

    const backend = await authenticatedJson(page, `/api/v1/bookings/${bookingId}`);
    expect(backend.status).toBe(200);
    expect(backend.body.status).toBe('CANCELLED');
  });

  test('Settings writes through Spring and survives a full browser reload', async ({ page }) => {
    await login(page);
    await page.goto('/app/settings?section=business');
    await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();

    const marker = 'System E2E PostgreSQL persistence marker';
    const description = page.getByLabel('Descripción pública');
    await description.fill(marker);
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByText('Guardado', { exact: true })).toBeVisible();

    await page.reload();
    await expect(page.getByLabel('Descripción pública')).toHaveValue(marker);

    const profile = await authenticatedJson(page, '/api/v1/business/profile');
    expect(profile.status).toBe(200);
    expect(profile.body.publicDescription).toBe(marker);
  });

  test('tenant B cannot read a booking owned by tenant A', async ({ page, request }) => {
    await login(page);
    const bookings = await authenticatedJson(page, '/api/v1/bookings');
    expect(bookings.status).toBe(200);
    expect(bookings.body.length).toBeGreaterThan(0);
    const tenantABookingId = bookings.body[0].id;

    const tenantA = await authenticatedJson(page, '/api/v1/auth/me');
    expect(tenantA.status).toBe(200);

    const suffix = Date.now();
    const register = await request.post('/api/v1/auth/register', {
      data: {
        adminName: 'Tenant B System E2E',
        email: `tenant-b-${suffix}@example.invalid`,
        password: 'TenantB-System-123!',
        businessName: 'Tenant B System E2E',
        timezone: 'America/Santiago',
        language: 'es',
        humanTransferPhone: null
      }
    });
    expect(register.status()).toBe(200);
    const registered = await register.json();
    expect(registered.accessToken).toBeTruthy();
    expect(registered.user.businessId).not.toBe(tenantA.body.businessId);

    const forbiddenRead = await request.get(`/api/v1/bookings/${tenantABookingId}`, {
      headers: { Authorization: `Bearer ${registered.accessToken}` }
    });
    expect(forbiddenRead.status()).toBe(404);

    const tenantBList = await request.get('/api/v1/bookings', {
      headers: { Authorization: `Bearer ${registered.accessToken}` }
    });
    expect(tenantBList.status()).toBe(200);
    const tenantBBookings = await tenantBList.json();
    expect(tenantBBookings.some(item => item.id === tenantABookingId)).toBe(false);
  });

  test('a real public booking becomes visible in the authenticated Agenda', async ({ page, request, browser }) => {
    await login(page);
    await page.goto('/app/agenda');

    await page.getByRole('button', { name: 'Reservas online' }).click();
    const publication = page.getByRole('dialog', { name: 'Reservas online' });
    await expect(publication).toBeVisible();

    const publicLink = publication.getByRole('textbox', { name: 'Enlace público' });
    await expect(publicLink).toBeVisible();

    const disabledState = publication.getByText('Desactivadas', { exact: true });
    if (await disabledState.isVisible()) {
      await publication.getByRole('button', { name: 'Activar reservas online' }).click();
    }
    await expect(publication.getByText('Activadas', { exact: true })).toBeVisible();

    const publicUrl = await publicLink.inputValue();
    const key = new URL(publicUrl).searchParams.get('key');
    expect(key).toBeTruthy();

    const publicPageResponse = await request.get(`/api/v1/public/booking-pages/${key}`);
    expect(publicPageResponse.status()).toBe(200);
    const publicPageData = await publicPageResponse.json();
    expect(publicPageData.services.length).toBeGreaterThan(0);
    const publicService = publicPageData.services[0];

    let publicDate = null;
    for (const date of futureDates()) {
      const availability = await request.get(
        `/api/v1/public/booking-pages/${key}/availability?serviceId=${encodeURIComponent(publicService.id)}&date=${date}`
      );
      if (!availability.ok()) continue;
      const body = await availability.json();
      if (Array.isArray(body.slots) && body.slots.length > 0) {
        publicDate = date;
        break;
      }
    }
    expect(publicDate, 'Public booking must expose at least one authoritative slot').not.toBeNull();

    const publicContext = await browser.newContext({ timezoneId: 'America/Santiago' });
    const publicPage = await publicContext.newPage();
    await publicPage.goto(publicUrl);
    await publicPage.getByRole('button', { name: publicService.name, exact: false }).click();
    await publicPage.getByLabel('Fecha', { exact: true }).fill(publicDate);
    const slotButton = publicPage.locator('[aria-label="Horarios disponibles"] button').first();
    await expect(slotButton).toBeVisible();
    await slotButton.click();

    const customerName = 'Public System E2E';
    await publicPage.getByLabel('Nombre').fill(customerName);
    await publicPage.getByLabel('Teléfono').fill('+56970000002');
    await publicPage.getByLabel('Email').fill('public-system-e2e@example.invalid');
    await publicPage.getByRole('button', { name: 'Confirmar reserva' }).click();
    await expect(publicPage.getByRole('heading', { name: 'Reserva confirmada' })).toBeVisible();
    await publicContext.close();

    await page.goto('/app/agenda');
    const internalRow = page.getByRole('row').filter({ hasText: customerName });
    await expect(internalRow).toBeVisible();
    await expect(internalRow).toContainText('Web');
    await expect(internalRow).toContainText('Confirmada');
  });
});
