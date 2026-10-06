const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

function invitation(overrides = {}) {
  return {
    id: '33333333-3333-3333-3333-333333333333',
    businessId: '11111111-1111-1111-1111-111111111111',
    businessName: 'Negocio E2E',
    name: 'Camila Soto',
    email: 'camila@negocio.cl',
    role: 'OPERATOR',
    expiresAt: '2026-10-09T12:00:00Z',
    status: 'PENDING',
    invitePath: null,
    ...overrides
  };
}

async function routeInvitation(page, preview = invitation()) {
  const calls = { preview: 0, accept: 0, acceptPayload: null };
  await page.route('**/api/v1/auth/invitations/**', async route => {
    const request = route.request();
    if (request.method() === 'POST') {
      calls.accept += 1;
      calls.acceptPayload = request.postDataJSON();
      return route.fulfill(json({
        accessToken: 'invite-react-jwt',
        tokenType: 'Bearer',
        expiresInSeconds: 3600,
        user: {
          id: '22222222-2222-2222-2222-222222222222',
          businessId: preview.businessId,
          name: preview.name,
          email: preview.email,
          roles: [preview.role]
        }
      }));
    }
    calls.preview += 1;
    return route.fulfill(json(preview));
  });
  return calls;
}

test.describe('React public invitation migration', () => {
  test('canonical invite loads without an existing session and previews a pending invitation', async ({ page }) => {
    const calls = await routeInvitation(page);

    await page.goto('/app/invite?businessId=11111111-1111-1111-1111-111111111111&token=test-token');

    await expect(page).toHaveURL(/\/app\/invite\/?\?businessId=.*&token=test-token$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Únete a Negocio E2E' })).toBeVisible();
    await expect(page.getByText('Camila Soto')).toBeVisible();
    await expect(page.getByText(/camila@negocio\.cl.*Operador/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Aceptar invitación' })).toBeVisible();
    expect(calls.preview).toBe(1);
    expect(calls.accept).toBe(0);
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
  });

  test('mismatched passwords fail locally and perform zero acceptance POSTs', async ({ page }) => {
    const calls = await routeInvitation(page);
    await page.goto('/app/invite?businessId=11111111-1111-1111-1111-111111111111&token=test-token');

    await page.getByLabel('Nueva contraseña').fill('UnaClaveMuySegura123');
    await page.getByLabel('Repite la contraseña').fill('OtraClaveMuySegura456');
    await page.getByRole('button', { name: 'Aceptar invitación' }).click();

    await expect(page.getByRole('alert')).toContainText('Las contraseñas no coinciden');
    expect(calls.accept).toBe(0);
  });

  test('explicit valid submit accepts exactly once, stores JWT and enters canonical app', async ({ page }) => {
    const calls = await routeInvitation(page);
    await page.route('**/api/v1/auth/me', route => route.fulfill(json({
      email: 'camila@negocio.cl',
      roles: ['OPERATOR'],
      permissions: []
    })));
    await page.route('**/api/v1/subscription', route => route.fulfill(json({
      plan: 'BASIC', publicPlanCode: 'BASIC', planName: 'Emprende',
      status: 'ACTIVE', includedMinutes: 100, usedMinutes: 0
    })));
    await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
      businessProfileConfigured: false, servicesConfigured: false, scheduleConfigured: false,
      knowledgeConfigured: false, humanTransferConfigured: false, phoneConfigured: false,
      readyForCalls: false, nextStep: 'business'
    })));
    await page.route('**/api/v1/operations/dashboard', route => route.fulfill(json({
      businessName: 'Negocio E2E', timezone: 'America/Santiago', localNow: '2026-10-06T06:00:00-04:00',
      callsToday: 0, callDurationSecondsToday: 0, bookingsToday: 0, newCustomersToday: 0,
      openRequests: 0, unansweredQuestions: 0, callFailuresToday: 0, estimatedCallCostTodayUsd: 0,
      recentCalls: [], recentRequests: [], unanswered: []
    })));
    await page.route('**/api/v1/commercial/analytics**', route => route.fulfill(json({
      days: 7, timezone: 'America/Santiago', primaryCurrency: 'CLP', totalRevenue: 0,
      paidOrders: 0, unitsSold: 0, averageTicket: 0, currencyTotals: [], salesOverTime: [],
      recepVozOrders: 0, recepVozRevenue: 0, bookingCurrency: 'CLP', paidBookings: 0,
      bookingRevenue: 0, recepVozPaidBookings: 0, recepVozBookingRevenue: 0, bookingCurrencyTotals: []
    })));

    await page.goto('/app/invite?businessId=11111111-1111-1111-1111-111111111111&token=test-token');
    await page.getByLabel('Nueva contraseña').fill('UnaClaveMuySegura123');
    await page.getByLabel('Repite la contraseña').fill('UnaClaveMuySegura123');
    await page.getByRole('button', { name: 'Aceptar invitación' }).click();

    await expect.poll(() => calls.accept).toBe(1);
    expect(calls.acceptPayload).toEqual({ password: 'UnaClaveMuySegura123' });
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBe('invite-react-jwt');
    await expect(page).toHaveURL(/\/app\/?$/);
  });

  for (const [status, copy] of [
    ['ACCEPTED', 'Esta invitación ya fue utilizada.'],
    ['EXPIRED', 'Esta invitación expiró. Pide una nueva.'],
    ['REVOKED', 'Esta invitación fue revocada.']
  ]) {
    test(status + ' invitation cannot render an active acceptance form', async ({ page }) => {
      const calls = await routeInvitation(page, invitation({ status }));
      await page.goto('/app/invite?businessId=11111111-1111-1111-1111-111111111111&token=test-token');

      await expect(page.getByText(copy, { exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Aceptar invitación' })).toHaveCount(0);
      expect(calls.accept).toBe(0);
    });
  }

  test('missing parameters fail closed without contacting invitation API', async ({ page }) => {
    let calls = 0;
    await page.route('**/api/v1/auth/invitations/**', route => {
      calls += 1;
      return route.fulfill(json({}));
    });

    await page.goto('/app/invite');

    await expect(page.getByRole('heading', { name: 'Enlace incompleto' })).toBeVisible();
    await expect(page.getByText(/genere una nueva invitación/i)).toBeVisible();
    expect(calls).toBe(0);
  });

  test('invalid preview response renders invalid invitation state', async ({ page }) => {
    await page.route('**/api/v1/auth/invitations/**', route => route.fulfill(json({
      message: 'Invitation not found'
    }, 404)));

    await page.goto('/app/invite?businessId=11111111-1111-1111-1111-111111111111&token=bad-token');

    await expect(page.getByRole('heading', { name: 'Invitación no válida' })).toBeVisible();
    await expect(page.getByRole('alert')).toContainText(/Invitation not found|invitación no está disponible/i);
    await expect(page.getByRole('button', { name: 'Aceptar invitación' })).toHaveCount(0);
  });

  test('legacy URL preserves businessId and token while redirecting and does not load invite.js', async ({ page }) => {
    await routeInvitation(page);
    const requests = [];
    page.on('request', request => requests.push(new URL(request.url()).pathname));

    await page.goto('/invite.html?businessId=11111111-1111-1111-1111-111111111111&token=legacy-token');

    await expect(page).toHaveURL(/\/app\/invite\/?\?businessId=11111111-1111-1111-1111-111111111111&token=legacy-token$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Únete a Negocio E2E' })).toBeVisible();
    expect(requests).not.toContain('/invite.js');
  });

  test('authenticated application routes remain inside AuthBoundary', async () => {
    const root = path.resolve(__dirname, '..');
    const app = fs.readFileSync(path.join(root, 'frontend/src/app/App.tsx'), 'utf8');

    expect(app).toContain('path="/invite"');
    expect(app).toMatch(/path="\/invite"[\s\S]*<InvitePage/);
    expect(app).toContain('<AuthBoundary>');
  });
});
