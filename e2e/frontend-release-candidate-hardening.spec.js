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

test('new ferretería owner is guided to the first incomplete setup step without entering operations', async ({ page }) => {
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
    nextStep: 'SERVICES'
  })));
  await page.route('**/api/v1/onboarding/guide', route => route.fulfill(json({
    readyForPilot: false,
    completed: 1,
    total: 7,
    progressPercent: 14,
    nextStep: {
      code: 'BUSINESS_SETUP',
      label: 'Datos, oferta y horarios',
      complete: false,
      detail: 'Pendiente',
      actionLabel: 'Preparar negocio',
      actionHref: '/settings.html'
    },
    steps: []
  })));
  await page.route('**/api/v1/onboarding/activation', route => route.fulfill(json({
    ready: false,
    completed: 1,
    total: 10,
    progressPercent: 10,
    blockers: ['SERVICES', 'SCHEDULE', 'RECEPTIONIST'],
    steps: []
  })));
  await page.route('**/api/v1/services', route => route.fulfill(json([])));
  await page.route('**/api/v1/business/hours', route => route.fulfill(json([])));
  await page.route('**/api/v1/knowledge**', route => route.fulfill(json([])));
  await page.route('**/api/v1/ai-agent', route => route.fulfill(json({
    configured: false,
    name: 'RecepVoz',
    language: 'es',
    active: false,
    capabilities: []
  })));
  await page.route('**/api/v1/phone-numbers', route => route.fulfill(json([])));
  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: false,
    configured: false,
    purchaseAvailable: false,
    provider: 'TWILIO',
    message: 'No disponible en RC'
  })));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    plan: 'BASIC',
    status: 'ACTIVE',
    serviceAllowed: true,
    maxConcurrentCalls: 1,
    includedMinutes: 100,
    usedMinutes: 0,
    overageMinutes: 0,
    billingProviderConnected: false,
    legacyFallback: false
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/admin/users', route => route.fulfill(json([])));
  await page.route('**/api/v1/admin/invitations', route => route.fulfill(json([])));
  await page.route('**/api/v1/operations/pilot-metrics**', route => route.fulfill(json({
    conversationsHandled: 0,
    bookingsCreated: 0,
    requiresAttention: 0
  })));
  await page.goto('/');

  await expect(page.locator('#firstUserOnboarding')).toBeVisible();
  await expect(page.locator('#firstUserOnboarding h1')).toHaveText('Vamos a preparar tu recepcionista');
  await expect(page.locator('#firstUserProgressText')).toHaveText('1 de 4 pasos completados');
  await expect(page.locator('#firstUserNextAction')).toHaveText('Continuar con Servicios');
  await expect(page.locator('#firstUserNextAction')).toHaveAttribute('href', '/settings.html?section=services');
  await expect(page.locator('#homeBusinessWorkspace')).toBeHidden();
  expect(mutations).toEqual([]);
});

test('ferretería RC surfaces low stock as an owner priority without causing a mutation', async ({ page }) => {
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
  await page.route('**/api/v1/inventory/product-low/variants', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory/product-low/movements', route => route.fulfill(json([])));

  await page.goto('/inventory.html');

  await expect(page.locator('[data-inventory-product-id="product-low"]')).toContainText('Disco de corte 115 mm');
  await expect(page.locator('#inventoryAlertsCount')).toHaveText('1 pendiente');
  await expect(page.locator('[data-inventory-alert-id="alert-low"]')).toContainText('Stock bajo');
  await expect(page.locator('[data-inventory-alert-id="alert-low"]')).toContainText('DISCO-115');
  expect(mutations).toEqual([]);
});

test('ferretería RC makes unresolved customer intent require human attention without leaking raw enums', async ({ page }) => {
  const mutations = trackMutations(page);
  await commonIdentity(page);

  await page.route('**/api/v1/calls?**', route => route.fulfill(json({
    content: [{
      id: 'call-human-rc',
      callerNumber: '+56955557777',
      direction: 'INBOUND',
      status: 'COMPLETED',
      startedAt: '2026-09-29T00:10:00Z',
      durationSeconds: 132,
      resolution: 'UNANSWERED_QUESTION_RECORDED'
    }],
    number: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1
  })));
  await page.route('**/api/v1/messaging/conversations', route => route.fulfill(json([])));
  await page.route('**/api/v1/calls/call-human-rc', route => route.fulfill(json({
    call: {
      id: 'call-human-rc',
      callerNumber: '+56955557777',
      direction: 'INBOUND',
      status: 'COMPLETED',
      startedAt: '2026-09-29T00:10:00Z',
      durationSeconds: 132,
      resolution: 'UNANSWERED_QUESTION_RECORDED'
    },
    summary: 'El cliente consultó por instalación industrial y necesita confirmación humana.',
    transcript: [
      {
        id: 't-human-1',
        speaker: 'USER',
        content: '¿Pueden instalar este compresor industrial?',
        sequenceNumber: 1,
        createdAt: '2026-09-29T00:10:05Z'
      },
      {
        id: 't-human-2',
        speaker: 'ASSISTANT',
        content: 'No tengo esa información confirmada. Una persona debe revisarla.',
        sequenceNumber: 2,
        createdAt: '2026-09-29T00:10:14Z'
      }
    ],
    actions: [{
      id: 'a-human-1',
      actionType: 'SEARCH_KNOWLEDGE',
      success: true,
      detail: 'Instalación industrial',
      createdAt: '2026-09-29T00:10:10Z'
    }]
  })));

  await page.goto('/conversations.html');

  await expect(page.locator('#conversationList')).toContainText('+56955557777');
  await expect(page.locator('#conversationList')).toContainText('Necesita atención humana');
  await expect(page.getByText('El cliente consultó por instalación industrial y necesita confirmación humana.')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('UNANSWERED_QUESTION_RECORDED');
  await expect(page.locator('body')).not.toContainText('SEARCH_KNOWLEDGE');
  expect(mutations).toEqual([]);
});

test('operator can inspect the active plan but cannot access admin usage or trigger billing mutations', async ({ page }) => {
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
  await page.route('**/api/v1/usage/summary?**', route => route.fulfill({
    status: 403,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'forbidden' })
  }));

  await page.goto('/account.html');

  await expect(page.locator('#planName')).toHaveText('Profesional');
  await expect(page.locator('#accountRole')).toHaveText('Operador');
  await expect(page.locator('#usageState')).toContainText('solo para administradores');
  await expect(page.locator('#accountError')).toBeHidden();
  await expect(page.getByRole('button', { name: /pagar|cobrar|suscrib/i })).toHaveCount(0);
  expect(mutations).toEqual([]);
});

test('billing outage degrades safely on mobile and does not create a payment attempt', async ({ page }) => {
  const mutations = trackMutations(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await commonIdentity(page);

  await page.route('**/api/v1/subscription', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'subscription unavailable' })
  }));

  await page.goto('/account.html');

  await expect(page.locator('#accountError')).toBeVisible();
  await expect(page.locator('#accountError')).toContainText('No pudimos cargar tu plan');
  await expect(page.locator('#accountEmail')).toHaveText('admin@ferreteria-rc.cl');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  expect(mutations).toEqual([]);
});
