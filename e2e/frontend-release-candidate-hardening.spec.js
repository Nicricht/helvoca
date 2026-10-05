const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

function trackMutations(page) {
  const mutations = [];
  page.on('request', request => {
    const method = request.method();
    const url = new URL(request.url());
    if (url.pathname.startsWith('/api/v1/') && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      mutations.push({ method, path: url.pathname });
    }
  });
  return mutations;
}

async function commonIdentity(page, roles = ['BUSINESS_ADMIN']) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'rc-hardening-token'));
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: roles.includes('OPERATOR') ? 'operador@ferreteria-rc.cl' : 'admin@ferreteria-rc.cl',
    roles
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    name: 'Ferretería Release Candidate',
    timezone: 'America/Santiago',
    language: 'es'
  })));
}

test('new ferretería owner is guided by React Home to the first incomplete setup step', async ({ page }) => {
  const mutations = trackMutations(page);
  await commonIdentity(page);

  await page.route('**/api/v1/onboarding/status', route => route.fulfill(json({
    businessProfileConfigured: true,
    servicesConfigured: false,
    scheduleConfigured: false,
    knowledgeConfigured: false,
    humanTransferConfigured: false,
    phoneConfigured: false,
    readyForCalls: false,
    nextStep: 'ADD_SERVICE'
  })));

  await page.goto('/app');

  const onboarding = page.getByRole('region', { name: 'Configura tu negocio' });
  await expect(onboarding).toBeVisible();
  await expect(onboarding.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25');
  await expect(onboarding).toContainText(/servicios/i);
  await expect(onboarding.getByRole('link', { name: /continuar/i }))
    .toHaveAttribute('href', '/app/settings?section=services');
  await expect(page.getByRole('region', { name: 'Qué está pasando hoy' })).toHaveCount(0);
  expect(mutations).toEqual([]);
});

test('ferretería RC surfaces low stock in React Inventory without causing a mutation', async ({ page }) => {
  const mutations = trackMutations(page);
  await commonIdentity(page);

  await page.route('**/api/v1/catalog', route => route.fulfill(json([{
    id: 'product-low',
    kind: 'PRODUCT',
    name: 'Disco de corte 115 mm',
    description: 'Metal',
    price: 4990,
    currency: 'CLP',
    active: true
  }])));
  await page.route('**/api/v1/inventory', route => route.fulfill(json([{
    id: 'stock-low',
    catalogItemId: 'product-low',
    productName: 'Disco de corte 115 mm',
    sku: 'DISCO-115',
    trackingEnabled: true,
    onHand: 2,
    reserved: 1,
    available: 1,
    reorderThreshold: 2,
    lowStock: true
  }])));
  await page.route('**/api/v1/inventory/alerts', route => route.fulfill(json([{
    id: 'alert-low',
    catalogItemId: 'product-low',
    variantId: null,
    type: 'LOW_STOCK',
    subjectName: 'Disco de corte 115 mm',
    sku: 'DISCO-115',
    available: 1,
    reorderThreshold: 2,
    acknowledged: false,
    acknowledgedAt: null,
    createdAt: '2026-09-29T01:00:00Z'
  }])));
  await page.route('**/api/v1/inventory/restock-subscriptions/notifications', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/restock-subscriptions', route => route.fulfill(json([])));

  await page.goto('/app/inventory');

  await expect(page.getByTestId('inventory-row-product-low')).toContainText('Disco de corte 115 mm');
  await expect(page.getByTestId('inventory-low-stock')).toContainText('1');
  await expect(page.getByTestId('inventory-alert-alert-low')).toContainText('Stock bajo');
  await expect(page.getByTestId('inventory-alert-alert-low')).toContainText('DISCO-115');
  expect(mutations).toEqual([]);
});

test('operator can inspect the active plan without admin billing actions', async ({ page }) => {
  const mutations = trackMutations(page);
  await commonIdentity(page, ['OPERATOR']);

  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    businessId: 'business-rc',
    plan: 'PRO',
    publicPlanCode: 'PRO',
    planName: 'Profesional',
    status: 'ACTIVE',
    serviceAllowed: true,
    maxConcurrentCalls: 2,
    includedMinutes: 500,
    usedMinutes: 125,
    overageMinutes: 0,
    currentPeriodStart: '2026-09-01T00:00:00Z',
    currentPeriodEnd: '2026-10-01T00:00:00Z',
    billingProviderConnected: true,
    entitlements: [],
    legacyFallback: false
  })));
  await page.route('**/api/v1/usage/**', route => route.fulfill({
    status: 403,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'forbidden' })
  }));

  await page.goto('/app/plan');

  await expect(page.getByTestId('plan-name')).toHaveText('Profesional');
  await expect(page.getByTestId('usage-restricted')).toContainText('propietarios y administradores');
  await expect(page.getByRole('heading', { name: 'Gestionar plan' })).toHaveCount(0);
  expect(mutations).toEqual([]);
});

test('billing outage degrades safely on React Plan mobile and does not create a payment attempt', async ({ page }) => {
  const mutations = trackMutations(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await commonIdentity(page);

  await page.route('**/api/v1/subscription', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'subscription unavailable' })
  }));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    billingEnabled: false,
    checkoutConfigured: false,
    currentPlanCode: null,
    currentPlanName: null,
    currentMonthlyPriceClp: null,
    subscriptionStatus: null,
    pendingPlanCode: null,
    pendingPlanName: null,
    pendingMonthlyPriceClp: null,
    checkoutUrl: null,
    awaitingProviderVerification: false
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/usage/**', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'usage unavailable' })
  }));

  await page.goto('/app/plan');

  await expect(page.getByRole('alert')).toContainText('No pudimos cargar tu plan');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  expect(mutations).toEqual([]);
});
