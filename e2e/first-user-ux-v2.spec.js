const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

async function mockBaseTenant(page, status) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'owner@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Barbería Norte Demo',
    timezone: 'America/Santiago',
    language: 'es',
    humanTransferPhone: null
  })));
  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json(status)));
  await page.route('**/api/v1/onboarding/guide', route => route.fulfill(json({
    readyForPilot: status.readyForCalls,
    completed: status.readyForCalls ? 7 : 1,
    total: 7,
    progressPercent: status.readyForCalls ? 100 : 14,
    nextStep: status.readyForCalls ? null : {
      code: 'BUSINESS_SETUP',
      label: 'Datos, oferta y horarios',
      complete: false,
      detail: 'Pendiente',
      actionLabel: 'Preparar negocio',
      actionHref: '/settings.html'
    },
    steps: []
  })));
  await page.route('**/api/v1/services', route => route.fulfill(json(
    status.servicesConfigured
      ? [{ id: 'svc1', name: 'Corte Premium', durationMinutes: 30, price: 18990, active: true }]
      : []
  )));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json(
    status.scheduleConfigured
      ? [{ dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }]
      : []
  )));
  await page.route('**/api/v1/knowledge?activeOnly=false', route => route.fulfill(json([])));
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: Boolean(status.phoneConfigured),
    name: 'RecepVoz',
    language: 'es',
    active: Boolean(status.phoneConfigured),
    greeting: 'Hola, gracias por llamar.',
    capabilities: []
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json(
    status.phoneConfigured
      ? [{ id: 'phone1', provider: 'TWILIO', externalId: 'PNdemo', phoneNumber: '+56911111111', active: true }]
      : []
  )));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false,
    configured: false,
    purchaseAvailable: false,
    provider: 'TWILIO',
    message: 'No disponible en E2E'
  })));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    provider: 'mercadopago',
    billingEnabled: true,
    checkoutConfigured: true,
    currentPlanCode: 'BASIC',
    currentPlanName: 'Emprende',
    currentMonthlyPriceClp: 24990,
    subscriptionStatus: 'ACTIVE',
    awaitingProviderVerification: false
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    plan: 'BASIC',
    status: 'ACTIVE',
    serviceAllowed: true,
    maxConcurrentCalls: 1,
    includedMinutes: 100,
    usedMinutes: 12,
    overageMinutes: 0,
    billingProviderConnected: true,
    legacyFallback: false
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/admin/users', route => route.fulfill(json([])));
  await page.route('**/api/v1/admin/invitations', route => route.fulfill(json([])));
  await page.route('**/api/v1/operations/pilot-metrics**', route => route.fulfill(json({
    conversationsHandled: 18,
    bookingsCreated: 6,
    requiresAttention: 1
  })));
}

test('new owner gets a four-step guided setup instead of the operational workspace', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockBaseTenant(page, {
    businessProfileConfigured: true,
    servicesConfigured: false,
    scheduleConfigured: false,
    knowledgeConfigured: false,
    humanTransferConfigured: false,
    phoneConfigured: false,
    readyForCalls: false,
    nextStep: 'SERVICES'
  });

  await page.goto('/');

  await expect(page.locator('#firstUserOnboarding')).toBeVisible();
  await expect(page.locator('#firstUserOnboarding h1')).toHaveText('Vamos a preparar tu recepcionista');
  await expect(page.locator('#firstUserProgressText')).toHaveText('1 de 4 pasos completados');
  await expect(page.locator('#firstUserSteps .first-user-step')).toContainText([
    'Tu negocio',
    'Servicios',
    'Horarios',
    'Recepcionista'
  ]);
  await expect(page.getByRole('link', { name: 'Continuar configuración' })).toHaveAttribute('href', '/settings.html');
  await expect(page.locator('#homeBusinessWorkspace')).toBeHidden();
  await expect(page.locator('#primaryNav a')).toContainText(['Inicio', 'Reservas', 'Clientes', 'Configuración']);
  await expect(page.locator('#primaryNav')).not.toContainText('Inventario');
});

test('ready owner lands on a simple daily home with direct reservations and customers navigation', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'e2e-token'));
  await mockBaseTenant(page, {
    businessProfileConfigured: true,
    servicesConfigured: true,
    scheduleConfigured: true,
    knowledgeConfigured: true,
    humanTransferConfigured: false,
    phoneConfigured: true,
    readyForCalls: true,
    nextStep: 'OPTIONAL_HUMAN_TRANSFER'
  });

  await page.goto('/');

  await expect(page.locator('#firstUserOnboarding')).toBeHidden();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('está atendiendo');
  await expect(page.getByRole('link', { name: 'Probar RecepVoz' })).toBeVisible();

  const nav = page.locator('#primaryNav');
  await expect(nav.locator('a')).toContainText(['Inicio', 'Reservas', 'Clientes', 'Configuración']);
  await expect(nav).not.toContainText('Inventario');

  await page.getByRole('link', { name: 'Reservas' }).click();
  await expect(page.locator('[data-home-tab="bookings"]')).toHaveClass(/active/);

  await page.getByRole('link', { name: 'Clientes' }).click();
  await expect(page.locator('[data-home-tab="customers"]')).toHaveClass(/active/);
});

test('public and authenticated shells load Bootstrap 5 before RecepVoz styles', async ({ page }) => {
  await page.goto('/');

  const stylesheets = await page.locator('link[rel="stylesheet"]').evaluateAll(nodes =>
    nodes.map(node => node.getAttribute('href'))
  );

  const bootstrapIndex = stylesheets.findIndex(href => /bootstrap(?:\.min)?\.css/.test(href || ''));
  const recepVozIndex = stylesheets.findIndex(href => href === '/styles.css');

  expect(bootstrapIndex).toBeGreaterThanOrEqual(0);
  expect(recepVozIndex).toBeGreaterThan(bootstrapIndex);
});
