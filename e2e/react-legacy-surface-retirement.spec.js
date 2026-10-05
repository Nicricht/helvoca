const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function seedSession(page) {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'legacy-retirement-e2e'));
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'admin@demo.cl',
    roles: ['BUSINESS_ADMIN']
  })));
  await page.route('**/api/v1/business', route => route.fulfill(json({
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Negocio Demo',
    timezone: 'America/Santiago',
    language: 'es'
  })));
  await page.route('**/api/v1/catalog', route => route.fulfill(json([])));
  await page.route('**/api/v1/inventory', route => route.fulfill(json([])));
  await page.route('**/api/v1/subscription', route => route.fulfill(json({
    businessId: '11111111-1111-1111-1111-111111111111',
    plan: 'EMPRENDE',
    publicPlanCode: 'EMPRENDE',
    planName: 'Emprende',
    status: 'ACTIVE',
    serviceAllowed: true,
    maxConcurrentCalls: 1,
    includedMinutes: 100,
    usedMinutes: 0,
    overageMinutes: 0,
    currentPeriodStart: '2026-10-01T00:00:00Z',
    currentPeriodEnd: '2026-11-01T00:00:00Z',
    graceUntil: null,
    billingProviderConnected: false,
    entitlements: [],
    legacyFallback: false
  })));
  await page.route('**/api/v1/usage/**', route => route.fulfill(json({
    included: 100,
    used: 0,
    remaining: 100,
    overage: 0
  })));
  await page.route('**/api/v1/billing/status', route => route.fulfill(json({
    billingEnabled: false,
    checkoutConfigured: false,
    currentPlanCode: 'EMPRENDE',
    currentPlanName: 'Emprende',
    currentMonthlyPriceClp: 29990,
    subscriptionStatus: 'ACTIVE',
    pendingPlanCode: null,
    pendingPlanName: null,
    pendingMonthlyPriceClp: null,
    checkoutUrl: null,
    awaitingProviderVerification: false
  })));
  await page.route('**/api/v1/public/pricing', route => route.fulfill(json([])));
  await page.route('**/api/v1/services', route => route.fulfill(json([])));
  await page.route('**/api/v1/bookings**', route => route.fulfill(json([])));
  await page.route('**/api/v1/public-booking/config', route => route.fulfill(json({ enabled: false })));
}

for (const legacy of [
  {
    path: '/inventory.html',
    target: /\/app\/inventory\/?$/,
    heading: 'Inventario',
    retiredAssets: ['/inventory.js', '/inventory.css']
  },
  {
    path: '/account.html',
    target: /\/app\/plan\/?$/,
    heading: 'Plan y consumo',
    retiredAssets: ['/account.js', '/account.css']
  },
  {
    path: '/conversations.html',
    target: /\/app\/agenda\/?$/,
    heading: 'Agenda',
    retiredAssets: ['/conversations.js', '/conversations.css']
  }
]) {
  test(`${legacy.path} is compatibility-only and lands on the canonical React surface`, async ({ page }) => {
    await seedSession(page);
    const requested = [];
    page.on('request', request => requested.push(new URL(request.url()).pathname));

    await page.goto(legacy.path);

    await expect(page).toHaveURL(legacy.target);
    await expect(page.getByRole('heading', { level: 1, name: legacy.heading })).toBeVisible();

    for (const asset of legacy.retiredAssets) {
      expect(requested).not.toContain(asset);
    }
  });
}

test('the public/auth shell no longer advertises standalone Conversations or legacy billing URLs', async ({ page }) => {
  await page.goto('/');

  await expect(page.locator('a[href="/conversations.html"]')).toHaveCount(0);
  await expect(page.locator('a[href="/account.html"]')).toHaveCount(0);
});
